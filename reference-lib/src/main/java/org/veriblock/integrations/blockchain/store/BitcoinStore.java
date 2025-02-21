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
import org.veriblock.integrations.sqlite.tables.BitcoinBlocksRepository;
import org.veriblock.integrations.sqlite.tables.BlockData;
import org.veriblock.integrations.sqlite.tables.KeyValueData;
import org.veriblock.integrations.sqlite.tables.KeyValueRepository;
import org.veriblock.sdk.BitcoinBlock;
import org.veriblock.sdk.BlockStoreException;
import org.veriblock.sdk.Sha256Hash;
import org.veriblock.sdk.services.SerializeDeserializeService;
import org.veriblock.sdk.util.Utils;

public class BitcoinStore {
    //private static final int DEFAULT_NUM_HEADERS = 5000;
    private static final Logger log = LoggerFactory.getLogger(BitcoinStore.class);
    
    // underlying database
    private final Connection databaseConnection;
    private final BitcoinBlocksRepository bitcoinRepository;
    private final KeyValueRepository keyValueRepository;
    
    private final String chainHeadRepositoryName = "chainHead";
    
    public BitcoinStore() throws SQLException {
        databaseConnection = ConnectionSelector.setConnectionDefault();
        bitcoinRepository = new BitcoinBlocksRepository(databaseConnection);
        keyValueRepository = new KeyValueRepository(databaseConnection);
    }
    
    public BitcoinStore(String databasePath) throws SQLException {
        databaseConnection = ConnectionSelector.setConnection(databasePath);
        bitcoinRepository = new BitcoinBlocksRepository(databaseConnection);
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
        bitcoinRepository.getBlocksRepository().clear();
        keyValueRepository.clear();
    }
    
    public StoredBitcoinBlock getChainHead() throws BlockStoreException, SQLException {
        String headEncoded = keyValueRepository.getValue(chainHeadRepositoryName);
        if(headEncoded == null) return null;
        
        StoredBitcoinBlock block = get(Sha256Hash.wrap(Utils.decodeHex(headEncoded)));
        return block;
    }

    public StoredBitcoinBlock setChainHead(StoredBitcoinBlock chainHead) throws BlockStoreException, SQLException {
        StoredBitcoinBlock existingBlock = get(chainHead.getHash());
        if(existingBlock == null) {
            throw new BlockStoreException("Chain head should reference existing block");
        }
        
        StoredBitcoinBlock previousBlock = getChainHead();
        
        String headEncoded = Utils.encodeHex(chainHead.getBlock().getHash().getBytes());
        KeyValueData data = new KeyValueData();
        data.key = chainHeadRepositoryName;
        data.value = headEncoded;
        keyValueRepository.save(data.key, data.value);
        
        return previousBlock;
    }

    public void put(StoredBitcoinBlock storedBlock) throws BlockStoreException, SQLException {
        byte[] serialized = SerializeDeserializeService.serialize(storedBlock.getBlock());
        String id = Utils.encodeHex(storedBlock.getHash().getBytes());
        BlockData data = new BlockData();
        data.id = id;
        data.previousId = Utils.encodeHex(storedBlock.getBlock().getPreviousBlock().getBytes());
        data.height = storedBlock.getHeight();
        data.work = storedBlock.getWork();
        data.data = serialized;
        bitcoinRepository.getBlocksRepository().save(data);
    }
    
    public StoredBitcoinBlock get(Sha256Hash hash) throws BlockStoreException, SQLException {
        BlockData data = bitcoinRepository.getBlocksRepository().get(Utils.encodeHex(hash.getBytes()));
        if(data == null) return null;

        BitcoinBlock block = SerializeDeserializeService.parseBitcoinBlockWithLength(ByteBuffer.wrap(data.data));
        StoredBitcoinBlock storedBlock = new StoredBitcoinBlock(block, data.work, data.height);
        return storedBlock;
    }
    
    ///HACK: it is actually a delete method. It deletes block with hash.
    ///HACK: storedBlock is not being used.
    public StoredBitcoinBlock replace(Sha256Hash hash, StoredBitcoinBlock storedBlock) throws BlockStoreException, SQLException {
        StoredBitcoinBlock replaced = get(hash);
        bitcoinRepository.getBlocksRepository().delete(Utils.encodeHex(hash.getBytes()));
        return replaced;
    }

    public List<StoredBitcoinBlock> get(Sha256Hash hash, int count) throws BlockStoreException, SQLException {
        List<StoredBitcoinBlock> blocks = new ArrayList<>();
        Sha256Hash currentHash = hash;
        
        while(true) {
            // check if we got the needed blocks
            if(blocks.size() >= count) break;
            StoredBitcoinBlock current = get(currentHash);
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
    public StoredBitcoinBlock getFromChain(Sha256Hash hash, int blocksAgo) throws BlockStoreException, SQLException {
        List<StoredBitcoinBlock> blocks = get(hash, blocksAgo + 1);
        // check if the branch is long enough
        if(blocks.size() < (blocksAgo + 1)) {
            return null;
        }
        return blocks.get(blocksAgo);
    }

    // start from the chainHead and search for a block with hash
    public StoredBitcoinBlock scanBestChain(Sha256Hash hash) throws BlockStoreException, SQLException {
        StoredBitcoinBlock current = getChainHead();
        if(current == null) return null;
        
        Sha256Hash currentHash = current.getHash();
        
        while(true) {
            if(currentHash.compareTo(hash) == 0) return current;
            // check if the block exists
            if(current == null) return null;
            // check if we found the Genesis block
            if(currentHash.toBigInteger().compareTo(BigInteger.ZERO) == 0) return null;
            
            currentHash = current.getBlock().getPreviousBlock();
            current = get(currentHash);
        }
    }
}
