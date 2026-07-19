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
import java.nio.channels.Pipe;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Byte/framing-level tests for {@link WireFraming}, including the
 * allocate-after-validate discipline (the readNewArray-class DoS fix
 * mirrored at this new wire boundary): an oversized declared length must be
 * rejected <em>before</em> any payload buffer is allocated, and a truncated
 * payload must fail fast with a bounded {@link EOFException}.
 */
public class WireFramingTest {

    @Test
    public void roundTrip_smallPayload() throws Exception {
        Pipe pipe = Pipe.open();
        byte[] payload = "hello wire handoff".getBytes("UTF-8");
        WireFraming.writeFrame(pipe.sink(), WireFraming.Type.REQUEST, payload);
        WireFraming.Frame f = WireFraming.readFrame(pipe.source());
        assertEquals(WireFraming.Type.REQUEST, f.type);
        assertArrayEquals(payload, f.payload);
    }

    @Test
    public void roundTrip_emptyPayload() throws Exception {
        Pipe pipe = Pipe.open();
        WireFraming.writeFrame(pipe.sink(), WireFraming.Type.REPLY_OK, new byte[0]);
        WireFraming.Frame f = WireFraming.readFrame(pipe.source());
        assertEquals(WireFraming.Type.REPLY_OK, f.type);
        assertEquals(0, f.payload.length);
    }

    @Test
    public void roundTrip_nullPayloadTreatedAsEmpty() throws Exception {
        Pipe pipe = Pipe.open();
        WireFraming.writeFrame(pipe.sink(), WireFraming.Type.REPLY_ERROR, null);
        WireFraming.Frame f = WireFraming.readFrame(pipe.source());
        assertEquals(0, f.payload.length);
    }

    @Test
    public void writeFrame_refusesOversizedPayload_beforeSending() throws Exception {
        Pipe pipe = Pipe.open();
        byte[] tooBig = new byte[WireFraming.MAX_PAYLOAD_LEN + 1];
        try {
            WireFraming.writeFrame(pipe.sink(), WireFraming.Type.REQUEST, tooBig);
            fail("expected refusal to write an oversized frame");
        } catch (IOException expected) {
            // good
        }
    }

    /**
     * The core adversarial self-test: craft a header declaring a payload
     * length far above {@link WireFraming#MAX_PAYLOAD_LEN} (2^31-1, i.e.
     * ~2GB) and confirm {@code readFrame} rejects it with a bounded
     * exception <strong>before</strong> attempting {@code ByteBuffer.allocate}
     * -- never an {@link OutOfMemoryError} or a multi-gigabyte allocation
     * attempt. Only the 10-byte header is ever sent; if the implementation
     * tried to allocate first it would then block forever waiting for
     * payload bytes that never arrive, which this test would time out on --
     * so a fast, bounded exception is itself proof the ceiling check ran
     * before allocation.
     */
    @Test(timeout = 10_000)
    public void readFrame_hostileOversizedLength_rejectedBeforeAllocation() throws Exception {
        Pipe pipe = Pipe.open();
        ByteBuffer header = ByteBuffer.allocate(10);
        header.putInt(WireFraming.MAGIC);
        header.put(WireFraming.VERSION);
        header.put(WireFraming.Type.REQUEST);
        header.putInt(Integer.MAX_VALUE - 1); // ~2GB, no payload bytes follow
        header.flip();
        writeFully(pipe.sink(), header);

        try {
            WireFraming.readFrame(pipe.source());
            fail("expected the oversized declared length to be rejected");
        } catch (IOException expected) {
            assertTrue("must not be EOF -- rejection must precede any read"
                    + " attempt on the (nonexistent) payload bytes",
                    !(expected instanceof EOFException));
        }
    }

    /** A declared length exactly at the ceiling is accepted (no false reject). */
    @Test
    public void readFrame_lengthExactlyAtCeiling_notRejectedByCeilingCheck() throws Exception {
        // We don't actually push MAX_PAYLOAD_LEN bytes through a Pipe (slow);
        // instead confirm the boundary decision by using a small ceiling-
        // sized *declared* length against a deliberately truncated payload,
        // and assert the failure is EOF (payload truncation), never the
        // "out-of-bounds length" IOException -- i.e. the ceiling check
        // itself accepted the value.
        Pipe pipe = Pipe.open();
        ByteBuffer header = ByteBuffer.allocate(10);
        header.putInt(WireFraming.MAGIC);
        header.put(WireFraming.VERSION);
        header.put(WireFraming.Type.REQUEST);
        header.putInt(WireFraming.MAX_PAYLOAD_LEN);
        header.flip();
        writeFully(pipe.sink(), header);
        pipe.sink().close(); // truncate: no payload bytes, then EOF

        try {
            WireFraming.readFrame(pipe.source());
            fail("expected EOF on the truncated payload");
        } catch (EOFException expected) {
            // good: ceiling check accepted the length; failure is the
            // (separate, expected) payload truncation.
        }
    }

    @Test
    public void readFrame_truncatedPayload_failsFastWithEOF_notHang() throws Exception {
        Pipe pipe = Pipe.open();
        ByteBuffer header = ByteBuffer.allocate(10);
        header.putInt(WireFraming.MAGIC);
        header.put(WireFraming.VERSION);
        header.put(WireFraming.Type.REQUEST);
        header.putInt(1000); // declare 1000 bytes
        header.flip();
        writeFully(pipe.sink(), header);
        writeFully(pipe.sink(), ByteBuffer.wrap(new byte[10])); // only 10 delivered
        pipe.sink().close();

        try {
            WireFraming.readFrame(pipe.source());
            fail("expected EOFException on truncated payload");
        } catch (EOFException expected) {
            // good
        }
    }

    @Test
    public void readFrame_badMagic_rejected() throws Exception {
        Pipe pipe = Pipe.open();
        ByteBuffer header = ByteBuffer.allocate(10);
        header.putInt(0xDEADBEEF);
        header.put(WireFraming.VERSION);
        header.put(WireFraming.Type.REQUEST);
        header.putInt(0);
        header.flip();
        writeFully(pipe.sink(), header);
        try {
            WireFraming.readFrame(pipe.source());
            fail("expected bad-magic rejection");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("magic"));
        }
    }

    @Test
    public void readFrame_badVersion_rejected() throws Exception {
        Pipe pipe = Pipe.open();
        ByteBuffer header = ByteBuffer.allocate(10);
        header.putInt(WireFraming.MAGIC);
        header.put((byte) 99);
        header.put(WireFraming.Type.REQUEST);
        header.putInt(0);
        header.flip();
        writeFully(pipe.sink(), header);
        try {
            WireFraming.readFrame(pipe.source());
            fail("expected bad-version rejection");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("version"));
        }
    }

    @Test
    public void readFrame_badType_rejected() throws Exception {
        Pipe pipe = Pipe.open();
        ByteBuffer header = ByteBuffer.allocate(10);
        header.putInt(WireFraming.MAGIC);
        header.put(WireFraming.VERSION);
        header.put((byte) 0x7f);
        header.putInt(0);
        header.flip();
        writeFully(pipe.sink(), header);
        try {
            WireFraming.readFrame(pipe.source());
            fail("expected bad-type rejection");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("type"));
        }
    }

    @Test
    public void readFrame_negativeLength_rejected() throws Exception {
        Pipe pipe = Pipe.open();
        ByteBuffer header = ByteBuffer.allocate(10);
        header.putInt(WireFraming.MAGIC);
        header.put(WireFraming.VERSION);
        header.put(WireFraming.Type.REQUEST);
        header.putInt(-1);
        header.flip();
        writeFully(pipe.sink(), header);
        try {
            WireFraming.readFrame(pipe.source());
            fail("expected negative-length rejection");
        } catch (IOException expected) {
            // good
        }
    }

    private static void writeFully(WritableByteChannel ch, ByteBuffer buf) throws IOException {
        while (buf.hasRemaining()) ch.write(buf);
    }
}
