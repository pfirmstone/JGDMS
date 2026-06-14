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
 * DER implementation of {@link MarshalFactory}.
 *
 * <p>Produces {@link DerMarshalInstanceOutput} for writing and
 * {@link DerMarshalInstanceInput} for reading. Both are stateless relative to
 * the factory; this class itself is stateless and may be instantiated freely.
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
 * <h2>STD-008 S13.6 "Option A" -- non-invasive spike</h2>
 * <p>
 * No platform files are modified. {@link DerMarshalledInstance} passes
 * {@code new DerMarshalFactory()} to the protected 3-arg
 * {@code MarshalledInstance} constructor, and overrides
 * {@code getMarshalFactory()} to return the same factory so that
 * {@code get()} also uses the DER path.
 */
public final class DerMarshalFactory implements MarshalFactory {

    /** No-arg constructor; this factory is stateless. */
    public DerMarshalFactory() {}

    /**
     * Creates a {@link DerMarshalInstanceOutput} that writes the DER-encoded
     * {@link MarshalledInstanceRecord} to {@code objOut}.
     *
     * @param objOut   the stream that will receive the encoded record bytes
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
     * Creates a {@link DerMarshalInstanceInput} that reads and decodes the
     * {@link MarshalledInstanceRecord} from {@code objIn}.
     *
     * @param objIn                    the stream containing the encoded record bytes
     * @param locIn                    ignored (DER has no codebase annotations)
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
                                                   ClassLoader  defaultLoader,
                                                   boolean      verifyCodebaseIntegrity,
                                                   ClassLoader  verifierLoader,
                                                   Collection   context)
            throws IOException {
        return new DerMarshalInstanceInput(objIn, context);
    }
}
