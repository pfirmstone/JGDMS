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
import javax.net.ssl.SSLException;
import net.jini.security.Security;
import org.apache.river.action.GetIntegerAction;

/**
 * Reads the whole ClientHello of a connection, before the connection is given
 * a handshake to work on.
 *
 * <p>A connection that has been accepted waits for its turn.  While it waits
 * it reads, on its own thread, so that a peer that goes away is noticed at
 * once, and a peer that never sends a ClientHello, sends something that is
 * not TLS, or sends only a part of a ClientHello, never takes one of the
 * places for handshakes to work on, which are few because each costs memory
 * and processor time, or a permit to compute.  A ClientHello is one handshake
 * message that may be split over several records, and a peer that has sent
 * one record of it has sent nothing that costs the server anything yet: the
 * work of the key exchange and the signature follows the last byte of it.
 * What a waiting connection holds is its socket, its thread and the bytes of
 * the ClientHello that have arrived, which are at most 32 812 (with the
 * default {@linkplain #MAX_MESSAGE longest message}) and are
 * allocated as they arrive, not when a record or the message announces its
 * length: nothing of the engine.  The bytes are given to the engine's
 * channel, or to the SSLSocket of a socket that has no channel, when the
 * connection has its turn, so that none are lost.
 *
 * <p>Every record is checked as it arrives, and the message is checked
 * against the longest that the JDK takes, so that the JDK is given nothing
 * that it would take long to refuse.  The number of records is bounded as
 * well: the JDK copies everything that it has of a fragmented handshake
 * message for every record that follows, so a message of 32 KiB in records of
 * one byte costs a server about 190 ms of processor time, 60 times the
 * work of a ClientHello that is answered, however few bytes of the peer's own
 * it takes.  (The bytes are not re-framed into larger records: a bound on the
 * number is as safe and takes no copy; a client that splits its ClientHello
 * into more than {@link #MAX_RECORDS} records is not one that this server
 * can serve.)  The second ClientHello of a handshake that has a
 * HelloRetryRequest is read in the same way, on the engine path as on the
 * SSLSocket path ({@link #readAfterRetry}); the engine path did not at first,
 * and put it together itself, which cost as much as the first did.  What the
 * peer sends after its ClientHellos is encrypted, and is not read here: its
 * records are counted by {@link RecordCounter}.
 *
 * <p>The read blocks.  It is ended, as every wait of a handshake is, by the
 * owner closing the raw socket.  A read that returns no byte, though it was
 * asked for some, is an error and not a reason to read again: a source that
 * does so would be read in a loop until the watchdog closes the socket.
 *
 * <p>The longest message is the one that the JDK takes, which the system
 * property {@code jdk.tls.maxHandshakeMessageSize} sets (default 32768 bytes,
 * the length of the body of the message, as the JDK measures it, without the
 * header of 4 bytes).  The property is read once, when this class is
 * initialized, as the JDK reads it, so that the two agree in a JVM that sets
 * it; a value that is not a number, that is not positive, or that is more
 * than the records of {@link #MAX_RECORDS} could carry is not used, and the
 * default is.  A server that raises it holds that many more bytes for each
 * connection that waits.
 */
final class ClientHelloReader {

    /** The bytes of a record header: type, version, length. */
    static final int HEADER = 5;

    /** The largest length of a TLS plaintext record. */
    static final int MAX_LENGTH = 16384;

    /** The system property by which the JDK sets the longest handshake message it takes. */
    static final String MAX_MESSAGE_PROPERTY = "jdk.tls.maxHandshakeMessageSize";

    /** The default of {@link #MAX_MESSAGE_PROPERTY} in the JDK. */
    static final int DEFAULT_MAX_MESSAGE = 32768;

    /**
     * The longest handshake message that the JDK takes, which is the value of
     * {@code jdk.tls.maxHandshakeMessageSize} (default 32768) as the JDK
     * measures it, the length that the header of the message gives, without
     * the 4 bytes of the header.  Read once, when the class is initialized;
     * see {@link #messageLimit(int)}.
     */
    static final int MAX_MESSAGE = messageLimit(
        ((Integer) Security.doPrivileged(
            new GetIntegerAction(MAX_MESSAGE_PROPERTY, DEFAULT_MAX_MESSAGE))).intValue());

    /**
     * The most records that a ClientHello may be split into.  A message of
     * the longest size needs three records of the longest length, and the
     * clients that fragment their handshake messages deliberately (to slip
     * past a filter, or to fit a small record size) use a few; a bound well
     * above that is far below what costs the JDK anything.
     */
    static final int MAX_RECORDS = 8;

    /**
     * Returns the longest message to use for a configured value of {@code
     * jdk.tls.maxHandshakeMessageSize}: the value, if it is positive and
     * the records of {@link #MAX_RECORDS} can carry it, else the default of
     * the JDK.  (The JDK uses any value, so that one that is not positive
     * makes it refuse every handshake message, which a server that is asked
     * to take ClientHellos is not to follow; one that the records cannot
     * carry is as good as the most they can.)
     */
    static int messageLimit(int configured) {
        return configured >= 1
            && configured <= MAX_RECORDS * MAX_LENGTH - MESSAGE_HEADER
            ? configured : DEFAULT_MAX_MESSAGE;
    }

    /** The bytes of the header of a handshake message: type, 3 bytes of length. */
    private static final int MESSAGE_HEADER = 4;

    /** The record type of a handshake record. */
    private static final int HANDSHAKE = 22;

    /** The record type of an alert record. */
    private static final int ALERT = 21;

    /** The handshake message type of a ClientHello. */
    private static final int CLIENT_HELLO = 1;

    /** The record type of a ChangeCipherSpec record. */
    private static final int CHANGE_CIPHER_SPEC = 20;

    /** The bytes of the compatibility ChangeCipherSpec record of TLS 1.3. */
    private static final int CHANGE_CIPHER_SPEC_LENGTH = HEADER + 1;

    private static final int INITIAL_CAPACITY = 512;

    private ClientHelloReader() { }

    /** Says that the peer sent something that is not a TLS ClientHello. */
    static final class NotTlsException extends IOException {
        private static final long serialVersionUID = 1L;

        NotTlsException(String message) {
            super(message);
        }
    }

    /**
     * Checks the bytes that have arrived of a connection's first record, as
     * far as they go.  The record must be a handshake record of version 3.x
     * whose length is 1 to 16384, and, once its first byte is there, that
     * byte must be the type of a ClientHello; the rest of the message may be
     * in the records that follow.
     *
     * @param bytes the bytes read, from the start, in positions 0 to {@code
     *        have - 1}
     * @return the length of the whole record with its header, once the header
     *         has arrived, else 0
     * @throws NotTlsException if what has arrived cannot be the start of a
     *         ClientHello
     */
    static int check(byte[] bytes, int have) throws NotTlsException {
        return check(bytes, 0, have, true);
    }

    /**
     * Checks the bytes of the record that begins at {@code start}, as {@link
     * #check(byte[], int)} does for the first.  A record after the first
     * carries the middle or the end of the message, whatever its first byte.
     *
     * @param have the number of bytes in the array, of which those from
     *        {@code start} belong to the record
     */
    private static int check(byte[] bytes, int start, int have, boolean first)
        throws NotTlsException
    {
        int n = have - start;
        if (n >= 1 && bytes[start] != HANDSHAKE) {
            throw new NotTlsException("not a TLS handshake record: "
                                      + (bytes[start] & 0xff));
        }
        if (n >= 2 && bytes[start + 1] != 3) {
            throw new NotTlsException("not TLS: version " + (bytes[start + 1] & 0xff));
        }
        if (n >= 3 && (bytes[start + 2] & 0xff) > 4) {
            throw new NotTlsException("not TLS: version 3." + (bytes[start + 2] & 0xff));
        }
        int total = 0;
        if (n >= HEADER) {
            int length = ((bytes[start + 3] & 0xff) << 8) | (bytes[start + 4] & 0xff);
            /*
             * Any length from 1: a handshake message may be split over
             * records, and only a record with no bytes is forbidden for a
             * handshake (RFC 8446, 5.1).
             */
            if (length < 1 || length > MAX_LENGTH) {
                throw new NotTlsException("not a ClientHello: record of " + length + " bytes");
            }
            total = HEADER + length;
        }
        if (first && n > HEADER && bytes[start + HEADER] != CLIENT_HELLO) {
            throw new NotTlsException("not a ClientHello: handshake message "
                                      + (bytes[start + HEADER] & 0xff));
        }
        return total;
    }

    /**
     * Checks the bytes of a ChangeCipherSpec record that has begun at {@code
     * start}: version 3.x and one byte, 1.
     *
     * @return the length of the record once its header is in, else 0
     */
    private static int checkChangeCipherSpec(byte[] bytes, int start, int have)
        throws NotTlsException
    {
        int n = have - start;
        if ((n >= 2 && bytes[start + 1] != 3)
            || (n >= 3 && (bytes[start + 2] & 0xff) > 4)
            || (n >= 4 && bytes[start + 3] != 0)
            || (n >= 5 && bytes[start + 4] != 1)
            || (n >= CHANGE_CIPHER_SPEC_LENGTH && bytes[start + HEADER] != 1))
        {
            throw new NotTlsException("not a ChangeCipherSpec before a ClientHello");
        }
        return n >= HEADER ? CHANGE_CIPHER_SPEC_LENGTH : 0;
    }

    /**
     * Reads the records of the connection's ClientHello, and nothing after
     * the last of them, and returns them, headers included, as they came.
     *
     * @throws NotTlsException as soon as the bytes that have arrived show that
     *         the peer is not sending a ClientHello: a record that is not
     *         one of a handshake, a message that is not a ClientHello or is
     *         longer than the JDK takes, more than {@link #MAX_RECORDS}
     *         records, or bytes after the end of the message in its last
     *         record
     * @throws EOFException if the peer closes the connection first
     * @throws IOException if the connection fails or is closed by its owner
     */
    static byte[] read(ReadableByteChannel channel) throws IOException {
        return read((bytes, off, len) -> channel.read(ByteBuffer.wrap(bytes, off, len)),
                    false);
    }

    /**
     * Reads the ClientHello from a stream, as {@link
     * #read(ReadableByteChannel)} does from a channel, for a socket that has
     * no channel.  The stream is not closed, and nothing is read from it after
     * the ClientHello.
     */
    static byte[] read(InputStream in) throws IOException {
        return read((bytes, off, len) -> in.read(bytes, off, len), false);
    }

    /**
     * Reads the second ClientHello of a handshake that the server answered
     * with a HelloRetryRequest, as {@link #read(InputStream)} reads the first.
     * The client may send the compatibility ChangeCipherSpec record of TLS
     * 1.3 (RFC 8446, appendix D.4) before it, which is returned with it, and
     * is not one of the records of the message.
     */
    static byte[] readAfterRetry(InputStream in) throws IOException {
        return read((bytes, off, len) -> in.read(bytes, off, len), true);
    }

    /**
     * Reads the second ClientHello from a source of bytes, as {@link
     * #readAfterRetry(InputStream)} does from a stream, for the engine path.
     * The source is the channel alone: this reads only what the source gives
     * it, and the caller puts the bytes returned after whatever it holds
     * itself, so that it must hold nothing, and the engine path checks that
     * it does not before it calls this.
     */
    static byte[] readAfterRetry(Source source) throws IOException {
        return read(source, true);
    }

    /** Where the bytes come from. */
    interface Source {
        /**
         * Reads at least one and at most {@code len} bytes; -1 at the end.  A
         * result of 0, for a {@code len} of more than 0, is an error of the
         * source, which the reader does not read again after.
         */
        int read(byte[] bytes, int off, int len) throws IOException;
    }

    private static byte[] read(Source source, boolean changeCipherSpec) throws IOException {
        byte[] bytes = new byte[INITIAL_CAPACITY];
        int have = 0;
        /* Where the record that is being read begins, and how long it is, or 0. */
        int start = 0;
        int total = 0;
        int records = 0;
        /* The first bytes of the message, which may be split over records. */
        byte[] header = new byte[MESSAGE_HEADER];
        int headerHave = 0;
        /* The bytes of the message in the records that are complete. */
        int payload = 0;
        /* The length of the message with its header, once it is known. */
        int message = -1;
        /* Whether a ChangeCipherSpec may still come before the message. */
        boolean ccsAllowed = changeCipherSpec;
        for (;;) {
            /* Never past the record: what follows it is not ours to take. */
            int wanted = start + (total > 0 ? total : HEADER);
            if (have == bytes.length && bytes.length < wanted) {
                /* Only when what has arrived fills it. */
                bytes = java.util.Arrays.copyOf(bytes,
                    Math.min(wanted, bytes.length * 2));
            }
            int len = Math.min(wanted, bytes.length) - have;
            int n = source.read(bytes, have, len);
            if (n < 0) {
                throw new EOFException(have == 0
                    ? "the peer closed the connection without sending anything"
                    : "the peer closed the connection in its ClientHello");
            }
            if (n == 0 && len > 0) {
                /* Asked for bytes, a blocking read gives one or ends: not a reason to spin. */
                throw new IOException("the read of the ClientHello returned no bytes"
                                      + " (" + len + " asked for)");
            }
            have += n;
            if (ccsAllowed && have > start && bytes[start] == CHANGE_CIPHER_SPEC) {
                total = checkChangeCipherSpec(bytes, start, have);
                if (total > 0 && have >= start + total) {
                    ccsAllowed = false;
                    start = have;
                    total = 0;
                }
                continue;
            }
            ccsAllowed = false;
            if (changeCipherSpec && records == 0 && have > start
                && bytes[start] == ALERT)
            {
                /*
                 * The client answers the HelloRetryRequest with an alert, as
                 * a client that cannot take what it asks for does: a TLS
                 * failure of the handshake, as the JDK counts it, and not
                 * bytes that are not TLS.
                 */
                throw new SSLException(
                    "the client sent an alert instead of a second ClientHello");
            }
            total = check(bytes, start, have, records == 0);
            if (total > 0 && message >= 0 && total - HEADER > message - payload) {
                throw new NotTlsException("not a ClientHello: a record of "
                    + (total - HEADER) + " bytes where " + (message - payload)
                    + " are left of the message");
            }
            if (total == 0 || have < start + total) {
                continue;
            }
            /* The record is in. */
            records++;
            for (int i = start + HEADER; i < have && headerHave < MESSAGE_HEADER; i++) {
                header[headerHave++] = bytes[i];
            }
            payload += total - HEADER;
            if (message < 0 && headerHave == MESSAGE_HEADER) {
                int length = ((header[1] & 0xff) << 16) | ((header[2] & 0xff) << 8)
                             | (header[3] & 0xff);
                if (length > MAX_MESSAGE) {
                    throw new NotTlsException("not a ClientHello: message of "
                                              + length + " bytes");
                }
                message = MESSAGE_HEADER + length;
            }
            if (message >= 0 && payload >= message) {
                if (payload > message) {
                    throw new NotTlsException("not a ClientHello: " + (payload - message)
                        + " bytes after the end of the message");
                }
                return java.util.Arrays.copyOf(bytes, have);
            }
            if (records >= MAX_RECORDS) {
                throw new NotTlsException("not a ClientHello that this server takes: "
                    + "more than " + MAX_RECORDS + " records");
            }
            start = have;
            total = 0;
        }
    }
}
