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

package au.net.zeus.jgdms.der.stream;

import org.apache.river.api.io.AtomicSerial;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Reading a record must not copy its payload more often than it has to. Before this was pinned, a 4 MiB {@code byte[]} record
 * was copied out of the input, rebuilt in a growing encoder, copied again to an array, re-parsed into a record that copied the
 * payload twice more, and finally decoded: about ten times the payload allocated by the read. Now it is copied once into the array
 * the decoder reads and once more for the field's own array.
 */
class DerStreamReadAllocationTest {

    @AtomicSerial
    public static final class Bytes {
        public static AtomicSerial.SerialForm[] serialForm() {
            return new AtomicSerial.SerialForm[] { new AtomicSerial.SerialForm("b", byte[].class) };
        }
        public static void serialize(AtomicSerial.PutArg arg, Bytes x) throws IOException {
            arg.put("b", x.b);
            arg.writeArgs();
        }
        final byte[] b;
        Bytes(byte[] b) { this.b = b; }
        public Bytes(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
            b = arg.get("b", new byte[0], byte[].class);
        }
    }

    private static final int SIZE = 4 * 1024 * 1024;

    private static long allocated() {
        return ((com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean())
                .getThreadAllocatedBytes(Thread.currentThread().threadId());
    }

    private static long readAllocation(byte[] stream) throws Exception {
        assumeTrue(ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean, "per-thread allocation counters");
        for (int i = 0; i < 2; i++) {                       // warm the paths
            DerObjectStreamCodec warm = new DerObjectStreamCodec();
            warm.initReader(stream);
            warm.readObject();
        }
        DerObjectStreamCodec c = new DerObjectStreamCodec();
        c.initReader(stream);
        long before = allocated();
        Object o = c.readObject();
        long used = allocated() - before;
        assertTrue(o instanceof Bytes && ((Bytes) o).b.length == SIZE, "the record decodes");
        return used;
    }

    @Test
    void aLargeOpaqueRecordIsCopiedOnlyAsOftenAsTheDecoderNeeds() throws Exception {
        DerObjectStreamCodec w = new DerObjectStreamCodec();
        w.writeObject(new Bytes(new byte[SIZE]));
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        w.drainTo(bos);
        long used = readAllocation(bos.toByteArray());
        // one copy of the payload for the decoder's input, one for the field's array, plus the small rest
        assertTrue(used < SIZE * 5 / 2, "reading a " + SIZE + " byte record allocated " + used + " bytes (" + (used / SIZE) + "x)");
    }
}
