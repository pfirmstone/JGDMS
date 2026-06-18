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
import net.jini.io.MarshalFactoryProvider;

/**
 * {@link MarshalFactoryProvider} implementation that registers the DER codec
 * (JGDMS-STD-006) with the {@link net.jini.io.MarshalledInstance} ServiceLoader
 * dispatch mechanism (JGDMS-STD-008 sec.13.2).
 *
 * <p>This class is discovered via
 * {@code META-INF/services/net.jini.io.MarshalFactoryProvider} when
 * {@code jgdms-der} is on the classpath. {@link net.jini.io.MarshalledInstance#get}
 * looks up this provider for any instance whose {@code payloadFormat} field equals
 * {@link MarshalledInstanceRecord#PAYLOAD_FORMAT} ({@code "JGDMS-STD-006/DER"}),
 * and delegates decoding to the returned {@link DerMarshalFactory}.
 *
 * <p>This enables a base {@link net.jini.io.MarshalledInstance} carrying DER state
 * to be decoded on any receiver that has {@code jgdms-der} on its classpath, without
 * requiring a {@link DerMarshalledInstance} subclass instance at the receiving end.
 * Format detection happens purely via {@code payloadFormat} and this ServiceLoader entry.
 *
 * <h2>Statelessness</h2>
 * <p>
 * This class is stateless. Both {@link #payloadFormat()} and {@link #marshalFactory()}
 * are pure; {@code marshalFactory()} is safe to call concurrently and its result
 * may be cached by the framework.
 */
public final class DerMarshalFactoryProvider implements MarshalFactoryProvider {

    /**
     * Public no-arg constructor required by {@link java.util.ServiceLoader}.
     */
    public DerMarshalFactoryProvider() {}

    /**
     * Returns the DER payload-format identifier handled by this provider.
     *
     * @return {@link MarshalledInstanceRecord#PAYLOAD_FORMAT} ({@code "JGDMS-STD-006/DER"})
     */
    @Override
    public String payloadFormat() {
        return MarshalledInstanceRecord.PAYLOAD_FORMAT;
    }

    /**
     * Returns a new {@link DerMarshalFactory} instance. The factory is stateless so
     * a new instance per call is inexpensive.
     *
     * @return a new {@link DerMarshalFactory}
     */
    @Override
    public MarshalFactory marshalFactory() {
        return new DerMarshalFactory();
    }
}
