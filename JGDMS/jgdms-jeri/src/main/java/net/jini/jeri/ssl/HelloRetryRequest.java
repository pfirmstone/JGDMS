/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.jini.jeri.ssl;

/**
 * Recognises a HelloRetryRequest among the bytes that a server writes.
 *
 * <p>A client whose key share is for a group that the server does not enable
 * is not refused: the server answers with a HelloRetryRequest, which is a
 * ServerHello whose random is a fixed value (RFC 8446, 4.1.3), and the client
 * sends a second ClientHello.  The work of the key exchange and the signature
 * is that of the second flight, which follows the second ClientHello, so a
 * handshake that holds a permit for the work of its first flight must hold one
 * for that too, and none while it waits for the second ClientHello.
 */
final class HelloRetryRequest {

    /**
     * The bytes of the record header, the header of the handshake message and
     * its legacy version that come before the random.
     */
    private static final int RANDOM_AT = 5 + 4 + 2;

    /** The random of a HelloRetryRequest: the SHA-256 of "HelloRetryRequest". */
    private static final byte[] RANDOM = {
        (byte) 0xCF, 0x21, (byte) 0xAD, 0x74, (byte) 0xE5, (byte) 0x9A, 0x61, 0x11,
        (byte) 0xBE, 0x1D, (byte) 0x8C, 0x02, 0x1E, 0x65, (byte) 0xB8, (byte) 0x91,
        (byte) 0xC2, (byte) 0xA2, 0x11, 0x16, 0x7A, (byte) 0xBB, (byte) 0x8C, 0x5E,
        0x07, (byte) 0x9E, 0x09, (byte) 0xE2, (byte) 0xC8, (byte) 0xA8, 0x33, (byte) 0x9C
    };

    /** The bytes from the start of a flight that tell whether it is one. */
    static final int LENGTH = RANDOM_AT + RANDOM.length;

    private HelloRetryRequest() { }

    /**
     * Returns whether the bytes are the start of a handshake record that
     * carries a HelloRetryRequest.  Fewer than {@link #LENGTH} bytes cannot
     * tell, and say no.
     */
    static boolean starts(byte[] bytes, int off, int len) {
        if (len < LENGTH || bytes[off] != 22 || bytes[off + 5] != 2) {
            return false;
        }
        for (int i = 0; i < RANDOM.length; i++) {
            if (bytes[off + RANDOM_AT + i] != RANDOM[i]) {
                return false;
            }
        }
        return true;
    }
}
