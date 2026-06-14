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
import net.jini.io.MarshalledInstance;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

/**
 * A {@link MarshalledInstance} subclass that uses the DER wire format
 * (JGDMS-STD-006 S7.8) to encode and decode its contained object.
 *
 * <h2>Encoding</h2>
 * <p>
 * Construction calls the protected 3-arg {@code MarshalledInstance(Object, Collection,
 * MarshalFactory)} constructor with a {@link DerMarshalFactory}. The factory writes
 * the full {@link MarshalledInstanceRecord} -- payload + embedded schema chain --
 * into the parent's {@code objBytes} field. {@code locBytes} is {@code null} because
 * DER has no codebase annotations (S8).
 *
 * <h2>Decoding</h2>
 * <p>
 * {@link #getMarshalFactory()} returns a fresh {@link DerMarshalFactory}, so
 * all of the parent's {@code get(...)} overloads use the DER decode path:
 * <ol>
 *   <li>The parent wraps {@code objBytes} in a {@code ByteArrayInputStream}.</li>
 *   <li>It calls {@link DerMarshalFactory#createMarshalInput}, which produces a
 *       {@link DerMarshalInstanceInput} that eagerly reads all bytes.</li>
 *   <li>The parent detects {@code in instanceof AtomicObjectInput} and calls
 *       {@code readObject(type)} -> {@link MarshalledInstanceCodec#decodeMarshalledInstance}.</li>
 * </ol>
 *
 * <h2>Serialization of this subclass</h2>
 * <p>
 * {@code DerMarshalledInstance} is not itself {@code @AtomicSerial}; it inherits the
 * parent's Java-serialization serial form ({@code objBytes}/{@code locBytes}/{@code hash}).
 * When this object is serialized and deserialized as the base {@code MarshalledInstance}
 * class (e.g. across a version boundary), the {@code get()} call on the reconstructed
 * base instance would use the default JOSS factory, which cannot decode the DER bytes.
 * Format-detection and ServiceLoader dispatch (the STD-008 S13 hybrid follow-on) would
 * address this; it is out of scope for this non-invasive spike.
 *
 * <h2>STD-008 S13.6 "Option A" -- non-invasive spike</h2>
 * <p>
 * Zero platform files are modified. This class, together with {@link DerMarshalFactory},
 * {@link DerMarshalInstanceOutput}, and {@link DerMarshalInstanceInput}, proves the
 * DER codec plugs into the existing {@code MarshalledInstance} seam end-to-end.
 */
public final class DerMarshalledInstance extends MarshalledInstance {

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Creates a {@code DerMarshalledInstance} containing the DER-encoded form
     * of {@code obj}, with an empty context collection.
     *
     * @param obj  the object to marshal; may be {@code null} (null is stored as
     *             null {@code objBytes}, consistent with the parent's contract)
     * @throws IOException if DER encoding of {@code obj} fails
     */
    public DerMarshalledInstance(Object obj) throws IOException {
        this(obj, Collections.emptySet());
    }

    /**
     * Creates a {@code DerMarshalledInstance} containing the DER-encoded form
     * of {@code obj}, with the given context collection.
     *
     * <p>Calls the protected 3-arg {@code MarshalledInstance} constructor, which
     * invokes {@link DerMarshalFactory#createMarshalOutput}, then
     * {@link DerMarshalInstanceOutput#writeObject(Object)}, and stores the resulting
     * DER bytes as {@code objBytes}.
     *
     * @param obj      the object to marshal; may be {@code null}
     * @param context  the serialization context collection; must not be {@code null}
     * @throws IOException          if DER encoding of {@code obj} fails
     * @throws NullPointerException if {@code context} is {@code null}
     */
    public DerMarshalledInstance(Object obj, Collection context) throws IOException {
        super(obj, context, new DerMarshalFactory());
    }

    // -------------------------------------------------------------------------
    // getMarshalFactory -- routes get() through the DER decode path
    // -------------------------------------------------------------------------

    /**
     * Returns a {@link DerMarshalFactory} so that all of the parent's
     * {@code get(...)} overloads decode {@code objBytes} using the DER codec.
     *
     * @return a new {@link DerMarshalFactory}
     */
    @Override
    protected MarshalFactory getMarshalFactory() {
        return new DerMarshalFactory();
    }
}
