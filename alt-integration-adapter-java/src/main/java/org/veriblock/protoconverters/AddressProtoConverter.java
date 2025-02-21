// VeriBlock Blockchain Project
// Copyright 2017-2018 VeriBlock, Inc
// Copyright 2018-2019 Xenios SEZC
// All rights reserved.
// https://www.veriblock.org
// Distributed under the MIT software license, see the accompanying
// file LICENSE or http://www.opensource.org/licenses/mit-license.php.

package org.veriblock.protoconverters;

import org.veriblock.sdk.Address;

import integration.api.grpc.VeriBlockMessages;

public final class AddressProtoConverter {

    private AddressProtoConverter() {} //never
    
    public static Address fromProto(VeriBlockMessages.Address protoData) {
        Address result = new Address(protoData.getAddress());
        return result;
    }
    
    public static VeriBlockMessages.Address toProto(Address data) {
        VeriBlockMessages.Address.Builder result = VeriBlockMessages.Address.newBuilder();
        result = result.setAddress(data.toString());
        return result.build();
    }
}
