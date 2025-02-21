// VeriBlock Blockchain Project
// Copyright 2017-2018 VeriBlock, Inc
// Copyright 2018-2019 Xenios SEZC
// All rights reserved.
// https://www.veriblock.org
// Distributed under the MIT software license, see the accompanying
// file LICENSE or http://www.opensource.org/licenses/mit-license.php.

package org.veriblock.integrations.sqlite.tables;

public class AuditorChangeData {
    public Long id;
    public String blockId;
    public String networkId;
    public Short operation;
    public Integer sequenceNum;
    public byte[] oldValue;
    public byte[] newValue;
}