// VeriBlock Blockchain Project
// Copyright 2017-2018 VeriBlock, Inc
// Copyright 2018-2019 Xenios SEZC
// All rights reserved.
// https://www.veriblock.org
// Distributed under the MIT software license, see the accompanying
// file LICENSE or http://www.opensource.org/licenses/mit-license.php.

package org.veriblock.integrations.blockchain.store;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.veriblock.integrations.sqlite.ConnectionSelector;
import org.veriblock.integrations.sqlite.tables.BlockData;
import org.veriblock.integrations.sqlite.tables.KeyValueData;
import org.veriblock.integrations.sqlite.tables.KeyValueRepository;
import org.veriblock.integrations.sqlite.tables.VeriBlockBlocksRepository;
import org.veriblock.sdk.BlockStoreException;
import org.veriblock.sdk.VBlakeHash;
import org.veriblock.sdk.VeriBlockBlock;
import org.veriblock.sdk.services.SerializeDeserializeService;
import org.veriblock.sdk.util.Utils;

public class VeriBlockStore {
    //private static final int DEFAULT_NUM_HEADERS = 90000;
    private static final Logger log = LoggerFactory.getLogger(VeriBlockStore.class);

    // underlying database
    private final Connection databaseConnection;
    private final VeriBlockBlocksRepository veriBlockRepository;
    private final KeyValueRepository keyValueRepository;

    private final String chainHeadRepositoryName = "chainHeadVbk";

    public VeriBlockStore() throws SQLException {
        databaseConnection = ConnectionSelector.setConnectionDefault();
        veriBlockRepository = new VeriBlockBlocksRepository(databaseConnection);
        keyValueRepository = new KeyValueRepository(databaseConnection);
    }

    public VeriBlockStore(String databasePath) throws SQLException {
        databaseConnection = ConnectionSelector.setConnection(databasePath);
        veriBlockRepository = new VeriBlockBlocksRepository(databaseConnection);
        keyValueRepository = new KeyValueRepository(databaseConnection);
    }

    public void shutdown() {
        try {
            if(databaseConnection != null) databaseConnection.close();
        } catch (SQLException e) {
            log.debug("Error closing database connection", e);
        }
    }

    public void clear() throws SQLException {
        veriBlockRepository.getBlocksRepository().clear();
        keyValueRepository.clear();
    }

    public StoredVeriBlockBlock getChainHead() throws BlockStoreException, SQLException {
        String headEncoded = keyValueRepository.getValue(chainHeadRepositoryName);
        if(headEncoded == null) return null;

        StoredVeriBlockBlock block = get(VBlakeHash.wrap(Utils.decodeHex(headEncoded)));
        return block;
    }

    public StoredVeriBlockBlock setChainHead(StoredVeriBlockBlock chainHead) throws BlockStoreException, SQLException {
        StoredVeriBlockBlock existingBlock = get(chainHead.getHash());
        if(existingBlock == null) {
            throw new BlockStoreException("Chain head should reference existing block");
        }

        StoredVeriBlockBlock previousBlock = getChainHead();

        String headEncoded = Utils.encodeHex(chainHead.getBlock().getHash().getBytes());
        KeyValueData data = new KeyValueData();
        data.key = chainHeadRepositoryName;
        data.value = headEncoded;
        keyValueRepository.save(data.key, data.value);

        return previousBlock;
    }

    public void put(StoredVeriBlockBlock storedBlock) throws BlockStoreException, SQLException {
        byte[] serialized = SerializeDeserializeService.serialize(storedBlock.getBlock());
        String id = Utils.encodeHex(storedBlock.getHash().getBytes());
        BlockData data = new BlockData();
        data.id = id;
        data.previousId = Utils.encodeHex(storedBlock.getBlock().getPreviousBlock().getBytes());
        data.height = storedBlock.getHeight();
        data.work = storedBlock.getWork();
        data.data = serialized;
        veriBlockRepository.getBlocksRepository().save(data);
    }

    public StoredVeriBlockBlock get(VBlakeHash hash) throws BlockStoreException, SQLException {
        List<BlockData> blocks = veriBlockRepository.getBlocksRepository().getEndsWithId(Utils.encodeHex(hash.getBytes()));
        if(blocks.isEmpty()) return null;
        
        BlockData data = blocks.get(0);
        if(data == null) return null;

        VeriBlockBlock block = SerializeDeserializeService.parseVeriBlockBlock(ByteBuffer.wrap(data.data));
        StoredVeriBlockBlock storedBlock = new StoredVeriBlockBlock(block, data.work);
        return storedBlock;
    }

    ///HACK: it is actually a delete method. It deletes block with hash.
    ///HACK: storedBlock is not being used.
    public StoredVeriBlockBlock replace(VBlakeHash hash, StoredVeriBlockBlock storedBlock) throws BlockStoreException, SQLException {
        StoredVeriBlockBlock replaced = get(hash);
        veriBlockRepository.getBlocksRepository().delete(Utils.encodeHex(hash.getBytes()));
        return replaced;
    }

    public List<StoredVeriBlockBlock> get(VBlakeHash hash, int count) throws BlockStoreException, SQLException {
        List<StoredVeriBlockBlock> blocks = new ArrayList<>();
        VBlakeHash currentHash = hash;

        while(true) {
            // check if we got the needed blocks
            if(blocks.size() >= count) break;

            StoredVeriBlockBlock current = get(currentHash);

            // check if the block exists
            if(current == null) break;
            blocks.add(current);

            // check if we found the Genesis block
            if(currentHash.toBigInteger().compareTo(BigInteger.ZERO) == 0) break;
            currentHash = current.getBlock().getPreviousBlock();
        }

        return blocks;
    }

    // search for a block 'blocksAgo' blocks before the block with 'hash'
    public StoredVeriBlockBlock getFromChain(VBlakeHash hash, int blocksAgo) throws BlockStoreException, SQLException {
        List<StoredVeriBlockBlock> blocks = get(hash, blocksAgo + 1);
        // check if the branch is long enough
        if(blocks.size() < (blocksAgo + 1)) {
            return null;
        }
        return blocks.get(blocksAgo);
    }

    // start from the chainHead and search for a block with hash
    public StoredVeriBlockBlock scanBestChain(VBlakeHash hash) throws BlockStoreException, SQLException {
        StoredVeriBlockBlock current = getChainHead();
        if(current == null) return null;

        VBlakeHash currentHash = current.getHash();

        while(true) {
            // trim both hashes to the lowest common length
            int commonMinLength = Math.min(currentHash.length, hash.length);
            VBlakeHash trimmedCurrentHash = VBlakeHash.trim(currentHash, commonMinLength);            
            VBlakeHash trimmedHash = VBlakeHash.trim(hash, commonMinLength);
            
            if(trimmedCurrentHash.equals(trimmedHash)) return current;

            // check if the block exists
            if(current == null) return null;
            // check if we found the Genesis block
            if(currentHash.toBigInteger().compareTo(BigInteger.ZERO) == 0) return null;

            currentHash = current.getBlock().getPreviousBlock();
            current = get(currentHash);
        }
    }
}
