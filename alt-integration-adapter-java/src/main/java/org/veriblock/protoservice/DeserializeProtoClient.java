// VeriBlock Blockchain Project
// Copyright 2017-2018 VeriBlock, Inc
// Copyright 2018-2019 Xenios SEZC
// All rights reserved.
// https://www.veriblock.org
// Distributed under the MIT software license, see the accompanying
// file LICENSE or http://www.opensource.org/licenses/mit-license.php.

package org.veriblock.protoservice;

import org.veriblock.protoconverters.AddressProtoConverter;
import org.veriblock.protoconverters.AltPublicationProtoConverter;
import org.veriblock.protoconverters.BitcoinBlockProtoConverter;
import org.veriblock.protoconverters.BitcoinTransactionProtoConverter;
import org.veriblock.protoconverters.MerklePathProtoConverter;
import org.veriblock.protoconverters.OutputsProtoConverter;
import org.veriblock.protoconverters.PublicationDataProtoConverter;
import org.veriblock.protoconverters.VeriBlockBlockProtoConverter;
import org.veriblock.protoconverters.VeriBlockMerklePathProtoConverter;
import org.veriblock.protoconverters.VeriBlockPoPTransactionProtoConverter;
import org.veriblock.protoconverters.VeriBlockPublicationProtoConverter;
import org.veriblock.protoconverters.VeriBlockTransactionProtoConverter;
import org.veriblock.sdk.Address;
import org.veriblock.sdk.AltPublication;
import org.veriblock.sdk.BitcoinBlock;
import org.veriblock.sdk.BitcoinTransaction;
import org.veriblock.sdk.MerklePath;
import org.veriblock.sdk.Output;
import org.veriblock.sdk.Pair;
import org.veriblock.sdk.PublicationData;
import org.veriblock.sdk.Sha256Hash;
import org.veriblock.sdk.ValidationResult;
import org.veriblock.sdk.VeriBlockBlock;
import org.veriblock.sdk.VeriBlockMerklePath;
import org.veriblock.sdk.VeriBlockPoPTransaction;
import org.veriblock.sdk.VeriBlockPublication;
import org.veriblock.sdk.VeriBlockTransaction;

import com.google.protobuf.ByteString;

import integration.api.grpc.DeserializeServiceGrpc;
import integration.api.grpc.VeriBlockMessages;
import io.grpc.Channel;

public class DeserializeProtoClient {

    private final DeserializeServiceGrpc.DeserializeServiceBlockingStub service;

    public DeserializeProtoClient(Channel channel) {
        service = DeserializeServiceGrpc.newBlockingStub(channel);
    }

    public Pair<ValidationResult, AltPublication> parseAltPublication(byte[] data){        
        VeriBlockMessages.BytesArrayRequest request = VeriBlockMessages.BytesArrayRequest.newBuilder().setData(ByteString.copyFrom(data)).build();
        VeriBlockMessages.AltPublicationReply reply = service.parseAltPublication(request);
        ValidationResult resultValid = VeriBlockServiceCommon.validationResultFromProto(reply.getResult());
        if(!resultValid.isValid()) return new Pair<>(resultValid, null);
        
        AltPublication resultPublication = AltPublicationProtoConverter.fromProto(reply.getPublication());
        return new Pair<>(resultValid, resultPublication);
    }
    
    public Pair<ValidationResult, PublicationData> parsePublicationData(byte[] data){
        VeriBlockMessages.BytesArrayRequest request = VeriBlockMessages.BytesArrayRequest.newBuilder().setData(ByteString.copyFrom(data)).build();
        VeriBlockMessages.PublicationDataReply reply = service.parsePublicationData(request);
        ValidationResult resultValid = VeriBlockServiceCommon.validationResultFromProto(reply.getResult());
        if(!resultValid.isValid()) return new Pair<>(resultValid, null);
        
        PublicationData resultPublication = PublicationDataProtoConverter.fromProto(reply.getPublication());
        return new Pair<>(resultValid, resultPublication);
    }
    
    public Pair<ValidationResult, BitcoinTransaction> parseBitcoinTransaction(byte[] data){
        VeriBlockMessages.BytesArrayRequest request = VeriBlockMessages.BytesArrayRequest.newBuilder().setData(ByteString.copyFrom(data)).build();
        VeriBlockMessages.BitcoinTransactionReply reply = service.parseBitcoinTransaction(request);
        ValidationResult resultValid = VeriBlockServiceCommon.validationResultFromProto(reply.getResult());
        if(!resultValid.isValid()) return new Pair<>(resultValid, null);
        
        BitcoinTransaction resultTransaction = BitcoinTransactionProtoConverter.fromProto(reply.getTransaction());
        return new Pair<>(resultValid, resultTransaction);
    }
    
    public Pair<ValidationResult, VeriBlockBlock> parseVeriBlockBlock(byte[] data){
        VeriBlockMessages.BytesArrayRequest request = VeriBlockMessages.BytesArrayRequest.newBuilder().setData(ByteString.copyFrom(data)).build();
        VeriBlockMessages.VeriBlockBlockReply reply = service.parseVeriBlockBlock(request);
        ValidationResult resultValid = VeriBlockServiceCommon.validationResultFromProto(reply.getResult());
        if(!resultValid.isValid()) return new Pair<>(resultValid, null);
        
        VeriBlockBlock resultBlock = VeriBlockBlockProtoConverter.fromProto(reply.getBlock());
        return new Pair<>(resultValid, resultBlock);
    }
    
    public Pair<ValidationResult, VeriBlockTransaction> parseVeriBlockTransaction(byte[] data){
        VeriBlockMessages.BytesArrayRequest request = VeriBlockMessages.BytesArrayRequest.newBuilder().setData(ByteString.copyFrom(data)).build();
        VeriBlockMessages.VeriBlockTransactionReply reply = service.parseVeriBlockTransaction(request);
        ValidationResult resultValid = VeriBlockServiceCommon.validationResultFromProto(reply.getResult());
        if(!resultValid.isValid()) return new Pair<>(resultValid, null);
        
        VeriBlockTransaction resultTransaction = VeriBlockTransactionProtoConverter.fromProto(reply.getTransaction());
        return new Pair<>(resultValid, resultTransaction);
    }
    
    public Pair<ValidationResult, VeriBlockPublication> parseVeriBlockPublication(byte[] data){
        VeriBlockMessages.BytesArrayRequest request = VeriBlockMessages.BytesArrayRequest.newBuilder().setData(ByteString.copyFrom(data)).build();
        VeriBlockMessages.VeriBlockPublicationReply reply = service.parseVeriBlockPublication(request);
        ValidationResult resultValid = VeriBlockServiceCommon.validationResultFromProto(reply.getResult());
        VeriBlockPublication resultPublication = VeriBlockPublicationProtoConverter.fromProto(reply.getPublication());
        return new Pair<>(resultValid, resultPublication);
    }
    
    public Pair<ValidationResult, VeriBlockPoPTransaction> parseVeriBlockPopTx(byte[] data){
        VeriBlockMessages.BytesArrayRequest request = VeriBlockMessages.BytesArrayRequest.newBuilder().setData(ByteString.copyFrom(data)).build();
        VeriBlockMessages.VeriBlockPoPTransactionReply reply = service.parseVeriBlockPopTx(request);
        ValidationResult resultValid = VeriBlockServiceCommon.validationResultFromProto(reply.getResult());
        if(!resultValid.isValid()) return new Pair<>(resultValid, null);
        
        VeriBlockPoPTransaction resultTransaction = VeriBlockPoPTransactionProtoConverter.fromProto(reply.getTransaction());
        return new Pair<>(resultValid, resultTransaction);
    }
    
    public Pair<ValidationResult, Output> parseOutput(byte[] data){
        VeriBlockMessages.BytesArrayRequest request = VeriBlockMessages.BytesArrayRequest.newBuilder().setData(ByteString.copyFrom(data)).build();
        VeriBlockMessages.OutputReply reply = service.parseOutput(request);
        ValidationResult resultValid = VeriBlockServiceCommon.validationResultFromProto(reply.getResult());
        if(!resultValid.isValid()) return new Pair<>(resultValid, null);
        
        Output resultOutput = OutputsProtoConverter.fromProto(reply.getOutput());
        return new Pair<>(resultValid, resultOutput);
    }
    
    public Pair<ValidationResult, Address> parseAddress(byte[] data){
        VeriBlockMessages.BytesArrayRequest request = VeriBlockMessages.BytesArrayRequest.newBuilder().setData(ByteString.copyFrom(data)).build();
        VeriBlockMessages.AddressReply reply = service.parseAddress(request);
        ValidationResult resultValid = VeriBlockServiceCommon.validationResultFromProto(reply.getResult());
        if(!resultValid.isValid()) return new Pair<>(resultValid, null);
        
        Address resultAddress = AddressProtoConverter.fromProto(reply.getAddress());
        return new Pair<>(resultValid, resultAddress);
    }
    
    public Pair<ValidationResult, BitcoinBlock> parseBitcoinBlock(byte[] data){
        VeriBlockMessages.BytesArrayRequest request = VeriBlockMessages.BytesArrayRequest.newBuilder().setData(ByteString.copyFrom(data)).build();
        VeriBlockMessages.BitcoinBlockReply reply = service.parseBitcoinBlock(request);
        ValidationResult resultValid = VeriBlockServiceCommon.validationResultFromProto(reply.getResult());
        if(!resultValid.isValid()) return new Pair<>(resultValid, null);
        
        BitcoinBlock resultBlock = BitcoinBlockProtoConverter.fromProto(reply.getBlock());
        return new Pair<>(resultValid, resultBlock);
    }
    
    public Pair<ValidationResult, VeriBlockMerklePath> parseVeriBlockMerklePath(byte[] data){
        VeriBlockMessages.BytesArrayRequest request = VeriBlockMessages.BytesArrayRequest.newBuilder().setData(ByteString.copyFrom(data)).build();
        VeriBlockMessages.VeriBlockMerklePathReply reply = service.parseVeriBlockMerklePath(request);
        ValidationResult resultValid = VeriBlockServiceCommon.validationResultFromProto(reply.getResult());
        if(!resultValid.isValid()) return new Pair<>(resultValid, null);
        
        VeriBlockMerklePath resultMerklePath = VeriBlockMerklePathProtoConverter.fromProto(reply.getMerklePath());
        return new Pair<>(resultValid, resultMerklePath);
    }
    
    public Pair<ValidationResult, MerklePath> parseMerklePath(byte[] data, Sha256Hash subject){
        VeriBlockMessages.MerklePathRequest request = VeriBlockMessages.MerklePathRequest.newBuilder().setData(ByteString.copyFrom(data)).setSubject(ByteString.copyFrom(subject.getBytes())).build();
        VeriBlockMessages.MerklePathReply reply = service.parseMerklePath(request);
        ValidationResult resultValid = VeriBlockServiceCommon.validationResultFromProto(reply.getResult());
        if(!resultValid.isValid()) return new Pair<>(resultValid, null);
        
        MerklePath resultMerklePath = MerklePathProtoConverter.fromProto(reply.getMerklePath());
        return new Pair<>(resultValid, resultMerklePath);
    }
}
