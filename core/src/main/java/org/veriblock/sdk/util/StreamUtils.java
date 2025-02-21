// VeriBlock Blockchain Project
// Copyright 2017-2018 VeriBlock, Inc
// Copyright 2018-2019 Xenios SEZC
// All rights reserved.
// https://www.veriblock.org
// Distributed under the MIT software license, see the accompanying
// file LICENSE or http://www.opensource.org/licenses/mit-license.php.

package org.veriblock.sdk.util;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

public class StreamUtils {
    public static void writeSingleByteLengthValueToStream(OutputStream stream, int value) throws IOException {
        byte[] trimmed = Utils.trimmedByteArrayFromInteger(value);
        stream.write(trimmed.length);
        stream.write(trimmed);
    }

    public static void writeSingleByteLengthValueToStream(OutputStream stream, long value) throws IOException {
        byte[] trimmed = Utils.trimmedByteArrayFromLong(value);
        stream.write(trimmed.length);
        stream.write(trimmed);
    }

    public static void writeVariableLengthValueToStream(OutputStream stream, byte[] value) throws IOException {
        byte[] dataSize = Utils.trimmedByteArrayFromInteger(value.length);
        stream.write((byte)dataSize.length);
        stream.write(dataSize);
        stream.write(value);
    }

    public static void writeSingleByteLengthValueToStream(OutputStream stream, byte[] value) throws IOException {
        stream.write((byte)value.length);
        stream.write(value);
    }

    public static byte[] getSingleByteLengthValue(ByteBuffer buffer, int maxLength, int minLength) {
        int length = buffer.get();
        checkLength(length, maxLength, minLength);
        
        byte[] value = new byte[length];
        buffer.get(value);

        return value;
    }
    
    public static byte[] getSingleByteLengthValue(ByteBuffer buffer) {
        return getSingleByteLengthValue(buffer, 255, 0);
    }

    public static byte[] getVariableLengthValue(ByteBuffer buffer, int maxLength, int minLength) {
        byte lengthLength = buffer.get();
        checkLength(lengthLength, 4, 0);
        
        byte[] lengthBytes = new byte[4];
        buffer.get(lengthBytes, 4 - lengthLength, lengthLength);

        int length = ByteBuffer.wrap(lengthBytes).getInt();
        checkLength(length, maxLength, minLength);
        
        byte[] value = new byte[length];
        buffer.get(value);

        return value;
    }
    
    public static byte[] getVariableLengthValue(ByteBuffer buffer) {  
        return getVariableLengthValue(buffer, Integer.MAX_VALUE, 0);
    }
    
    private static void checkLength(int length, int maxLength, int minLength)
    {
        if (length < minLength || length > maxLength)
            throw new IllegalArgumentException("Unexpected length: " + length
                + " (expected a value between " + minLength
                + " and " + maxLength + ")");
    }
}
