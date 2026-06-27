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

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The bounded eager-read DoS guard (finding A): refuse to buffer more than the limit. */
public class DerInputLimitsTest {

    private static byte[] bytes(int n) {
        byte[] b = new byte[n];
        for (int i = 0; i < n; i++) b[i] = (byte) i;
        return b;
    }

    @Test
    public void underLimitReturnsAllBytes() throws Exception {
        byte[] data = bytes(100);
        byte[] out = DerInputLimits.readAllBytesBounded(new ByteArrayInputStream(data), 1000);
        assertArrayEquals(data, out);
    }

    @Test
    public void exactlyAtLimitIsAccepted() throws Exception {
        byte[] data = bytes(256);
        byte[] out = DerInputLimits.readAllBytesBounded(new ByteArrayInputStream(data), 256);
        assertEquals(256, out.length);
    }

    @Test
    public void oneOverLimitIsRejected() {
        byte[] data = bytes(257);
        IOException ex = assertThrows(IOException.class,
                () -> DerInputLimits.readAllBytesBounded(new ByteArrayInputStream(data), 256));
        // never buffers the whole oversize stream -- just enough (limit + 1) to detect it
    }

    @Test
    public void hugeLimitDisablesTheCap() throws Exception {
        // Operator-disabled cap (>= MAX_VALUE-8): falls back to readAllBytes, no overflow.
        byte[] data = bytes(50);
        byte[] out = DerInputLimits.readAllBytesBounded(new ByteArrayInputStream(data), Integer.MAX_VALUE);
        assertArrayEquals(data, out);
    }
}
