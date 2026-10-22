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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

/**
 * Reads the first TLS record of a connection, which must carry the start of a
 * ClientHello, before the connection is given a handshake to work on.
 *
 * <p>A connection that has been accepted waits for its turn.  While it waits
 * it reads, on its own thread, so that a peer that goes away is noticed at
 * once, and a peer that never sends a ClientHello, or sends something that is
 * not TLS, never takes one of the places for handshakes to work on, which are
 * few because each costs memory and processor time.  What it holds while it
 * waits is its socket, its thread and the bytes of this one record, which are
 * at most 16389 and are allocated as they arrive, not when the record
 * announces its length: nothing of the engine.  The bytes are given to the
 * engine's channel, or to the SSLSocket of a socket that has no channel, when
 * the connection has its turn, so that none are lost.
 *
 * <p>The read blocks.  It is ended, as every wait of a handshake is, by the
 * owner closing the raw socket.
 */
final class FirstRecord {

    /** The bytes of a record header: type, version, length. */
    static final int HEADER = 5;

    /** The largest length of a TLS plaintext record. */
    static final int MAX_LENGTH = 16384;

    /** The record type of a handshake record. */
    private static final int HANDSHAKE = 22;

    /** The handshake message type of a ClientHello. */
    private static final int CLIENT_HELLO = 1;

    private static final int INITIAL_CAPACITY = 512;

    private FirstRecord() { }

    /** Says that the peer sent something that is not the start of a TLS handshake. */
    static final class NotTlsException extends IOException {
        private static final long serialVersionUID = 1L;

        NotTlsException(String message) {
            super(message);
        }
    }

    /**
     * Checks the bytes that have arrived of a connection's first record, as
     * far as they go.
     *
     * @param bytes the bytes read, from the start, in positions 0 to {@code
     *        have - 1}
     * @return the length of the whole record with its header, once the header
     *         has arrived, else 0
     * @throws NotTlsException if what has arrived cannot be the start of a
     *         ClientHello
     */
    static int check(byte[] bytes, int have) throws NotTlsException {
        if (have >= 1 && bytes[0] != HANDSHAKE) {
            throw new NotTlsException("not a TLS handshake record: " + (bytes[0] & 0xff));
        }
        if (have >= 2 && bytes[1] != 3) {
            throw new NotTlsException("not TLS: version " + (bytes[1] & 0xff));
        }
        if (have >= 3 && (bytes[2] & 0xff) > 4) {
            throw new NotTlsException("not TLS: version 3." + (bytes[2] & 0xff));
        }
        int total = 0;
        if (have >= HEADER) {
            int length = ((bytes[3] & 0xff) << 8) | (bytes[4] & 0xff);
            if (length < 4 || length > MAX_LENGTH) {
                throw new NotTlsException("not a ClientHello: record of " + length + " bytes");
            }
            total = HEADER + length;
        }
        if (have > HEADER && bytes[HEADER] != CLIENT_HELLO) {
            throw new NotTlsException("not a ClientHello: handshake message "
                                      + (bytes[HEADER] & 0xff));
        }
        return total;
    }

    /**
     * Reads the first record of the connection and nothing after it, and
     * returns it, header included.
     *
     * @throws NotTlsException as soon as the bytes that have arrived show that
     *         the peer is not sending a ClientHello
     * @throws EOFException if the peer closes the connection first
     * @throws IOException if the connection fails or is closed by its owner
     */
    static byte[] read(ReadableByteChannel channel) throws IOException {
        return read((bytes, off, len) -> channel.read(ByteBuffer.wrap(bytes, off, len)));
    }

    /**
     * Reads the first record from a stream, as {@link #read(ReadableByteChannel)}
     * does from a channel, for a socket that has no channel.  The stream is
     * not closed, and nothing is read from it after the record.
     */
    static byte[] read(InputStream in) throws IOException {
        return read((bytes, off, len) -> in.read(bytes, off, len));
    }

    /** Where the bytes come from. */
    private interface Source {
        /** Reads at least one and at most {@code len} bytes; -1 at the end. */
        int read(byte[] bytes, int off, int len) throws IOException;
    }

    private static byte[] read(Source source) throws IOException {
        byte[] bytes = new byte[INITIAL_CAPACITY];
        int have = 0;
        int total = 0;
        for (;;) {
            /* Never past the record: what follows it is not ours to take. */
            int wanted = total > 0 ? total : HEADER;
            if (have == bytes.length && bytes.length < wanted) {
                /* Only when what has arrived fills it. */
                bytes = java.util.Arrays.copyOf(bytes,
                    Math.min(wanted, bytes.length * 2));
            }
            int n = source.read(bytes, have, Math.min(wanted, bytes.length) - have);
            if (n < 0) {
                throw new EOFException(have == 0
                    ? "the peer closed the connection without sending anything"
                    : "the peer closed the connection in the first TLS record");
            }
            have += n;
            total = check(bytes, have);
            if (total > 0 && have >= total) {
                return java.util.Arrays.copyOf(bytes, total);
            }
        }
    }
}
