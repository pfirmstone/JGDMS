/*
 * Copyright 2026 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.net.zeus.jgdms.loader.isolation;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

/**
 * Byte/framing-level wire protocol for the client&harr;subprocess wire
 * handoff (task&nbsp;T4).  This is the concrete framing the wiring SOW asked
 * for -- not just the mechanism-level description.
 *
 * <h2>Frame layout</h2>
 * <pre>
 *   offset  0 : int32  magic     = 0x53504857  ("SPHW")
 *   offset  4 : byte   version   = 1
 *   offset  5 : byte   type      (see {@link Type})
 *   offset  6 : int32  length    payload length in bytes, 0 &le; length &le; {@link #MAX_PAYLOAD_LEN}
 *   offset 10 : byte[length]     payload
 * </pre>
 * All multi-byte integers are big-endian (network byte order). The fixed
 * 10-byte header is always read/written first, in full, before the payload
 * is touched.
 *
 * <h2>Allocate-after-validate discipline (readNewArray-class hardening)</h2>
 * The {@code length} field is attacker-influenced on every read this class
 * performs on the subprocess side (a hostile/compromised peer on the other
 * end of the channel).  Mirroring the fix applied to
 * {@code AtomicMarshalInputStream}'s {@code readNewArray} /
 * {@code readBlockDataLong} / {@code decodeUTF} (a wire-declared count must
 * never size an allocation before it is validated), {@link #readFrame} checks
 * {@code length} against {@link #MAX_PAYLOAD_LEN} <strong>before</strong>
 * calling {@code ByteBuffer.allocate(length)} -- a hostile length far above
 * the ceiling is rejected with a bounded {@link IOException} and commits no
 * memory. Below the ceiling, the payload is still read incrementally
 * (looping {@link ReadableByteChannel#read(ByteBuffer)} until full or EOF)
 * so a truncated payload fails fast with a bounded {@link EOFException}
 * rather than a channel read hanging or silently returning a short buffer.
 *
 * @since 3.1.1
 */
final class WireFraming {

    /** {@code "SPHW"} -- SubProcess Handoff Wire. */
    static final int MAGIC = 0x53504857;

    static final byte VERSION = 1;

    /**
     * Ceiling on a single frame's payload length, checked <em>before</em>
     * any allocation. Generous for a marshalled control/business message
     * (interface names, a marshalled {@code MarshalledInstance} envelope, a
     * marshalled bootstrap-proxy stub, invocation arguments/results) while
     * still bounding worst-case memory commitment from a single hostile
     * length field. Codebase JAR bytes never travel over this channel --
     * those are fetched by the subprocess directly from the codebase URLs,
     * exactly as the legacy in-process path does.
     */
    static final int MAX_PAYLOAD_LEN = 64 * 1024 * 1024; // 64 MiB

    private static final int HEADER_LEN = 10;

    private WireFraming() { }

    /** Frame message types. */
    static final class Type {
        /** client&rarr;subprocess: a {@code WireHandoffRequest}. */
        static final byte REQUEST = 1;
        /** subprocess&rarr;client: a successful {@code WireHandoffReply}. */
        static final byte REPLY_OK = 2;
        /** subprocess&rarr;client: a bounded, closed-vocabulary error. */
        static final byte REPLY_ERROR = 3;
        /** client&rarr;subprocess: a business method invocation. */
        static final byte INVOKE_REQUEST = 4;
        /** subprocess&rarr;client: a normal invocation return value. */
        static final byte INVOKE_REPLY_RESULT = 5;
        /** subprocess&rarr;client: the invoked method threw. */
        static final byte INVOKE_REPLY_EXCEPTION = 6;

        private Type() { }

        static boolean isValid(byte t) {
            return t == REQUEST || t == REPLY_OK || t == REPLY_ERROR
                    || t == INVOKE_REQUEST || t == INVOKE_REPLY_RESULT
                    || t == INVOKE_REPLY_EXCEPTION;
        }
    }

    /** A decoded frame: message type + raw payload bytes. */
    static final class Frame {
        final byte type;
        final byte[] payload;

        Frame(byte type, byte[] payload) {
            this.type = type;
            this.payload = payload;
        }
    }

    /**
     * Writes one frame. The whole header+payload is assembled in a single
     * buffer and written with a bounded retry loop so a partial
     * {@code write} (legal for a {@link WritableByteChannel}) does not
     * silently truncate the frame on the wire.
     */
    static void writeFrame(WritableByteChannel ch, byte type, byte[] payload)
            throws IOException {
        if (payload == null) payload = new byte[0];
        if (payload.length > MAX_PAYLOAD_LEN) {
            // Never attempt to SEND an over-ceiling frame either: a bug that
            // built too large a payload must fail loudly here, not silently
            // ship a frame the peer's own ceiling will bounce.
            throw new IOException(
                "Refusing to write oversized wire-handoff frame: "
                + payload.length + " bytes > ceiling " + MAX_PAYLOAD_LEN);
        }
        ByteBuffer buf = ByteBuffer.allocate(HEADER_LEN + payload.length);
        buf.putInt(MAGIC);
        buf.put(VERSION);
        buf.put(type);
        buf.putInt(payload.length);
        buf.put(payload);
        buf.flip();
        writeFully(ch, buf);
    }

    /**
     * Reads one frame. Validates the header (magic, version, type) and the
     * declared payload length against {@link #MAX_PAYLOAD_LEN}
     * <strong>before</strong> allocating the payload buffer -- the
     * allocate-after-validate discipline this class exists to enforce.
     *
     * @throws EOFException if the channel is exhausted before a complete
     *         frame (header or payload) has been read -- including a
     *         truncated payload on an otherwise-valid, in-bounds length
     * @throws IOException  if the header is malformed (bad magic/version/
     *         type) or the declared length exceeds {@link #MAX_PAYLOAD_LEN}
     */
    static Frame readFrame(ReadableByteChannel ch) throws IOException {
        ByteBuffer header = ByteBuffer.allocate(HEADER_LEN);
        readFully(ch, header, "frame header");
        header.flip();
        int magic = header.getInt();
        if (magic != MAGIC) {
            throw new IOException(
                "Bad wire-handoff frame magic: 0x"
                + Integer.toHexString(magic) + " (expected 0x"
                + Integer.toHexString(MAGIC) + "); refusing (fail-closed).");
        }
        byte version = header.get();
        if (version != VERSION) {
            throw new IOException(
                "Unsupported wire-handoff frame version: " + version
                + " (expected " + VERSION + "); refusing (fail-closed).");
        }
        byte type = header.get();
        if (!Type.isValid(type)) {
            throw new IOException(
                "Unrecognised wire-handoff frame type: " + type
                + "; refusing (fail-closed).");
        }
        int length = header.getInt();
        // ---- Allocate-after-validate: reject BEFORE ByteBuffer.allocate ----
        if (length < 0 || length > MAX_PAYLOAD_LEN) {
            throw new IOException(
                "Wire-handoff frame declares an out-of-bounds payload"
                + " length (" + length + "; ceiling " + MAX_PAYLOAD_LEN
                + "); refusing before allocation (fail-closed, denial of"
                + " service guard).");
        }
        byte[] payload;
        if (length == 0) {
            payload = new byte[0];
        } else {
            ByteBuffer body = ByteBuffer.allocate(length);
            readFully(ch, body, "frame payload");
            payload = body.array();
        }
        return new Frame(type, payload);
    }

    private static void writeFully(WritableByteChannel ch, ByteBuffer buf)
            throws IOException {
        while (buf.hasRemaining()) {
            int n = ch.write(buf);
            if (n < 0) {
                throw new EOFException(
                    "Channel closed while writing wire-handoff frame");
            }
        }
    }

    private static void readFully(ReadableByteChannel ch, ByteBuffer buf,
                                  String what) throws IOException {
        while (buf.hasRemaining()) {
            int n = ch.read(buf);
            if (n < 0) {
                throw new EOFException(
                    "Channel closed / truncated while reading wire-handoff "
                    + what + " (" + buf.position() + "/" + buf.capacity()
                    + " bytes read)");
            }
            // n == 0 is legal for a non-blocking channel with nothing
            // currently available; callers of this package-private helper
            // are only ever given blocking channels (real UDS/TCP sockets),
            // so a busy-loop here is bounded in practice. Documented rather
            // than silently assumed.
        }
    }
}
