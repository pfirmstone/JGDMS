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

import au.net.zeus.jgdms.der.object.ObjectCodec;
import au.net.zeus.jgdms.der.schema.SchemaChain;
import au.net.zeus.jgdms.der.schema.SchemaGenerator;
import au.net.zeus.jgdms.der.marshal.MarshalledInstanceRecord;
import au.net.zeus.jgdms.showcase.model.CalibratedReading;
import org.apache.river.api.io.AtomicMarshalOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HexFormat;

/**
 * Small helpers shared by the showcase demonstrations. Nothing here is part of the
 * wire format itself — these just call the public library entry points and format
 * the results for a human to read.
 *
 * <p>The library method names used:
 * <ul>
 *   <li>{@code SchemaGenerator.generateChain(Class)} — works out the shape
 *       description for a value type and its whole family tree.</li>
 *   <li>{@code ObjectCodec.encodeHierarchy(object, chain)} — writes the object's
 *       data in the canonical (one-and-only) byte form.</li>
 *   <li>{@code MarshalledInstanceRecord.fromChain(...).encode()} — one complete,
 *       self-describing record: the data plus the shape description that lets a
 *       reader make sense of it.</li>
 * </ul>
 */
public final class ShowcaseSupport {

    private ShowcaseSupport() {}

    /** The canonical byte form of one object's data (no surrounding framing). */
    public static byte[] canonicalBytes(Object o) throws Exception {
        SchemaChain.Result chain = SchemaGenerator.generateChain(o.getClass());
        return ObjectCodec.encodeHierarchy(o, chain);
    }

    /**
     * One complete self-describing record: the object's data together with the shape
     * description a reader needs. This is what you would send if you sent the shape
     * description every single time (no sharing).
     */
    public static byte[] selfDescribingRecord(Object o) throws Exception {
        SchemaChain.Result chain = SchemaGenerator.generateChain(o.getClass());
        byte[] payload = ObjectCodec.encodeHierarchy(o, chain);
        return MarshalledInstanceRecord.fromChain(chain, payload).encode();
    }

    /** The size in bytes of just the shape description (the family-tree description). */
    public static int shapeDescriptionBytes(Class<?> type) throws Exception {
        SchemaChain.Result chain = SchemaGenerator.generateChain(type);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        chain.chain().forEach(rec -> buf.writeBytes(rec.encode()));
        return buf.size();
    }

    /** Java's built-in object serialization, using the project's compatible stream. */
    public static byte[] javaBuiltInSerialization(Object o) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        ObjectOutputStream oos = new AtomicMarshalOutputStream(b, Collections.emptyList());
        oos.writeObject(o);
        oos.flush();
        return b.toByteArray();
    }

    public static String sha256Hex(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }

    /** A short, human-readable hex preview of a byte array. */
    public static String hexPreview(byte[] data, int maxBytes) {
        HexFormat hf = HexFormat.of().withUpperCase();
        int n = Math.min(maxBytes, data.length);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i > 0 && i % 2 == 0) sb.append(' ');
            sb.append(hf.toHexDigits(data[i]));
        }
        if (data.length > n) sb.append(" ...(").append(data.length).append(" bytes total)");
        return sb.toString();
    }

    /**
     * A deterministic representation of one reading as JSON text, with every field
     * name spelled out — the way a verbose text format such as JSON sends its data.
     * Used only as a size comparison point; it repeats the field names on every object.
     */
    public static byte[] asJsonText(CalibratedReading r) {
        String json = "{"
                + "\"sequenceNumber\":" + r.getSequenceNumber() + ","
                + "\"originatingStationName\":\"" + r.getOriginatingStationName() + "\","
                + "\"measuredQuantityName\":\"" + r.getMeasuredQuantityName() + "\","
                + "\"unitOfMeasurement\":\"" + r.getUnitOfMeasurement() + "\","
                + "\"calibratedValue\":" + r.getCalibratedValue() + ","
                + "\"calibrationCertificateReference\":\"" + r.getCalibrationCertificateReference() + "\""
                + "}";
        return json.getBytes(StandardCharsets.UTF_8);
    }

    /** A line of a text bar chart, scaled so the largest value fills {@code width}. */
    public static String bar(long value, long max, int width) {
        int filled = max == 0 ? 0 : (int) Math.round((double) value / max * width);
        return "#".repeat(Math.max(0, filled));
    }
}
