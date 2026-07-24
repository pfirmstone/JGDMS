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

package au.net.zeus.jgdms.showcase;

import au.net.zeus.jgdms.showcase.demo.SameObjectSameBytesDemo;
import au.net.zeus.jgdms.showcase.demo.ShowcaseSupport;
import au.net.zeus.jgdms.showcase.model.CalibratedReading;
import au.net.zeus.jgdms.showcase.model.ReadingBatch;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Automated checks for the "same object, same bytes" claim. A viewer or a build server
 * can run these to confirm the demonstration is telling the truth.
 */
class SameObjectSameBytesTest {

    private static CalibratedReading reading() {
        return new CalibratedReading(1_000_042L, "Station-North", "temperature",
                "degreesCelsius", 21.5, "calibration-certificate-2026-0042");
    }

    @Test
    void equalValues_produceIdenticalCanonicalBytes_andChecksum() throws Exception {
        CalibratedReading a = reading();
        CalibratedReading b = reading();
        assertNotSame(a, b);
        byte[] ba = ShowcaseSupport.canonicalBytes(a);
        byte[] bb = ShowcaseSupport.canonicalBytes(b);
        assertArrayEquals(ba, bb, "equal values must give identical canonical bytes");
        assertEquals(ShowcaseSupport.sha256Hex(ba), ShowcaseSupport.sha256Hex(bb),
                "identical bytes must give the same checksum");
    }

    @Test
    void signature_survivesRoundTrip() throws Exception {
        CalibratedReading r = reading();
        byte[] key = "a-shared-signing-key".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] before = SameObjectSameBytesDemo.sign(ShowcaseSupport.canonicalBytes(r), key);

        CalibratedReading afterTrip = SameObjectSameBytesDemo.roundTrip(r);
        assertEquals(r, afterTrip, "round trip must preserve the value");
        byte[] after = SameObjectSameBytesDemo.sign(ShowcaseSupport.canonicalBytes(afterTrip), key);
        assertArrayEquals(before, after, "a signature over the canonical bytes must survive the round trip");
    }

    @Test
    void canonicalFormat_ignoresObjectSharing_butJavaSerializationDoesNot() throws Exception {
        CalibratedReading r  = new CalibratedReading(7, "Station-East", "pressure",
                "kilopascal", 99.2, "calibration-certificate-2026-0007");
        CalibratedReading r2 = new CalibratedReading(7, "Station-East", "pressure",
                "kilopascal", 99.2, "calibration-certificate-2026-0007");
        ReadingBatch sameInstanceTwice = new ReadingBatch(Arrays.asList(r, r));
        ReadingBatch twoEqualInstances = new ReadingBatch(Arrays.asList(r, r2));
        assertEquals(sameInstanceTwice, twoEqualInstances, "the two batches are equal by value");

        // Canonical: equal values -> identical bytes, regardless of object sharing.
        assertArrayEquals(
                ShowcaseSupport.canonicalBytes(sameInstanceTwice),
                ShowcaseSupport.canonicalBytes(twoEqualInstances),
                "canonical bytes depend only on the value");

        // Java's built-in serialization: sharing changes the bytes, so equal values differ.
        byte[] javaA = ShowcaseSupport.javaBuiltInSerialization(sameInstanceTwice);
        byte[] javaB = ShowcaseSupport.javaBuiltInSerialization(twoEqualInstances);
        assertFalse(Arrays.equals(javaA, javaB),
                "Java's built-in serialization gives equal values DIFFERENT bytes here "
                + "(it records object sharing) -- which is exactly why its bytes are not an identity");
    }

    @Test
    void demoMainRunsGreen() throws Exception {
        // The demo's main() exits non-zero if any claim fails; here it must complete.
        SameObjectSameBytesDemo.main(new String[0]);
    }
}
