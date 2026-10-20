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

package au.net.zeus.jgdms.der;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.Arrays;

/**
 * B1 measurement: plain bounded read vs item-framed bounded read, on benign streams of the corpus sizes and on a hostile
 * stream (one item declaring more than the budget, followed by a full budget of bytes). Not a test: run
 * {@code java -cp target/classes:target/test-classes au.net.zeus.jgdms.der.DerFramedReadMicroBench}.
 */
public final class DerFramedReadMicroBench {
    interface Work { void run() throws Exception; }
    private static final com.sun.management.ThreadMXBean MX = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();

    static void measure(String label, int n, Work w) throws Exception {
        for (int i = 0; i < n; i++) w.run();
        double[] ns = new double[9], al = new double[9];
        for (int r = 0; r < 9; r++) {
            long a0 = MX.getCurrentThreadAllocatedBytes(), t0 = System.nanoTime();
            for (int i = 0; i < n; i++) w.run();
            ns[r] = (double) (System.nanoTime() - t0) / n;
            al[r] = (double) (MX.getCurrentThreadAllocatedBytes() - a0) / n;
        }
        Arrays.sort(ns); Arrays.sort(al);
        System.out.printf("%-34s %12.0f ns/op %12.0f B/op%n", label, ns[4], al[4]);
    }

    static byte[] benign(int items, int contentLen) {
        java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
        o.writeBytes(new byte[] {(byte) 0x8F, 1, 1});
        for (int i = 0; i < items; i++) o.writeBytes(DerWriter.writeOctetString(new byte[contentLen]));
        return o.toByteArray();
    }

    public static void main(String[] a) throws Exception {
        int max = DerInputLimits.DEFAULT.maxInputBytes();
        for (Object[] c : new Object[][] {{"benign 300 B (1 item)", benign(1, 290)}, {"benign 14 KB (100 items)", benign(100, 130)},
                {"benign 66 KB (1 item)", benign(1, 65600)}}) {
            byte[] s = (byte[]) c[1];
            measure("plain  " + c[0], 20000, () -> DerInputLimits.readAllBytesBounded(new ByteArrayInputStream(s), max));
            measure("framed " + c[0], 20000, () -> DerInputLimits.readFramedBounded(new ByteArrayInputStream(s), max));
        }
        byte[] hostile = new byte[max + 64];
        hostile[0] = 0x04; hostile[1] = (byte) 0x84; hostile[2] = 0x7F; hostile[3] = (byte) 0xFF; hostile[4] = (byte) 0xFF; hostile[5] = (byte) 0xFF;
        measure("plain  hostile (16 MiB declared)", 40, () -> { try { DerInputLimits.readAllBytesBounded(new ByteArrayInputStream(hostile), max); } catch (IOException e) { } });
        measure("framed hostile (16 MiB declared)", 40, () -> { try { DerInputLimits.readFramedBounded(new ByteArrayInputStream(hostile), max); } catch (IOException e) { } });
        // a hostile stream that is just max+1 bytes of well-formed 1 MiB items (cannot be refused at a header)
        byte[] big = benign(17, 1 << 20);
        measure("plain  17 x 1 MiB items", 20, () -> { try { DerInputLimits.readAllBytesBounded(new ByteArrayInputStream(big), max); } catch (IOException e) { } });
        measure("framed 17 x 1 MiB items", 20, () -> { try { DerInputLimits.readFramedBounded(new ByteArrayInputStream(big), max); } catch (IOException e) { } });
    }
}
