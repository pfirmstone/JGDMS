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

import net.jini.io.MarshalFactory;
import net.jini.io.MarshalInstanceInput;
import net.jini.io.MarshalInstanceOutput;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;

/**
 * DER implementation of {@link MarshalFactory} (JGDMS-STD-008 sec.13.3, B+C hybrid).
 *
 * <p>Produces {@link DerMarshalInstanceOutput} for writing and
 * {@link DerMarshalInstanceInput} for reading. Both are stateless relative to
 * the factory; this class itself is stateless and may be instantiated freely.
 *
 * <h2>Schema-aware decode (9-arg overload)</h2>
 * <p>
 * The primary decode path is the 9-arg widened {@link #createMarshalInput(InputStream,
 * InputStream, byte[], byte[], String, ClassLoader, boolean, ClassLoader, Collection)}.
 * This override receives the embedded {@code schemaBytes} that
 * {@link net.jini.io.MarshalledInstance} carries as a first-class field (promoted there
 * by {@link DerMarshalInstanceOutput#getSchemaBytes()} at marshal time) and passes them
 * to {@link DerMarshalInstanceInput}. The 6-arg abstract form is not usable by this codec
 * because it does not receive the schema; it throws {@link UnsupportedOperationException}.
 *
 * <h2>Unused parameters</h2>
 * <ul>
 *   <li>{@code locOut} / {@code locIn} -- ignored: DER has no codebase annotations
 *       (STD-006 S8). The embedded schema provides data-independence.</li>
 *   <li>{@code defaultLoader}, {@code verifyCodebaseIntegrity}, {@code verifierLoader} --
 *       ignored: DER carries the schema, not codebase URLs; class loading is implicit
 *       (the receiver's own classloader resolves classes by name from the embedded
 *       schema chain).</li>
 * </ul>
 *
 * <h2>ServiceLoader dispatch</h2>
 * <p>
 * {@link DerMarshalFactoryProvider} registers this factory as the handler for
 * {@link MarshalledInstanceRecord#PAYLOAD_FORMAT} ({@code "JGDMS-STD-006/DER"}) via
 * the {@link net.jini.io.MarshalFactoryProvider} ServiceLoader SPI. The base
 * {@link net.jini.io.MarshalledInstance#getMarshalFactory()} resolves this factory
 * through that mechanism; no subclass override of {@code getMarshalFactory()} is needed.
 */
public final class DerMarshalFactory implements MarshalFactory {

    /** No-arg constructor; this factory is stateless. */
    public DerMarshalFactory() {}

    /**
     * Creates a {@link DerMarshalInstanceOutput} that writes the DER-encoded
     * payload bytes to {@code objOut} and stashes the schema for retrieval via
     * the widened {@link MarshalInstanceOutput} methods.
     *
     * @param objOut   the stream that will receive the encoded payload bytes
     * @param locOut   ignored (DER has no codebase annotations)
     * @param context  the stream context collection; forwarded to the output
     * @return a new {@link DerMarshalInstanceOutput}
     * @throws IOException never thrown by this implementation; declared for interface
     */
    @Override
    public MarshalInstanceOutput createMarshalOutput(OutputStream objOut,
                                                     OutputStream locOut,
                                                     Collection   context)
            throws IOException {
        return new DerMarshalInstanceOutput(objOut, context);
    }

    /**
     * Schema-aware decode -- primary path called by
     * {@link net.jini.io.MarshalledInstance#get} (STD-008 sec.13.3).
     *
     * <p>Creates a {@link DerMarshalInstanceInput} using the {@code schemaBytes}
     * first-class field from {@code MarshalledInstance} to drive decoding. The
     * {@code objIn} stream carries the payload bytes written by
     * {@link DerMarshalInstanceOutput#writeObject}.
     *
     * @param objIn                    the stream containing the payload bytes
     * @param locIn                    ignored (DER has no codebase annotations)
     * @param schemaBytes              the embedded schema from the first-class
     *                                 {@code MarshalledInstance.schemaBytes} field
     * @param schemaDigest             ignored (re-derived from the schema chain)
     * @param payloadFormat            ignored (routing already done by MarshalledInstance)
     * @param defaultLoader            ignored (DER resolves classes via embedded schema)
     * @param verifyCodebaseIntegrity  ignored (DER has no codebase URLs to verify)
     * @param verifierLoader           ignored
     * @param context                  the stream context collection; forwarded to the input
     * @return a new {@link DerMarshalInstanceInput}
     * @throws IOException if reading from {@code objIn} fails during construction
     */
    @Override
    public MarshalInstanceInput createMarshalInput(InputStream  objIn,
                                                   InputStream  locIn,
                                                   byte[]       schemaBytes,
                                                   byte[]       schemaDigest,
                                                   String       payloadFormat,
                                                   ClassLoader  defaultLoader,
                                                   boolean      verifyCodebaseIntegrity,
                                                   ClassLoader  verifierLoader,
                                                   Collection   context)
            throws IOException {
        return new DerMarshalInstanceInput(objIn, schemaBytes, context);
    }

    /**
     * The 6-arg form does NOT receive the schema and therefore cannot be used by
     * this codec (the schema is mandatory for DER decoding per STD-006 sec.7.8).
     * The base {@link net.jini.io.MarshalledInstance#get} always calls the 9-arg
     * widened form for non-JOSS payloads; this method should never be reached
     * in normal operation.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    public MarshalInstanceInput createMarshalInput(InputStream  objIn,
                                                   InputStream  locIn,
                                                   ClassLoader  defaultLoader,
                                                   boolean      verifyCodebaseIntegrity,
                                                   ClassLoader  verifierLoader,
                                                   Collection   context)
            throws IOException {
        throw new UnsupportedOperationException(
                "DER requires the schema-aware createMarshalInput (9-arg form); "
                + "the 6-arg form does not supply schemaBytes");
    }
}
