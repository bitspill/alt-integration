// VeriBlock Blockchain Project
// Copyright 2017-2018 VeriBlock, Inc
// Copyright 2018-2019 Xenios SEZC
// All rights reserved.
// https://www.veriblock.org
// Distributed under the MIT software license, see the accompanying
// file LICENSE or http://www.opensource.org/licenses/mit-license.php.

package org.veriblock.sdk;

import org.veriblock.sdk.services.SerializeDeserializeService;
import org.veriblock.sdk.util.Preconditions;

import java.util.Arrays;

public class VeriBlockBlock{
    private byte[] raw;
    private final int height;
    private final short version;
    private final VBlakeHash previousBlock;
    private final VBlakeHash previousKeystone;
    private final VBlakeHash secondPreviousKeystone;
    private final Sha256Hash merkleRoot;
    private final int timestamp;
    private final int difficulty;
    private final int nonce;
    
    private  VBlakeHash hash;

    public int getHeight() {
        return height;
    }

    public short getVersion() {
        return version;
    }

    public VBlakeHash getPreviousBlock() {
        return previousBlock;
    }

    public VBlakeHash getPreviousKeystone() {
        return previousKeystone;
    }

    public VBlakeHash getSecondPreviousKeystone() {
        return secondPreviousKeystone;
    }

    public Sha256Hash getMerkleRoot() {
        return merkleRoot;
    }

    public int getTimestamp() {
        return timestamp;
    }

    public int getDifficulty() {
        return difficulty;
    }

    public int getNonce() {
        return nonce;
    }

    public byte[] getRaw() {
        return raw;
    }

    public VBlakeHash getHash() {
        return this.hash;
    }

    public VeriBlockBlock(int height,
                          short version,
                          VBlakeHash previousBlock,
                          VBlakeHash previousKeystone,
                          VBlakeHash secondPreviousKeystone,
                          Sha256Hash merkleRoot,
                          int timestamp,
                          int difficulty,
                          int nonce) {
        Preconditions.argument(previousBlock != null && previousBlock.length >= VBlakeHash.PREVIOUS_BLOCK_LENGTH,
                "Invalid previous block");
        Preconditions.argument(previousKeystone != null && previousKeystone.length >= VBlakeHash.PREVIOUS_KEYSTONE_LENGTH,
                "Invalid previous keystone");
        Preconditions.argument(secondPreviousKeystone != null && secondPreviousKeystone.length >= VBlakeHash.PREVIOUS_KEYSTONE_LENGTH,
                "Invalid second previous keystone");
        Preconditions.argument(merkleRoot != null && merkleRoot.length >= Sha256Hash.VERIBLOCK_MERKLE_ROOT_LENGTH,
                "Invalid merkle root");

        this.height = height;
        this.version = version;
        this.previousBlock = previousBlock.trimToPreviousBlockSize();
        this.previousKeystone = previousKeystone.trimToPreviousKeystoneSize();
        this.secondPreviousKeystone = secondPreviousKeystone.trimToPreviousKeystoneSize();
        this.merkleRoot = merkleRoot.trim(Sha256Hash.VERIBLOCK_MERKLE_ROOT_LENGTH);
        this.timestamp = timestamp;
        this.difficulty = difficulty;
        this.nonce = nonce;

        this.raw = SerializeDeserializeService.serializeHeaders(this);
        this.hash = VBlakeHash.hash(this.raw);
    }

    public int getRoundIndex() {
        return getHeight() % Constants.KEYSTONE_INTERVAL;
    }

    public boolean isKeystone() {
        return getHeight() % Constants.KEYSTONE_INTERVAL == 0;
    }

    public VBlakeHash getEffectivePreviousKeystone() {
        if (getHeight() % Constants.KEYSTONE_INTERVAL == 1) return getPreviousBlock();

        return getPreviousKeystone();
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o != null && getClass() == o.getClass() && Arrays.equals(SerializeDeserializeService.serialize(this), SerializeDeserializeService.serialize((VeriBlockBlock) o));

    }

}
