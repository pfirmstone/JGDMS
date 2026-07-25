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

package au.net.zeus.jgdms.showcase.demo;

import au.net.zeus.jgdms.showcase.model.CalibratedReading;
import au.net.zeus.jgdms.showcase.model.ReadingBatch;

import java.util.Arrays;
import java.util.List;

/**
 * Demonstration: "Same object, same bytes -- everywhere."
 *
 * <p>The canonical wire format writes an object's value to one, and only one, sequence
 * of bytes. Two separately built objects that are equal produce identical bytes; the
 * same value written in another run or another process produces identical bytes. That
 * makes a plain checksum (a SHA-256) of the bytes a genuine identity for the value, and
 * it makes a signature over the bytes survive a round trip through the wire.
 *
 * <p>Java's built-in serialization does not promise this: its bytes depend on how the
 * object graph was built (in particular, whether two equal parts were the same object
 * or two separate-but-equal objects), so two values that are equal can serialize to
 * different bytes.
 */
public final class SameObjectSameBytesDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("========================================================================");
        System.out.println(" Same object, same bytes -- everywhere");
        System.out.println("========================================================================");
        System.out.println(" Source: https://github.com/pfirmstone/JGDMS/blob/trunk/JGDMS/examples/wire-protocol-showcase/src/main/java/au/net/zeus/jgdms/showcase/demo/SameObjectSameBytesDemo.java");
        System.out.println();

        CalibratedReading reading = new CalibratedReading(
                1_000_042L, "Station-North", "temperature", "degreesCelsius",
                21.5, "calibration-certificate-2026-0042");

        // ---- 1. Encode the same value twice; identical bytes, identical checksum ----
        byte[] first  = ShowcaseSupport.canonicalBytes(reading);
        byte[] second = ShowcaseSupport.canonicalBytes(
                new CalibratedReading(1_000_042L, "Station-North", "temperature",
                        "degreesCelsius", 21.5, "calibration-certificate-2026-0042"));
        System.out.println("Two separately built readings with the same value, in the canonical format:");
        System.out.println("  pass 1 : " + ShowcaseSupport.hexPreview(first, 16));
        System.out.println("  pass 2 : " + ShowcaseSupport.hexPreview(second, 16));
        System.out.println("  identical bytes? " + Arrays.equals(first, second));
        System.out.println();
        System.out.println("  checksum (SHA-256) of pass 1 : " + ShowcaseSupport.sha256Hex(first));
        System.out.println("  checksum (SHA-256) of pass 2 : " + ShowcaseSupport.sha256Hex(second));
        System.out.println("  same checksum? "
                + ShowcaseSupport.sha256Hex(first).equals(ShowcaseSupport.sha256Hex(second)));
        System.out.println();
        System.out.println("  => The checksum IS the identity of the value. Anyone, anywhere, who has");
        System.out.println("     the same reading computes the same checksum, with no shared state.");
        System.out.println();

        // ---- 2. A signature survives a round trip through the wire ------------------
        byte[] key = "a-shared-signing-key".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] signatureBeforeSending = sign(first, key);
        CalibratedReading afterTrip = roundTrip(reading);
        byte[] bytesAfterTrip = ShowcaseSupport.canonicalBytes(afterTrip);
        boolean stillVerifies = Arrays.equals(signatureBeforeSending, sign(bytesAfterTrip, key));
        System.out.println("A signature made over the canonical bytes, checked after the reading has");
        System.out.println("been written out and read back in:");
        System.out.println("  bytes identical after the round trip? " + Arrays.equals(first, bytesAfterTrip));
        System.out.println("  signature still verifies?             " + stillVerifies);
        System.out.println();

        // ---- 3. Contrast with Java's built-in serialization ------------------------
        CalibratedReading r  = new CalibratedReading(7, "Station-East", "pressure",
                "kilopascal", 99.2, "calibration-certificate-2026-0007");
        CalibratedReading r2 = new CalibratedReading(7, "Station-East", "pressure",
                "kilopascal", 99.2, "calibration-certificate-2026-0007");
        ReadingBatch sameInstanceTwice = new ReadingBatch(Arrays.asList(r, r));
        ReadingBatch twoEqualInstances = new ReadingBatch(Arrays.asList(r, r2));

        System.out.println("Now two batches that are EQUAL by value:");
        System.out.println("  batch A: the same reading object appears twice");
        System.out.println("  batch B: two separate readings that are equal appear");
        System.out.println("  are the two batches equal by value? " + sameInstanceTwice.equals(twoEqualInstances));
        System.out.println();

        byte[] canonA = ShowcaseSupport.canonicalBytes(sameInstanceTwice);
        byte[] canonB = ShowcaseSupport.canonicalBytes(twoEqualInstances);
        byte[] javaA  = ShowcaseSupport.javaBuiltInSerialization(sameInstanceTwice);
        byte[] javaB  = ShowcaseSupport.javaBuiltInSerialization(twoEqualInstances);

        System.out.println("  canonical format : batch A = " + canonA.length + " bytes, batch B = "
                + canonB.length + " bytes  -> identical? " + Arrays.equals(canonA, canonB));
        System.out.println("  Java built-in    : batch A = " + javaA.length + " bytes, batch B = "
                + javaB.length + " bytes  -> identical? " + Arrays.equals(javaA, javaB));
        System.out.println();
        System.out.println("  => The canonical format gives equal values identical bytes. Java's built-in");
        System.out.println("     serialization does not: it also records whether the two parts were the");
        System.out.println("     same object, so equal values can come out as different bytes -- and a");
        System.out.println("     checksum or signature over them would not match.");
        System.out.println();

        boolean allHeld = Arrays.equals(first, second)
                && stillVerifies
                && Arrays.equals(canonA, canonB)
                && !Arrays.equals(javaA, javaB);
        System.out.println(allHeld
                ? "ALL CLAIMS HELD: canonical bytes are a stable, portable identity for a value."
                : "A CLAIM DID NOT HOLD -- see above.");
        if (!allHeld) System.exit(1);
    }

    /** Write the reading out in the canonical form and read it back into a new object. */
    public static CalibratedReading roundTrip(CalibratedReading r) throws Exception {
        au.net.zeus.jgdms.der.schema.SchemaChain.Result chain =
                au.net.zeus.jgdms.der.schema.SchemaGenerator.generateChain(CalibratedReading.class);
        byte[] payload = au.net.zeus.jgdms.der.object.ObjectCodec.encodeHierarchy(r, chain);
        return au.net.zeus.jgdms.der.object.ObjectCodec.decodeHierarchy(
                CalibratedReading.class, chain, payload);
    }

    /** A message authentication code over the bytes -- stands in for a real signature. */
    public static byte[] sign(byte[] data, byte[] key) throws Exception {
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }
}
