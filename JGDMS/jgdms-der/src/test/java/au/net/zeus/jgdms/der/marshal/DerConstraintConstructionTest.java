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

import au.net.zeus.jgdms.der.marshal.fixtures.VersionedRecord;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.MarshallingFormat;
import net.jini.io.MarshalFactory;
import net.jini.io.MarshalledInstance;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Enforcement surface 1 (JGDMS-STD-008 sec.13), proven where the DER
 * {@code MarshalFactoryProvider} IS on the classpath (unlike the platform-only test):
 * a {@link MarshallingFormat#DER} requirement selects the DER codec, and the
 * constraint-aware {@link MarshalledInstance} constructor round-trips via DER with NO
 * MarshalledInstance subclass.
 */
class DerConstraintConstructionTest {

    private static InvocationConstraints requireDer() {
        return new InvocationConstraints(MarshallingFormat.DER, null);
    }

    @Test
    void requiredDerSelectsTheDerFactory() throws Exception {
        MarshalFactory f = MarshalledInstance.chooseMarshalFactory(requireDer());
        assertInstanceOf(DerMarshalFactory.class, f,
                "MarshallingFormat.DER must resolve to DerMarshalFactory via ServiceLoader");
    }

    @Test
    void constraintAwareCtorRoundTripsUnderDer() throws Exception {
        VersionedRecord orig = new VersionedRecord(42, "hello", "world");
        MarshalledInstance mi = new MarshalledInstance(
                orig, Collections.emptySet(), requireDer());
        VersionedRecord copy = mi.get(false, VersionedRecord.class);
        assertEquals(orig, copy,
                "constraint-aware ctor must round-trip the object through the DER codec");
    }
}
