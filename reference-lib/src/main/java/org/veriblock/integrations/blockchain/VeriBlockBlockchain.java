// VeriBlock Blockchain Project
// Copyright 2017-2018 VeriBlock, Inc
// Copyright 2018-2019 Xenios SEZC
// All rights reserved.
// https://www.veriblock.org
// Distributed under the MIT software license, see the accompanying
// file LICENSE or http://www.opensource.org/licenses/mit-license.php.

package org.veriblock.integrations.blockchain;

import java.math.BigInteger;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.veriblock.integrations.auditor.Change;
import org.veriblock.integrations.blockchain.changes.AddVeriBlockBlockChange;
import org.veriblock.integrations.blockchain.changes.SetVeriBlockHeadChange;
import org.veriblock.integrations.blockchain.changes.SetVeriBlockProofChange;
import org.veriblock.integrations.blockchain.store.BitcoinStore;
import org.veriblock.integrations.blockchain.store.StoredBitcoinBlock;
import org.veriblock.integrations.blockchain.store.StoredVeriBlockBlock;
import org.veriblock.integrations.blockchain.store.VeriBlockStore;
import org.veriblock.integrations.params.NetworkParameters;
import org.veriblock.sdk.BlockStoreException;
import org.veriblock.sdk.Constants;
import org.veriblock.sdk.Sha256Hash;
import org.veriblock.sdk.VBlakeHash;
import org.veriblock.sdk.VeriBlockBlock;
import org.veriblock.sdk.VerificationException;
import org.veriblock.sdk.services.ValidationService;
import org.veriblock.sdk.util.BitcoinUtils;
import org.veriblock.sdk.util.Preconditions;

public class VeriBlockBlockchain {
    private static final Logger log = LoggerFactory.getLogger(VeriBlockBlockchain.class);

    private static final int MINIMUM_TIMESTAMP_BLOCK_COUNT = 20;
    private static final int DIFFICULTY_ADJUST_BLOCK_COUNT = VeriBlockDifficultyCalculator.RETARGET_PERIOD;
    private static final int BITCOIN_FINALITY = 11;
    private static final int[] POP_CONSENSUS_WEIGHTS_BY_RELATIVE_BITCOIN_INDEX = new int[]{100, 100, 95, 89, 80, 69, 56, 40, 21};

    private final VeriBlockStore store;
    private final BitcoinStore bitcoinStore;
    private final Map<VBlakeHash, StoredVeriBlockBlock> temporalStore;
    private final NetworkParameters networkParameters;
    private StoredVeriBlockBlock temporaryChainHead = null;

    private boolean hasTemporaryModifications() {
        return temporaryChainHead != null || temporalStore.size() > 0;
    }

    public VeriBlockBlockchain(NetworkParameters networkParameters,
            VeriBlockStore store, BitcoinStore bitcoinStore) {
        Preconditions.notNull(store, "Store cannot be null");
        Preconditions.notNull(bitcoinStore, "Bitcoin store cannot be null");
        Preconditions.notNull(networkParameters, "Network parameters cannot be null");

        this.store = store;
        this.bitcoinStore = bitcoinStore;
        this.networkParameters = networkParameters;
        this.temporalStore = new HashMap<>();
    }

    public VeriBlockBlock get(VBlakeHash hash) throws BlockStoreException, SQLException {
        StoredVeriBlockBlock storedBlock = getInternal(hash);
        if (storedBlock != null) {
            return storedBlock.getBlock();
        }

        return null;
    }

    public VeriBlockBlock searchBestChain(VBlakeHash hash) throws BlockStoreException, SQLException {
        // Look at the temporal store first
        StoredVeriBlockBlock storedBlock;
        if (temporaryChainHead != null) {
            storedBlock = getInternal(hash);
        } else {
            storedBlock = store.scanBestChain(hash);
        }

        if (storedBlock != null) {
            return storedBlock.getBlock();
        }

        return null;
    }

    public List<Change> add(VeriBlockBlock block) throws VerificationException, BlockStoreException, SQLException {
        Preconditions.state(!hasTemporaryModifications(), "Cannot add a block while having temporary modifications");

        return addWithProof(block, Sha256Hash.ZERO_HASH);
    }

    public List<Change> addWithProof(VeriBlockBlock block, Sha256Hash blockOfProof) throws VerificationException, BlockStoreException, SQLException {
        Preconditions.state(!hasTemporaryModifications(), "Cannot add a block with proof while having temporary modifications");

        // Lightweight verification of the header
        ValidationService.verify(block);

        BigInteger work = BigInteger.ZERO;
        VBlakeHash previousHash = VBlakeHash.EMPTY_HASH;
        if (getChainHeadInternal() != null) {
            // Further verification requiring context
            StoredVeriBlockBlock previous = checkConnectivity(block);
            if (!verifyBlock(block, previous)) {
                return Collections.emptyList();
            }
            work = work.add(previous.getWork());
            previousHash = previous.getHash();
        }

        StoredVeriBlockBlock storedBlock = new StoredVeriBlockBlock(
                block,
                work.add(BitcoinUtils.decodeCompactBits(block.getDifficulty())),
                blockOfProof);

        List<Change> changes = new ArrayList<>();
        store.put(storedBlock);
        changes.add(new AddVeriBlockBlockChange(storedBlock, storedBlock));

        // Try to update the prior keystone's proof
        Change keystoneChange = trySetBlockProof(storedBlock.getBlock().getEffectivePreviousKeystone(), blockOfProof);
        if (keystoneChange != null) changes.add(keystoneChange);

        // Special case for first block in a period
        if (block.getRoundIndex() == 1) {
            Change priorKeystoneChange = trySetBlockProof(block.getPreviousKeystone(), blockOfProof);
            if (priorKeystoneChange != null) changes.add(priorKeystoneChange);
        }

        StoredVeriBlockBlock chainHead = store.getChainHead();
        if (chainHead == null || chainHead.getHash().equals(previousHash) || resolveToFork(chainHead, storedBlock)) {
            StoredVeriBlockBlock priorHead = store.setChainHead(storedBlock);
            ///HACK: this is a dummy block that represents a change from null to genesis block
            if(priorHead == null) {
                VeriBlockBlock emptyBlock = new VeriBlockBlock(0, (short) 0, VBlakeHash.EMPTY_HASH, VBlakeHash.EMPTY_HASH, VBlakeHash.EMPTY_HASH,
                        Sha256Hash.ZERO_HASH, 0, 0, 0);
                priorHead = new StoredVeriBlockBlock(emptyBlock, BigInteger.ONE);
            }
            changes.add(new SetVeriBlockHeadChange(priorHead, storedBlock));
        }

        return changes;
    }

    public List<Change> addAll(List<VeriBlockBlock> blocks) throws VerificationException, BlockStoreException, SQLException {
        Preconditions.state(!hasTemporaryModifications(), "Cannot add blocks while having temporary modifications");

        List<Change> changes = new ArrayList<>();

        List<VeriBlockBlock> sortedBlocks = blocks.stream()
                .sorted(Comparator.comparingInt(VeriBlockBlock::getHeight))
                .collect(Collectors.toList());

        for (VeriBlockBlock block : sortedBlocks) {
            changes.addAll(add(block));
        }

        return changes;
    }

    public List<Change> setBlockOfProof(VeriBlockBlock block, Sha256Hash blockOfProof) throws BlockStoreException, SQLException {
        Preconditions.state(!hasTemporaryModifications(), "Cannot set a block of proof while having temporary modifications");

        List<Change> changes = new ArrayList<>();

        Change blockChange = trySetBlockProof(block.getHash(), blockOfProof);
        if (blockChange != null) changes.add(blockChange);

        Change keystoneChange = trySetBlockProof(block.getEffectivePreviousKeystone(), blockOfProof);
        if (keystoneChange != null) changes.add(keystoneChange);

        // Special case for first block in a period
        if (block.getRoundIndex() == 1) {
            Change priorKeystoneChange = trySetBlockProof(block.getPreviousKeystone(), blockOfProof);
            if (priorKeystoneChange != null) changes.add(priorKeystoneChange);
        }

        return changes;
    }

    public void addTemporarily(VeriBlockBlock block) throws VerificationException, BlockStoreException, SQLException {
        addTemporarily(block, Sha256Hash.ZERO_HASH);
    }

    public void addTemporarily(VeriBlockBlock block, Sha256Hash blockOfProof) throws VerificationException, BlockStoreException, SQLException {
        // Lightweight verification of the header
        ValidationService.verify(block);

        // Further verification requiring context
        StoredVeriBlockBlock previous = checkConnectivity(block);
        if (!verifyBlock(block, previous)) {
            return;
        }

        StoredVeriBlockBlock storedBlock = new StoredVeriBlockBlock(
                block,
                previous.getWork().add(BitcoinUtils.decodeCompactBits(block.getDifficulty())),
                blockOfProof);

        ///HACK: we always cut the hash for a key to the keystone size
        temporalStore.put(block.getHash().trimToPreviousKeystoneSize(), storedBlock);

        // Try to update the prior keystone's proof
        trySetBlockProofTemporarily(block.getEffectivePreviousKeystone(), blockOfProof);

        // Special case for first block in a period
        if (block.getRoundIndex() == 1) {
            trySetBlockProofTemporarily(block.getPreviousKeystone(), blockOfProof);
        }

        StoredVeriBlockBlock chainHead = getChainHeadInternal();
        if (chainHead.getHash().equals(previous.getHash()) || resolveToFork(chainHead, storedBlock)) {
            temporaryChainHead = storedBlock;
        }
    }

    public void addAllTemporarily(List<VeriBlockBlock> blocks) throws VerificationException {
        blocks.forEach(t -> {
            try {
                addTemporarily(t);
            } catch (VerificationException | BlockStoreException | SQLException e) {
                throw new BlockStoreException(e);
            }
        });
    }

    public void setBlockOfProofTemporarily(VeriBlockBlock block, Sha256Hash blockOfProof) throws BlockStoreException, SQLException {
        trySetBlockProofTemporarily(block.getHash(), blockOfProof);

        trySetBlockProof(block.getEffectivePreviousKeystone(), blockOfProof);

        // Special case for first block in a period
        if (block.getRoundIndex() == 1) {
            trySetBlockProof(block.getPreviousKeystone(), blockOfProof);
        }
    }

    public void clearTemporaryModifications() {
        temporaryChainHead = null;
        temporalStore.clear();
    }

    public void rewind(List<Change> changes) throws BlockStoreException, SQLException {
        for (Change change : changes) {
            if (change.getChainIdentifier().equals(Constants.VERIBLOCK_HEADER_MAGIC)) {
                switch (change.getOperation()) {
                    case ADD_BLOCK:
                        StoredVeriBlockBlock newValue = StoredVeriBlockBlock.deserialize(change.getNewValue());
                        StoredVeriBlockBlock oldValue = StoredVeriBlockBlock.deserialize(change.getOldValue());
                        store.replace(newValue.getHash(), oldValue);
                        break;
                    case SET_HEAD:
                        StoredVeriBlockBlock priorHead = StoredVeriBlockBlock.deserialize(change.getOldValue());
                        store.setChainHead(priorHead);
                        break;
                    case SET_PROOF:
                        StoredVeriBlockBlock priorProof = StoredVeriBlockBlock.deserialize(change.getOldValue());
                        store.replace(priorProof.getHash(), priorProof);
                        break;
                }
            }
        }
    }

    private StoredVeriBlockBlock getInternal(VBlakeHash hash) throws BlockStoreException, SQLException {
        VBlakeHash trimmed = hash.trimToPreviousKeystoneSize();
        if (temporalStore.containsKey(trimmed)) {
            return temporalStore.get(trimmed);
        }

        return store.get(hash);
    }

    private StoredVeriBlockBlock getChainHeadInternal() throws BlockStoreException, SQLException {
        if (temporaryChainHead != null) return temporaryChainHead;

        return store.getChainHead();
    }

    private List<StoredVeriBlockBlock> getChainInternal(VBlakeHash head, int count) throws BlockStoreException, SQLException {
        List<StoredVeriBlockBlock> blocks = new ArrayList<>();

        VBlakeHash cursor = head.trimToPreviousKeystoneSize();
        while (temporalStore.containsKey(cursor)) {
            StoredVeriBlockBlock tempBlock = temporalStore.get(cursor);
            blocks.add(tempBlock);

            if (blocks.size() == count) break;

            cursor = tempBlock.getBlock().getPreviousBlock().trimToPreviousKeystoneSize();
        }

        if (blocks.size() > 0) {
            StoredVeriBlockBlock last = blocks.get(blocks.size() - 1);
            blocks.addAll(store.get(last.getBlock().getPreviousBlock(), count - blocks.size()));
        } else {
            blocks.addAll(store.get(head, count));
        }

        return blocks;
    }

    private Change trySetBlockProof(VBlakeHash hash, Sha256Hash blockOfProof) throws BlockStoreException, SQLException {
        if (blockOfProof.equals(Sha256Hash.ZERO_HASH)) return null;

        StoredVeriBlockBlock storedBlock = store.get(hash);
        if (storedBlock == null) {
            return null;
        }

        if (storedBlock.getBlockOfProof().equals(Sha256Hash.ZERO_HASH)) {
            storedBlock.setBlockOfProof(blockOfProof);
            StoredVeriBlockBlock replaced = store.replace(storedBlock.getHash(), storedBlock);
            return new SetVeriBlockProofChange(replaced, storedBlock);
        }

        // Is it better?
        StoredBitcoinBlock incumbent = bitcoinStore.scanBestChain(storedBlock.getBlockOfProof());
        StoredBitcoinBlock candidate = bitcoinStore.get(blockOfProof);

        if (incumbent == null || incumbent.getHeight() > candidate.getHeight()) {
            storedBlock.setBlockOfProof(candidate.getHash());
            StoredVeriBlockBlock replaced = store.replace(storedBlock.getHash(), storedBlock);
            return new SetVeriBlockProofChange(replaced, storedBlock);
        }

        return null;
    }

    private void trySetBlockProofTemporarily(VBlakeHash hash, Sha256Hash blockOfProof) throws BlockStoreException, SQLException {
        if (blockOfProof.equals(Sha256Hash.ZERO_HASH)) return;

        StoredVeriBlockBlock storedBlock = getInternal(hash);
        if (storedBlock == null) {
            return;
        }

        if (storedBlock.getBlockOfProof().equals(Sha256Hash.ZERO_HASH)) {
            storedBlock.setBlockOfProof(blockOfProof);
            temporalStore.put(hash.trimToPreviousKeystoneSize(), storedBlock);
            return;
        }

        // Is it better?
        StoredBitcoinBlock incumbent = bitcoinStore.scanBestChain(storedBlock.getBlockOfProof());
        StoredBitcoinBlock candidate = bitcoinStore.get(blockOfProof);

        if (incumbent == null || incumbent.getHeight() > candidate.getHeight()) {
            storedBlock.setBlockOfProof(candidate.getHash());
            temporalStore.put(hash.trimToPreviousKeystoneSize(), storedBlock);
        }
    }

    private boolean resolveToFork(StoredVeriBlockBlock chainHead, StoredVeriBlockBlock candidate) throws BlockStoreException, SQLException {
        if (chainHead.getBlock().getEffectivePreviousKeystone().probablyEquals(candidate.getBlock().getEffectivePreviousKeystone())) {
            if (candidate.getWork().compareTo(chainHead.getWork()) > 0) {
                return true;
            }
        }

        StoredVeriBlockBlock bestCursor = chainHead;
        StoredVeriBlockBlock candidateCursor = candidate;
        List<StoredVeriBlockBlock> bestChain = new ArrayList<>();
        List<StoredVeriBlockBlock> candidateChain = new ArrayList<>();

        if (bestCursor.getBlock().isKeystone()) {
            bestChain.add(bestCursor);
        }
        if (candidateCursor.getBlock().isKeystone()) {
            candidateChain.add(candidateCursor);
        }

        do {
            if (bestCursor.getKeystoneIndex() == candidateCursor.getKeystoneIndex()) {
                bestCursor = getInternal(bestCursor.getBlock().getEffectivePreviousKeystone());
                bestChain.add(bestCursor);

                candidateCursor = getInternal(candidateCursor.getBlock().getEffectivePreviousKeystone());
                if (candidateCursor == null) {
                    return false;
                }
                candidateChain.add(candidateCursor);
            } else if (bestCursor.getKeystoneIndex() > candidateCursor.getKeystoneIndex()) {
                bestCursor = getInternal(bestCursor.getBlock().getEffectivePreviousKeystone());
                bestChain.add(bestCursor);
            } else if (candidateCursor.getKeystoneIndex() > bestCursor.getKeystoneIndex()) {
                candidateCursor = getInternal(candidateCursor.getBlock().getEffectivePreviousKeystone());
                if (candidateCursor == null) {
                    return false;
                }
                candidateChain.add(candidateCursor);
            }
        } while (!bestCursor.getBlock().equals(candidate.getBlock()));

        return compareChains(candidateChain, bestChain) > 0;
    }

    private int compareChains(List<StoredVeriBlockBlock> candidate, List<StoredVeriBlockBlock> incumbent) throws BlockStoreException, SQLException {
        // Sort them to be sure
        incumbent.sort(Comparator.comparingInt(StoredVeriBlockBlock::getHeight));
        candidate.sort(Comparator.comparingInt(StoredVeriBlockBlock::getHeight));

        int incumbentScore = 0;
        int candidateScore = 0;

        StoredBitcoinBlock lastIncumbentBlockOfProof = null;
        StoredBitcoinBlock lastCandidateBlockOfProof = null;
        for (int i = 0; i < Math.max(incumbent.size(), candidate.size()); i++) {
            StoredBitcoinBlock incumbentBlockOfProof = null;
            StoredBitcoinBlock candidateBlockOfProof = null;
            if (i < incumbent.size()) {
                StoredVeriBlockBlock storedBlock = getInternal(incumbent.get(i).getHash());
                if (storedBlock != null) {
                    Sha256Hash proof = storedBlock.getBlockOfProof();
                    if (!Sha256Hash.ZERO_HASH.equals(proof)) {
                        incumbentBlockOfProof = bitcoinStore.scanBestChain(proof);
                        if (incumbentBlockOfProof != null && lastIncumbentBlockOfProof != null &&
                                lastIncumbentBlockOfProof.getHeight() + BITCOIN_FINALITY < incumbentBlockOfProof.getHeight()) {
                            incumbentBlockOfProof = null;
                        }
                    }
                }
            }
            if (i < candidate.size()) {
                StoredVeriBlockBlock storedBlock = getInternal(incumbent.get(i).getHash());
                if (storedBlock != null) {
                    Sha256Hash proof = storedBlock.getBlockOfProof();
                    if (!Sha256Hash.ZERO_HASH.equals(proof)) {
                        candidateBlockOfProof = bitcoinStore.scanBestChain(proof);
                        if (candidateBlockOfProof != null && lastCandidateBlockOfProof != null &&
                                lastCandidateBlockOfProof.getHeight() + BITCOIN_FINALITY < candidateBlockOfProof.getHeight()) {
                            candidateBlockOfProof = null;
                        }
                    }
                }
            }

            incumbentScore += getScore(incumbentBlockOfProof, candidateBlockOfProof);
            candidateScore += getScore(candidateBlockOfProof, incumbentBlockOfProof);

            lastIncumbentBlockOfProof = incumbentBlockOfProof;
            lastCandidateBlockOfProof = candidateBlockOfProof;
        }

        return Integer.compare(candidateScore, incumbentScore);
    }

    private int getScore(StoredBitcoinBlock blockToScore, StoredBitcoinBlock alternative) {
        if (blockToScore == null) return 0;
        if (alternative == null) return POP_CONSENSUS_WEIGHTS_BY_RELATIVE_BITCOIN_INDEX[0];

        int offset = Math.min(blockToScore.getHeight(), alternative.getHeight());

        if (blockToScore.getHeight() - offset >= POP_CONSENSUS_WEIGHTS_BY_RELATIVE_BITCOIN_INDEX.length) {
            return 0;
        }

        return POP_CONSENSUS_WEIGHTS_BY_RELATIVE_BITCOIN_INDEX[blockToScore.getHeight() - offset];
    }

    private boolean verifyBlock(VeriBlockBlock block, StoredVeriBlockBlock previous) throws VerificationException, BlockStoreException, SQLException {
        if (!checkDuplicate(block)) return false;

        List<StoredVeriBlockBlock> context = getChainInternal(block.getPreviousBlock(), DIFFICULTY_ADJUST_BLOCK_COUNT);

        checkTimestamp(block, context);
        checkDifficulty(block, previous, context);

        return true;
    }

    private boolean checkDuplicate(VeriBlockBlock block) throws BlockStoreException, SQLException {
        // Duplicate?
        StoredVeriBlockBlock duplicate = getInternal(block.getHash());
        if (duplicate != null) {
            log.info("Block '{}' has already been added", block.getHash().toString());
            return false;
        }

        return true;
    }

    private StoredVeriBlockBlock checkConnectivity(VeriBlockBlock block) throws BlockStoreException, SQLException {
        // Connects to a known "seen" block (except for origin block)
        StoredVeriBlockBlock previous = getInternal(block.getPreviousBlock());
        if (previous == null) {
            throw new VerificationException("Block does not fit");
        }

        StoredVeriBlockBlock keystone = store.get(block.getPreviousKeystone());
        if (keystone == null) {
            // Do I have any blocks at that keystone height?
            int keystoneBlocksAgo = block.getHeight() % 20;
            switch (keystoneBlocksAgo) {
                case 0:
                    keystoneBlocksAgo = 20;
                    break;
                case 1:
                    keystoneBlocksAgo = 21;

            }
            List<StoredVeriBlockBlock> context = store.get(block.getPreviousBlock(), keystoneBlocksAgo);
            if (context.size() == keystoneBlocksAgo) {
                throw new VerificationException("Block's previous keystone is not found");
            }
            // If the context chain can't reach to this height, we just don't have enough blocks yet
        }

        StoredVeriBlockBlock secondKeystone = store.get(block.getSecondPreviousKeystone());
        if (secondKeystone == null) {
            // Do I have any blocks at that keystone height?
            int keystoneBlocksAgo = block.getHeight() % 20;
            switch (keystoneBlocksAgo) {
                case 0:
                    keystoneBlocksAgo = 40;
                    break;
                case 1:
                    keystoneBlocksAgo = 41;
                    break;
                default:
                    keystoneBlocksAgo += 20;

            }
            List<StoredVeriBlockBlock> context = store.get(block.getPreviousBlock(), keystoneBlocksAgo);
            if (context.size() == keystoneBlocksAgo) {
                throw new VerificationException("Block's second previous keystone is not found");
            }
        }

        return previous;
    }

    private void checkTimestamp(VeriBlockBlock block, List<StoredVeriBlockBlock> context) throws VerificationException {
        if (context.size() < MINIMUM_TIMESTAMP_BLOCK_COUNT) {
            log.warn("Not enough context blocks to check timestamp");
            return;
        }

        Optional<Integer> median = context.stream()
                .sorted(Comparator.comparingInt(StoredVeriBlockBlock::getHeight).reversed())
                .limit(MINIMUM_TIMESTAMP_BLOCK_COUNT)
                .map(b -> b.getBlock().getTimestamp())
                .sorted()
                .skip((MINIMUM_TIMESTAMP_BLOCK_COUNT / 2) - 1)
                .findFirst();

        if (!median.isPresent() || block.getTimestamp() <= median.get()) {
            throw new VerificationException("Block is too far in the past");
        }
    }

    private void checkDifficulty(VeriBlockBlock block, StoredVeriBlockBlock previous, List<StoredVeriBlockBlock> context) throws VerificationException {
        if (context.size() < DIFFICULTY_ADJUST_BLOCK_COUNT) {
            log.warn("Not enough context blocks to check difficulty");
            return;
        }

        List<VeriBlockBlock> contextBlocks = context.stream().map(StoredVeriBlockBlock::getBlock).collect(Collectors.toList());
        BigInteger calculated = VeriBlockDifficultyCalculator.calculate(networkParameters, previous.getBlock(), contextBlocks);

        if (block.getDifficulty() != (int)BitcoinUtils.encodeCompactBits(calculated)) {
            throw new VerificationException("Block does not conform to expected difficulty");
        }
    }
}
