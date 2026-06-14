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

import au.net.zeus.jgdms.der.DerException;
import au.net.zeus.jgdms.der.object.ObjectCodec;
import au.net.zeus.jgdms.der.schema.SchemaChain;
import au.net.zeus.jgdms.der.schema.SchemaGenerator;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.util.Arrays;
import java.util.Objects;

/**
 * Entry point for Phase 5.2: decode a {@link MarshalledInstanceRecord} using
 * the embedded (at-marshal-time) schema, not the receiver's current
 * {@code serialForm()}.
 *
 * <h2>§7.8 normative rule — embedded schema is authoritative</h2>
 * <p>
 * The {@code schemaBytes} embedded in a {@code MarshalledInstanceRecord} are the
 * authoritative, permanent schema for decoding {@code payloadBytes}. The receiver
 * <b>MUST</b> use this embedded schema to populate {@code GetArg}, not its own
 * current {@code serialForm()}. The receiver's {@code serialForm()} describes what
 * its code can <em>process</em>; the embedded schema describes what the data
 * <em>contains</em>. The {@code @AtomicSerial} {@code GetArg} layer bridges any
 * divergence at runtime.
 *
 * <h2>Three decoding cases (§3.9)</h2>
 * <p>
 * In all three cases the payload is decoded using the embedded chain (never the
 * receiver's current {@code serialForm()}):
 * <dl>
 *   <dt><b>Case (a)</b> — schema digest matches code.</dt>
 *   <dd>{@code schemaDigest} in the record equals the digest computed from the
 *       receiver's own current {@code serialForm()} chain. Schema and code are
 *       identical. This is the primary (fast) path.</dd>
 *
 *   <dt><b>Case (b)</b> — embedded schema has MORE fields than the receiver knows.</dt>
 *   <dd>The sender was newer than the receiver. The embedded schema carries fields
 *       that the receiver's constructor never requests via {@code arg.get()}. Those
 *       fields are decoded and stored in {@code GetArg} but silently discarded after
 *       construction. No error occurs. Backward-compatibility: new data, old code.</dd>
 *
 *   <dt><b>Case (c)</b> — embedded schema has FEWER fields than the receiver expects.</dt>
 *   <dd>The receiver was updated after the data was serialised. The receiver's
 *       constructor calls {@code arg.get(name, default)} for fields that are absent
 *       from the embedded schema; the default is returned. No error occurs.
 *       Forward-compatibility: old data, new code.</dd>
 * </dl>
 *
 * <h2>Divergence detection result</h2>
 * <p>
 * {@link #decodeMarshalledInstance(MarshalledInstanceRecord, Class)} returns a
 * {@link Result} that exposes the decoded object together with the detected
 * {@link SchemaCase} ({@code A}, {@code B}, or {@code C}), useful for testing and
 * diagnostics.
 *
 * <h2>GetArg bridge</h2>
 * <p>
 * The decoding always delegates to
 * {@link ObjectCodec#decodeHierarchy(Class, SchemaChain.Result, byte[])} with the
 * <em>embedded chain</em> as the second argument. This means the {@link
 * au.net.zeus.jgdms.der.getarg.DerFieldStore} per class is populated from the
 * embedded schema's field list — never from the receiver's {@code serialForm()}.
 * The receiver's constructor then issues {@code arg.get(name, default)} calls; each
 * either finds a value in the store (data was present in the payload) or returns the
 * declared default (data was absent from the embedded schema). The store silently
 * holds any extra fields the receiver's code did not request.
 */
public final class MarshalledInstanceCodec {

    private MarshalledInstanceCodec() {
        throw new AssertionError("no instances");
    }

    // =========================================================================
    // Schema-divergence case enumeration
    // =========================================================================

    /**
     * The three decoding cases from JGDMS-STD-006 §3.9.
     * "The schema" in all three cases is the MarshalledInstance embedded schema.
     */
    public enum SchemaCase {
        /**
         * (a) The embedded schema digest matches the digest computed from the
         * receiver's own current {@code serialForm()} chain. Schema and code are
         * identical. Primary (fast) path.
         */
        A_MATCH,

        /**
         * (b) The embedded schema digest does NOT match the receiver's own digest.
         * Schema and code have diverged. The {@code GetArg} layer absorbs the
         * divergence (extra fields in the embedded store are silently ignored;
         * missing fields from the receiver's perspective return defaults). This
         * covers both "sender newer" and "sender older" — both produce a mismatch.
         *
         * <p>Per §3.9, "case (b)" labels the "sender was newer" sub-case and
         * "case (c)" labels "receiver was newer"; both are detected as a
         * non-matching digest here. {@link #B_OR_C_MISMATCH} covers both.
         */
        B_OR_C_MISMATCH
    }

    // =========================================================================
    // Result record
    // =========================================================================

    /**
     * Result of {@link #decodeMarshalledInstance}, containing the decoded object
     * and the detected {@link SchemaCase}.
     *
     * @param <T>         the decoded type
     * @param object      the constructed instance
     * @param schemaCase  whether the embedded and receiver schemas matched
     */
    public record Result<T>(T object, SchemaCase schemaCase) {}

    // =========================================================================
    // Public entry point
    // =========================================================================

    /**
     * Decodes a {@link MarshalledInstanceRecord} using the embedded (at-marshal-time)
     * schema chain, not the receiver's current {@code serialForm()}.
     *
     * <h2>Steps</h2>
     * <ol>
     *   <li>Parse {@code rec.schemaBytes()} → embedded chain (leaf-first).</li>
     *   <li>Compute the receiver's own current digest via
     *       {@link SchemaGenerator#generateChain(Class)}.{@link SchemaChain.Result#leafDigest()}.
     *       Compare to {@code rec.schemaDigest()}.
     *       Equal → case (a). Unequal → case (b)/(c).</li>
     *   <li><b>Always</b> decode the payload using the <em>embedded chain</em> via
     *       {@link ObjectCodec#decodeHierarchy(Class, SchemaChain.Result, byte[])}.
     *       The embedded schema builds the per-class {@link
     *       au.net.zeus.jgdms.der.getarg.DerFieldStore}s. The receiver's
     *       {@code (GetArg)} constructor then calls {@code arg.get()} for the fields
     *       it knows; extra embedded-only fields sit in the store unrequested; absent
     *       fields return their {@code get(name, default)} defaults.</li>
     *   <li>Return the constructed object together with the detected
     *       {@link SchemaCase}.</li>
     * </ol>
     *
     * <p>The receiver's {@code serialForm()} is <b>never</b> used to build
     * {@code DerFieldStore}s — it is only queried to compute the digest for
     * schema-match detection (step 2).
     *
     * @param <T>            the expected return type
     * @param rec            the {@code MarshalledInstanceRecord} to decode
     * @param receiverClass  the receiver's class; must be assignable-to by the
     *                       chain's leaf class; used for assignability check and
     *                       digest computation
     * @return a {@link Result} with the constructed object and detected schema case
     * @throws DerException           if the DER encoding is malformed
     * @throws InvalidObjectException if the class's {@code check(GetArg)} fails
     * @throws IOException            if construction fails with an {@link IOException}
     * @throws ClassNotFoundException if a class named in the schema cannot be loaded
     * @throws NullPointerException   if any argument is null
     */
    public static <T> Result<T> decodeMarshalledInstance(
            MarshalledInstanceRecord rec,
            Class<T> receiverClass)
            throws DerException, IOException, ClassNotFoundException {

        Objects.requireNonNull(rec,           "rec");
        Objects.requireNonNull(receiverClass, "receiverClass");

        // Step 1: parse the embedded schema chain
        SchemaChain.Result embeddedChain = rec.decodeSchemaChainAsResult();

        // Step 2: compare embedded digest to receiver's own current digest
        SchemaCase schemaCase;
        try {
            SchemaChain.Result receiverChain = SchemaGenerator.generateChain(receiverClass);
            byte[] receiverDigest  = receiverChain.leafDigest();
            byte[] embeddedDigest  = rec.schemaDigest();
            schemaCase = Arrays.equals(receiverDigest, embeddedDigest)
                    ? SchemaCase.A_MATCH
                    : SchemaCase.B_OR_C_MISMATCH;
        } catch (DerException e) {
            // If the receiver class has no @AtomicSerial serialForm() we cannot compute
            // its current digest — treat as a mismatch (case b/c) and proceed with the
            // embedded schema, which is always correct.
            schemaCase = SchemaCase.B_OR_C_MISMATCH;
        }

        // Step 3: ALWAYS decode using the embedded chain (§7.8 normative).
        // The embedded schema builds the DerFieldStores; the receiver's serialForm()
        // is never consulted for decoding.
        T object = ObjectCodec.decodeHierarchy(
                receiverClass,
                embeddedChain,
                rec.payloadBytes());

        return new Result<>(object, schemaCase);
    }
}
