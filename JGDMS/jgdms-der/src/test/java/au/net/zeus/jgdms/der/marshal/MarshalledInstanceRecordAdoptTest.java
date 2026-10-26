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

package au.net.zeus.jgdms.der.marshal;

import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** {@link MarshalledInstanceRecord#adopt} and the decoder's use of it: the same record as the copying constructor, copied less. */
class MarshalledInstanceRecordAdoptTest {

    private static byte[] digest() {
        byte[] d = new byte[32];
        Arrays.fill(d, (byte) 7);
        return d;
    }

    @Test
    void anAdoptedRecordIsTheSameRecordAsACopiedOne() {
        byte[] payload = {1, 2, 3, 4};
        byte[] schema = {9, 8, 7};
        MarshalledInstanceRecord copied = new MarshalledInstanceRecord(payload, schema, digest(), MarshalledInstanceRecord.PAYLOAD_FORMAT);
        MarshalledInstanceRecord adopted = MarshalledInstanceRecord.adopt(payload.clone(), schema.clone(), digest(), MarshalledInstanceRecord.PAYLOAD_FORMAT);
        assertArrayEquals(copied.encode(), adopted.encode());
        assertArrayEquals(payload, adopted.payloadBytes());
        assertArrayEquals(schema, adopted.schemaBytes());
        assertArrayEquals(digest(), adopted.schemaDigest());
        assertEquals(MarshalledInstanceRecord.PAYLOAD_FORMAT, adopted.payloadFormat());
    }

    @Test
    void anAdoptedRecordStillNeverExposesItsArrays() {
        MarshalledInstanceRecord adopted = MarshalledInstanceRecord.adopt(new byte[] {1, 2}, new byte[] {3}, digest(), "f");
        adopted.payloadBytes()[0] = 99;
        adopted.schemaBytes()[0] = 99;
        adopted.schemaDigest()[0] = 99;
        assertArrayEquals(new byte[] {1, 2}, adopted.payloadBytes());
        assertArrayEquals(new byte[] {3}, adopted.schemaBytes());
        assertArrayEquals(digest(), adopted.schemaDigest());
    }

    @Test
    void adoptMakesTheConstructorsChecks() {
        assertThrows(IllegalArgumentException.class, () -> MarshalledInstanceRecord.adopt(new byte[0], new byte[0], new byte[31], "f"));
        assertThrows(NullPointerException.class, () -> MarshalledInstanceRecord.adopt(null, new byte[0], digest(), "f"));
        assertThrows(NullPointerException.class, () -> MarshalledInstanceRecord.adopt(new byte[0], null, digest(), "f"));
        assertThrows(NullPointerException.class, () -> MarshalledInstanceRecord.adopt(new byte[0], new byte[0], null, "f"));
        assertThrows(NullPointerException.class, () -> MarshalledInstanceRecord.adopt(new byte[0], new byte[0], digest(), null));
    }

    @Test
    void decodingCopiesThePayloadOnce() throws Exception {
        assumeTrue(ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean, "per-thread allocation counters");
        int size = 4 * 1024 * 1024;
        byte[] der = new MarshalledInstanceRecord(new byte[size], new byte[10], digest(),
                MarshalledInstanceRecord.PAYLOAD_FORMAT).encode();
        com.sun.management.ThreadMXBean tm = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        for (int i = 0; i < 3; i++) {
            MarshalledInstanceRecord.decode(der);                       // warm
        }
        long id = Thread.currentThread().threadId();
        long before = tm.getThreadAllocatedBytes(id);
        MarshalledInstanceRecord rec = MarshalledInstanceRecord.decode(der);
        long used = tm.getThreadAllocatedBytes(id) - before;
        assertEquals(size, rec.payloadView().length);
        assertTrue(used < size * 3 / 2, "decoding a " + size + " byte payload allocated " + used + " bytes");
    }
}
