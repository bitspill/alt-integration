// VeriBlock Blockchain Project
// Copyright 2017-2018 VeriBlock, Inc
// Copyright 2018-2019 Xenios SEZC
// All rights reserved.
// https://www.veriblock.org
// Distributed under the MIT software license, see the accompanying
// file LICENSE or http://www.opensource.org/licenses/mit-license.php.

package org.veriblock.sdk;

public enum  BlockType {

    VERI_BLOCK_TX((byte)0x01),
    VERI_BLOCK_POP_TX((byte)0x02);

    byte id;

    BlockType(byte id) {
        this.id = id;
    }

    public byte getId() {
        return id;
    }
}
