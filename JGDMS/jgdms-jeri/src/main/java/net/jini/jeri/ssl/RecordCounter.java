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
 * Counts the records among the bytes that a server reads from a peer while a
 * handshake runs, and refuses a peer that sends many more of them than the
 * bytes it sends could need.
 *
 * <p>The JDK takes each record of a handshake message on its own, and for
 * every record that does not complete the message it does work and copies
 * what it has of the message, so that a message of 30 KiB in records that
 * carry six bytes each costs a server 100 milliseconds of processor time and
 * often several times that, and in records of one byte more.  The ClientHello
 * is read whole, by {@link ClientHelloReader}, before any work is done for
 * it.  What the peer sends after the server's first flight is encrypted: the
 * server cannot tell the records of the peer's Certificate message from the
 * records of a flood without the JDK, which decrypts them, and the peer,
 * which has the keys, can frame them as it likes.  What the server can see
 * is the framing: a record has a header of five bytes whatever it carries.
 *
 * <p>So the bytes that follow the ClientHellos are counted, whatever the
 * records carry, as the SSLSocket path reads them, and on the engine path as
 * the engine consumes them, a record at a time (so that what the peer sends
 * behind its Finished, which the handshake does not take, is not counted),
 * and the peer is refused when it has sent more
 * records than {@link #FREE} and one for every {@link #BYTES_PER_RECORD}
 * bytes that it has sent.  A client that frames its flight as the standard
 * allows, in records of up to 16 KiB or, if it asked for a smaller size (RFC
 * 6066), of 512 bytes, never comes near it; one that splits a message into
 * records of a few bytes is refused after the first few.
 *
 * <p>Not thread-safe: it is used by the thread that reads the connection.
 */
final class RecordCounter {

    /**
     * The records that a peer may send however few bytes they carry: the
     * records of a handshake that has a HelloRetryRequest, and the
     * ChangeCipherSpec records, are well within it.
     */
    static final int FREE = 16;

    /**
     * The bytes with which the peer pays for a further record: well below the
     * smallest record size that a client may ask for, which is 512 bytes
     * of plain text, so that every legitimate framing is within the bound.
     */
    static final int BYTES_PER_RECORD = 256;

    /** The bytes of the header of a record: type, version, length. */
    private static final int HEADER = ClientHelloReader.HEADER;

    private final byte[] header = new byte[HEADER];

    /** The bytes of the header of the record that are in. */
    private int headerHave;

    /** The bytes of the record that is under way that are still to come. */
    private int left;

    private long records;
    private long bytes;

    /**
     * Counts the bytes {@code b[off]} to {@code b[off + len - 1]}, which are
     * the next that the peer has sent, from a record boundary if they are the
     * first.
     *
     * @throws ClientHelloReader.NotTlsException if the peer has sent more
     *         records than its bytes allow
     */
    void count(byte[] b, int off, int len) throws ClientHelloReader.NotTlsException {
        int end = off + len;
        int i = off;
        while (i < end) {
            if (left > 0) {
                int n = Math.min(left, end - i);
                left -= n;
                i += n;
                continue;
            }
            header[headerHave++] = b[i++];
            if (headerHave == HEADER) {
                headerHave = 0;
                left = ((header[3] & 0xff) << 8) | (header[4] & 0xff);
                records++;
                long seen = bytes + (i - off);
                if (records > FREE + seen / BYTES_PER_RECORD) {
                    throw new ClientHelloReader.NotTlsException(
                        "more records than a handshake needs: " + records
                        + " in " + seen + " bytes");
                }
            }
        }
        bytes += len;
    }
}
