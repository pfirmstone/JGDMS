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

import net.jini.io.MarshalledInstance;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

/**
 * A thin public bridge over the protected
 * {@link MarshalledInstance#MarshalledInstance(Object, Collection, net.jini.io.MarshalFactory)}
 * constructor that produces a {@link MarshalledInstance} encoded in the DER wire format
 * (JGDMS-STD-006 S7.8 / STD-008 sec.13, B+C hybrid).
 *
 * <h2>Encoding</h2>
 * <p>
 * Construction calls the protected 3-arg {@code MarshalledInstance(Object, Collection,
 * MarshalFactory)} constructor with a {@link DerMarshalFactory}. The factory writes
 * <em>only the payload bytes</em> to the parent's {@code payloadBytes} field (the schema
 * travels as a separate first-class field via {@link DerMarshalInstanceOutput#getSchemaBytes()},
 * {@link DerMarshalInstanceOutput#getSchemaDigest()}, and
 * {@link DerMarshalInstanceOutput#getPayloadFormat()}).
 *
 * <h2>Decoding -- via ServiceLoader, no subclass override needed</h2>
 * <p>
 * This class does NOT override {@code getMarshalFactory()}. The base
 * {@link MarshalledInstance#getMarshalFactory()} reads the {@code payloadFormat} field
 * (set to {@link MarshalledInstanceRecord#PAYLOAD_FORMAT}) and calls
 * {@code factoryForFormat(payloadFormat)}, which discovers {@link DerMarshalFactoryProvider}
 * via {@link java.util.ServiceLoader} and returns a {@link DerMarshalFactory}.
 * The full decode flow is:
 * <ol>
 *   <li>{@code get()} -> {@code getMarshalFactory()} (base implementation).</li>
 *   <li>{@code factoryForFormat("JGDMS-STD-006/DER")} -> ServiceLoader ->
 *       {@link DerMarshalFactoryProvider} -> {@link DerMarshalFactory}.</li>
 *   <li>{@code DerMarshalFactory.createMarshalInput(..., schemaBytes, ...)} (9-arg form)
 *       -> {@link DerMarshalInstanceInput}.</li>
 *   <li>{@code DerMarshalInstanceInput.readObject(type)} -> decode driven by
 *       {@code schemaBytes} via {@link MarshalledInstanceCodec}.</li>
 * </ol>
 *
 * <p>This means a base {@link MarshalledInstance} carrying DER state
 * (e.g. received across a version boundary where only the base class was deserialized)
 * is decodable on any receiver that has {@code jgdms-der} on its classpath, without
 * requiring this subclass at the receiving end. The ServiceLoader dispatch by
 * {@code payloadFormat} IS the proof that no subclass override is needed.
 *
 * <h2>STD-008 S13 "B+C" hybrid</h2>
 * <p>
 * {@code payloadBytes} carries only the DER payload (NOT the full
 * {@link MarshalledInstanceRecord} blob). The schema chain travels as first-class
 * {@code MarshalledInstance} fields ({@code schemaBytes}, {@code schemaDigest},
 * {@code payloadFormat}). Reconstruction on decode:
 * {@link DerMarshalInstanceInput} assembles a transient
 * {@link MarshalledInstanceRecord} from those two pieces.
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
     *             null {@code payloadBytes}, consistent with the parent's contract)
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
     * DER payload bytes as {@code payloadBytes} with the schema as separate first-class
     * fields ({@code schemaBytes}, {@code schemaDigest}, {@code payloadFormat}).
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
    // NO getMarshalFactory() override -- decoding uses ServiceLoader dispatch
    // -------------------------------------------------------------------------
    //
    // The base MarshalledInstance.getMarshalFactory() reads this instance's
    // payloadFormat field ("JGDMS-STD-006/DER") and calls factoryForFormat(format),
    // which uses ServiceLoader to discover DerMarshalFactoryProvider -> DerMarshalFactory.
    //
    // This is the ServiceLoader-dispatch proof: get() exercises the base getMarshalFactory()
    // -> factoryForFormat(payloadFormat) -> ServiceLoader -> DerMarshalFactoryProvider
    // -> DerMarshalFactory -> 9-arg createMarshalInput(..., schemaBytes, ...)
    // -> DerMarshalInstanceInput.readObject(type) -> decode driven by schemaBytes.
    //
    // No override is needed here. The same flow applies to a base MarshalledInstance
    // carrying DER state received across a version boundary.
}
