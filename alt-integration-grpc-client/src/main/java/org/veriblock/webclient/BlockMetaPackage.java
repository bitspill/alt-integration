// VeriBlock Blockchain Project
// Copyright 2017-2018 VeriBlock, Inc
// Copyright 2018-2019 Xenios SEZC
// All rights reserved.
// https://www.veriblock.org
// Distributed under the MIT software license, see the accompanying
// file LICENSE or http://www.opensource.org/licenses/mit-license.php.

package org.veriblock.webclient;

import org.veriblock.sdk.Sha256Hash;

public class BlockMetaPackage {
    private final Sha256Hash hash;
    public Sha256Hash getHash() {
        return hash;
    }

    public BlockMetaPackage(Sha256Hash hash) {
        this.hash = hash;
    }
}
