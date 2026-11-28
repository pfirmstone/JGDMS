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
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.river.api.io.AtomicMarshalInputStream;
import org.apache.river.api.io.AtomicMarshalOutputStream;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * The {@code JarAnalysisReport(GetArg)} reader must refuse a report whose
 * {@code classNames[i]} (the signed per-class name) differs from
 * {@code classResults[i].getClassName()} (RL-JGDMS-0197).  The Map constructor
 * cannot build such a report, so the stream is produced from a consistent report
 * and one {@code classNames} entry is rewritten in place to a same-length name.
 * It must also refuse a report whose {@code classNames} repeats a name.
 *
 * <p>Serialization goes through {@link AtomicMarshalOutputStream} /
 * {@link AtomicMarshalInputStream}, as in {@code AnalysisRequestTest}.
 * Runtime tier: fast (milliseconds).
 */
public class JarAnalysisReportDeserializationTest {

    private static final String SIGNED_NAME   = "com/example/Foo";
    private static final String FORGED_NAME   = "com/example/Bar";
    private static final byte[] PLACEHOLDER   = { 0 };

    private static JarAnalysisReport report() {
        // Distinct String instances so the stream carries the name twice
        // instead of a back-reference from the result to the key.
        String key = new String(SIGNED_NAME);
        String resultName = new String(SIGNED_NAME);
        Map<String, ClassAnalysisResult> m = new LinkedHashMap<String, ClassAnalysisResult>();
        m.put(key, new ClassAnalysisResult(resultName, ClinitVerdict.CLEAN,
                AtomicSerialVerdict.COMPLIANT, ConstrainableProxyVerdict.NA,
                Collections.<String>emptyList(), Collections.<String>emptyList()));
        return new JarAnalysisReport("abc123", m, PLACEHOLDER,
                new String[0], new String[0]);
    }

    /** Two classes, {@link #SIGNED_NAME} then {@link #FORGED_NAME}; every name
     *  is a distinct String instance, so the stream carries each one twice. */
    private static JarAnalysisReport twoClassReport() {
        Map<String, ClassAnalysisResult> m = new LinkedHashMap<String, ClassAnalysisResult>();
        for (String name : new String[] { SIGNED_NAME, FORGED_NAME }) {
            m.put(new String(name), new ClassAnalysisResult(new String(name),
                    ClinitVerdict.CLEAN, AtomicSerialVerdict.COMPLIANT,
                    ConstrainableProxyVerdict.NA,
                    Collections.<String>emptyList(), Collections.<String>emptyList()));
        }
        return new JarAnalysisReport("abc123", m, PLACEHOLDER,
                new String[0], new String[0]);
    }

    private static byte[] serialize(JarAnalysisReport r) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new AtomicMarshalOutputStream(baos, null);
        oos.writeObject(r);
        oos.flush();
        oos.close();
        return baos.toByteArray();
    }

    private static JarAnalysisReport deserialize(byte[] bytes) throws Exception {
        ObjectInputStream ois = AtomicMarshalInputStream.create(
                new ByteArrayInputStream(bytes), null, false, null, null, false);
        return (JarAnalysisReport) ois.readObject();
    }

    /** Index of the {@code occurrence}th (0-based) match of {@code pattern}, or -1. */
    private static int indexOf(byte[] data, byte[] pattern, int occurrence) {
        int seen = 0;
        for (int i = 0; i + pattern.length <= data.length; i++) {
            int j = 0;
            while (j < pattern.length && data[i + j] == pattern[j]) j++;
            if (j == pattern.length && seen++ == occurrence) return i;
        }
        return -1;
    }

    @Test
    public void consistentReport_roundTrips() throws Exception {
        JarAnalysisReport back = deserialize(serialize(report()));
        assertEquals(1, back.getResults().size());
        ClassAnalysisResult cr = back.getResults().get(SIGNED_NAME);
        assertNotNull(cr);
        assertEquals(SIGNED_NAME, cr.getClassName());
    }

    @Test
    public void classNameDifferentFromResultClassName_isRejectedOnDeserialization()
            throws Exception {
        byte[] stream = serialize(report());
        byte[] signed = SIGNED_NAME.getBytes(StandardCharsets.UTF_8);
        byte[] forged = FORGED_NAME.getBytes(StandardCharsets.UTF_8);
        assertEquals(signed.length, forged.length);
        // The stream carries the name twice: once in classNames, once in the
        // ClassAnalysisResult.  Rewrite exactly one of the two.
        assertTrue("name must occur in classNames and in the result",
                indexOf(stream, signed, 1) >= 0);
        System.arraycopy(forged, 0, stream, indexOf(stream, signed, 0), forged.length);
        try {
            deserialize(stream);
            fail("classNames[0] differs from classResults[0].className: must be rejected");
        } catch (InvalidObjectException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("className"));
        }
    }

    @Test
    public void distinctClassNames_roundTrip() throws Exception {
        JarAnalysisReport back = deserialize(serialize(twoClassReport()));
        assertEquals(2, back.getResults().size());
        assertNotNull(back.getResults().get(SIGNED_NAME));
        assertNotNull(back.getResults().get(FORGED_NAME));
    }

    /**
     * The signature covers the {@code classNames} array but consumers read the
     * results map, which keeps only the last of a repeated name.  Every
     * occurrence of the second name is rewritten to the first, so
     * {@code classNames} and the per-class names still agree with each other
     * and only the duplicate itself is wrong.
     */
    @Test
    public void duplicateClassNames_areRejectedOnDeserialization() throws Exception {
        byte[] stream = serialize(twoClassReport());
        byte[] first = SIGNED_NAME.getBytes(StandardCharsets.UTF_8);
        byte[] second = FORGED_NAME.getBytes(StandardCharsets.UTF_8);
        assertEquals(first.length, second.length);
        int rewritten = 0;
        for (int at = indexOf(stream, second, 0); at >= 0; at = indexOf(stream, second, 0)) {
            System.arraycopy(first, 0, stream, at, first.length);
            rewritten++;
        }
        assertEquals("the second name occurs in classNames and in its result", 2, rewritten);
        try {
            deserialize(stream);
            fail("classNames holds the same name twice: must be rejected");
        } catch (InvalidObjectException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("duplicate"));
        }
    }
}
