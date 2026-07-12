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
package au.net.zeus.jgdms.api.codebase;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.MessageDigest;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import net.pack200.Normalize;
import org.apache.river.api.io.AtomicMarshalInputStream;
import org.apache.river.api.io.AtomicMarshalOutputStream;
import org.apache.river.api.net.Uri;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests the JGDMS-STD-002 content-hash binding verification performed by
 * {@link AnalysisRequest} during AtomicSerial deserialization.
 *
 * <p>The request carries the <em>normalised</em> JAR bytes and a SHA-256
 * content hash; {@code AnalysisRequest.check} must reject any request whose
 * declared hash does not match the SHA-256 of the bytes actually carried.
 *
 * <p>Serialization is exercised through {@link AtomicMarshalOutputStream} /
 * {@link AtomicMarshalInputStream}, which is the path that actually invokes the
 * {@code @AtomicSerial} {@code serialize(PutArg)} writer and the
 * {@code AnalysisRequest(GetArg)} reader (and hence {@code check}).
 *
 * @since 3.1.1
 */
public class AnalysisRequestTest {

    /**
     * Builds a minimal JAR and returns its normalised (Pack200 fixed point)
     * form, so that the request's serialize/deserialize pack-unpack round trip
     * is byte-for-byte identity.
     */
    private static byte[] normalisedJar() throws Exception {
        ByteArrayOutputStream rawBaos = new ByteArrayOutputStream();
        try (JarOutputStream jos = new JarOutputStream(rawBaos)) {
            JarEntry entry = new JarEntry("META-INF/MANIFEST.MF");
            jos.putNextEntry(entry);
            jos.write("Manifest-Version: 1.0\n".getBytes("UTF-8"));
            jos.closeEntry();
        }
        ByteArrayOutputStream normBaos = new ByteArrayOutputStream();
        Normalize.normalize(new ByteArrayInputStream(rawBaos.toByteArray()),
                normBaos, Normalize.Options.reproducible());
        return normBaos.toByteArray();
    }

    private static String sha256Hex(byte[] bytes) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(bytes);
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            sb.append(Character.forDigit((b >> 4) & 0xf, 16));
            sb.append(Character.forDigit(b & 0xf, 16));
        }
        return sb.toString();
    }

    /** Serializes {@code req} via the AtomicSerial output stream. */
    private static byte[] serialize(AnalysisRequest req) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new AtomicMarshalOutputStream(baos, null);
        oos.writeObject(req);
        oos.flush();
        oos.close();
        return baos.toByteArray();
    }

    /** Deserializes via the AtomicSerial input stream (invokes check). */
    private static AnalysisRequest deserialize(byte[] bytes) throws Exception {
        ObjectInputStream ois = AtomicMarshalInputStream.create(
                new ByteArrayInputStream(bytes), null, false, null, null, false);
        return (AnalysisRequest) ois.readObject();
    }

    /**
     * A request whose contentHash matches SHA-256 of its JAR bytes must
     * round-trip through AtomicSerial deserialization successfully.
     */
    @Test
    public void testMatchingHashDeserializes() throws Exception {
        byte[] jarBytes = normalisedJar();
        String hash = sha256Hex(jarBytes);
        AnalysisRequest req = new AnalysisRequest(
                jarBytes, hash, new Uri("https://example.com/test.jar"));

        AnalysisRequest result = deserialize(serialize(req));

        Assert.assertEquals(hash, result.getContentHash());
        Assert.assertArrayEquals(jarBytes, result.getJarBytes());
    }

    /**
     * A request whose contentHash does NOT match SHA-256 of its JAR bytes must
     * be rejected with an {@link InvalidObjectException} during deserialization.
     */
    @Test
    public void testMismatchedHashRejected() throws Exception {
        byte[] jarBytes = normalisedJar();
        // Deliberately wrong (but well-formed) 64-hex-char content hash.
        String tamperedHash =
                "0000000000000000000000000000000000000000000000000000000000000000";
        Assert.assertNotEquals(sha256Hex(jarBytes), tamperedHash);

        AnalysisRequest req = new AnalysisRequest(
                jarBytes, tamperedHash, new Uri("https://example.com/test.jar"));
        byte[] serialized = serialize(req);

        try {
            deserialize(serialized);
            Assert.fail("Expected InvalidObjectException for mismatched contentHash");
        } catch (InvalidObjectException expected) {
            Assert.assertTrue(
                    "message should mention contentHash mismatch",
                    expected.getMessage() != null
                            && expected.getMessage().contains("contentHash"));
        }
    }

    // =========================================================================
    // maxBfsDepth ceiling (Fix 4) — enforced at both the public constructor
    // and check()/deserialization, since a locally-constructed request never
    // calls check() at all.
    // =========================================================================

    /**
     * The public, locally-constructed-request constructor must reject a
     * {@code maxBfsDepth} above {@link AnalysisRequest#MAX_MAX_BFS_DEPTH}.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testConstructorRejectsMaxBfsDepthAboveCeiling() throws Exception {
        byte[] jarBytes = normalisedJar();
        String hash = sha256Hex(jarBytes);
        new AnalysisRequest(jarBytes, hash, null,
                AnalysisRequest.MAX_MAX_BFS_DEPTH + 1);
    }

    /**
     * A {@code maxBfsDepth} exactly at the ceiling must still be accepted —
     * the ceiling is inclusive.
     */
    @Test
    public void testConstructorAcceptsMaxBfsDepthAtCeiling() throws Exception {
        byte[] jarBytes = normalisedJar();
        String hash = sha256Hex(jarBytes);
        AnalysisRequest req = new AnalysisRequest(jarBytes, hash, null,
                AnalysisRequest.MAX_MAX_BFS_DEPTH);
        Assert.assertEquals(AnalysisRequest.MAX_MAX_BFS_DEPTH, req.getMaxBfsDepth());
    }

    /**
     * {@code check()} (the deserialization path) must independently reject a
     * {@code maxBfsDepth} above the ceiling — it must not rely solely on the
     * public constructor's guard.  Since the public constructor now also
     * enforces the ceiling, an out-of-range value cannot be produced through
     * the normal API; this test uses reflection to set the field directly
     * after construction, standing in for an attacker who controls the wire
     * bytes directly rather than going through this JVM's constructor (the
     * same threat model {@link #testMismatchedHashRejected} exercises for
     * the content-hash binding).
     */
    @Test
    public void testCheckRejectsMaxBfsDepthAboveCeilingOnDeserialize() throws Exception {
        byte[] jarBytes = normalisedJar();
        String hash = sha256Hex(jarBytes);
        AnalysisRequest req = new AnalysisRequest(
                jarBytes, hash, new Uri("https://example.com/test.jar"),
                AnalysisRequest.DEFAULT_MAX_BFS_DEPTH);
        forceMaxBfsDepth(req, AnalysisRequest.MAX_MAX_BFS_DEPTH + 1);

        byte[] serialized = serialize(req);

        try {
            deserialize(serialized);
            Assert.fail("Expected InvalidObjectException for maxBfsDepth above ceiling");
        } catch (InvalidObjectException expected) {
            Assert.assertTrue(
                    "message should mention maxBfsDepth",
                    expected.getMessage() != null
                            && expected.getMessage().contains("maxBfsDepth"));
        }
    }

    /** Reflectively overwrites the private final {@code maxBfsDepth} field,
     * bypassing the constructor's own ceiling guard — simulates an
     * attacker-controlled value arriving on the wire rather than a value
     * this JVM's constructor validated. */
    private static void forceMaxBfsDepth(AnalysisRequest req, int value) throws Exception {
        java.lang.reflect.Field f = AnalysisRequest.class.getDeclaredField("maxBfsDepth");
        f.setAccessible(true);
        f.setInt(req, value);
    }
}
