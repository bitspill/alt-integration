// VeriBlock Blockchain Project
// Copyright 2017-2018 VeriBlock, Inc
// Copyright 2018-2019 Xenios SEZC
// All rights reserved.
// https://www.veriblock.org
// Distributed under the MIT software license, see the accompanying
// file LICENSE or http://www.opensource.org/licenses/mit-license.php.

package org.veriblock.integrations.rewards;

public class PopEndorsement {
    private String miner;
    private String txid;
    
    public PopEndorsement(String miner, String txid) {
        this.miner = miner;
        this.txid = txid;
    }

    public String getMiner() {
        return miner;
    }
    
    public String getTxid() {
        return txid;
    }
}
