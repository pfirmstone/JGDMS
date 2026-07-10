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

package net.jini.io;

/**
 * Service-provider interface (JGDMS-STD-008 sec.13.2) that maps a
 * {@code payloadFormat} identifier to the {@link MarshalFactory} able to decode it.
 *
 * <p>{@link MarshalledInstance#get} reads its own {@code payloadFormat} field, looks
 * up the matching provider via {@link java.util.ServiceLoader}, and decodes with the
 * returned factory. This realises the historical {@code // TODO: ServiceProvider for
 * MarshalFactory} note: a {@code MarshalledInstance} produced by an alternative codec
 * (e.g. JGDMS-STD-006/ATOMIC-DER) is decodable on any receiver that has the corresponding
 * provider on its classpath, with no subclass of {@code MarshalledInstance} and no
 * dynamic codebase loading required. The codec module registers its implementation in
 * {@code META-INF/services/net.jini.io.MarshalFactoryProvider}.
 *
 * <p>The legacy Java-Object-Serialization (JOSS) factory ({@link MarshalledInstance#FORMAT_JOSS})
 * is the built-in fallback and is NOT discovered through this SPI.
 *
 * <p>Implementations MUST have a public no-argument constructor and SHOULD be
 * stateless; {@link #marshalFactory()} may be invoked concurrently and its result
 * cached by format.
 */
public interface MarshalFactoryProvider {

    /**
     * The self-describing payload-format identifier this provider handles
     * (e.g. {@code "JGDMS-STD-006/ATOMIC-DER"}). Must be non-{@code null} and must not equal
     * {@link MarshalledInstance#FORMAT_JOSS} (which is the reserved built-in default).
     *
     * @return the format identifier; never {@code null}.
     */
    String payloadFormat();

    /**
     * A {@link MarshalFactory} that decodes payloads of {@link #payloadFormat()}.
     *
     * @return the factory; never {@code null}.
     */
    MarshalFactory marshalFactory();
}
