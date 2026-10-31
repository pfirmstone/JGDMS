/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.river.api.io;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.Test;

/**
 * Invariant: primitive data that our own writer puts outside any {@code writeObject} (for
 * example the reducing-context block a client sends under a SecurityManager) must be
 * readable at the top level of a fresh stream, whatever its length up to the writer's block
 * limit; a declared block longer than that limit must still be refused.
 *
 * <p>The array-length budget is 0 outside {@code readObject}, so before the fix every
 * top-level long-form block (256 to 1024 bytes) was refused, though the writer emits them.
 */
public class AtomicMarshalInputStreamTopLevelBlockTest {

    private static final byte TC_BLOCKDATALONG = 0x7A;

    private static byte[] payload(int n) {
        byte[] b = new byte[n];
        for (int i = 0; i < n; i++) b[i] = (byte) (i * 31 + 7);
        return b;
    }

    private static byte[] writeTopLevel(byte[] data) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream out = new AtomicMarshalOutputStream(baos, null);
        out.write(data);
        out.flush();
        return baos.toByteArray();
    }

    private static ObjectInputStream open(byte[] wire) throws Exception {
        return AtomicMarshalInputStream.create(
                new ByteArrayInputStream(wire), null, false, null, null, false);
    }

    private static void roundTrips(int n) throws Exception {
        byte[] data = payload(n);
        ObjectInputStream in = open(writeTopLevel(data));
        byte[] back = new byte[n];
        in.readFully(back);
        assertArrayEquals("top-level run of " + n + " bytes", data, back);
    }

    @Test
    public void shortTopLevelRunRoundTrips() throws Exception {
        roundTrips(200); // short-form block: the control, passed before the fix
    }

    @Test
    public void longFormTopLevelRunRoundTrips() throws Exception {
        roundTrips(300); // long-form block, over 255 and under the writer's limit
    }

    @Test
    public void multiBlockTopLevelRunRoundTrips() throws Exception {
        roundTrips(1100); // the writer cuts this into a 1024-byte block and a 76-byte block
    }

    @Test
    public void blockAtTheWriterLimitIsAccepted() throws Exception {
        roundTrips(1024);
    }

    /** A wire stream that declares a long-form block of {@code declared} bytes at top level. */
    private static ObjectInputStream hostile(int declared) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // Take the real stream header from our own writer, then append a hand-made block.
        ObjectOutputStream out = new AtomicMarshalOutputStream(baos, null);
        out.flush();
        baos.write(TC_BLOCKDATALONG);
        baos.write(declared >>> 24);
        baos.write(declared >>> 16);
        baos.write(declared >>> 8);
        baos.write(declared);
        return open(baos.toByteArray());
    }

    private static void refused(int declared) throws Exception {
        ObjectInputStream in = hostile(declared);
        try {
            in.read();
            fail("a top-level block declaring " + declared + " bytes was not refused");
        } catch (IOException expected) {
            assertTrue(expected.toString(),
                    String.valueOf(expected.getMessage()).contains("excessively long"));
        }
    }

    @Test
    public void hostileHugeTopLevelBlockIsRefused() throws Exception {
        refused(Integer.MAX_VALUE);
    }

    @Test
    public void blockOneOverTheWriterLimitIsRefused() throws Exception {
        refused(ObjOutputStream.MAX_BLOCK_DATA_LEN + 1); // the writer never emits more in one block
    }

    @Test
    public void negativeDeclaredLengthIsRefused() throws Exception {
        refused(-1);
    }
}
