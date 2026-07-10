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

import net.jini.core.constraint.MarshallingFormat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Cross-module consistency: the platform-side {@link MarshallingFormat#ATOMIC_DER}
 * constraint identifier MUST equal the DER codec's wire format constant and the
 * {@link DerMarshalFactoryProvider}'s registered {@code payloadFormat}. The
 * constraint lives in {@code jgdms-platform} and inlines the identifier string
 * (it cannot depend on {@code jgdms-der}); this test guards against the two
 * drifting apart.
 */
class DerFormatConstraintConsistencyTest {

    @Test
    void derConstraintMatchesRecordPayloadFormat() {
        assertEquals(MarshalledInstanceRecord.PAYLOAD_FORMAT,
                MarshallingFormat.ATOMIC_DER.getFormat(),
                "MarshallingFormat.ATOMIC_DER must name the same wire format as "
                + "MarshalledInstanceRecord.PAYLOAD_FORMAT");
    }

    @Test
    void derConstraintMatchesProviderPayloadFormat() {
        assertEquals(new DerMarshalFactoryProvider().payloadFormat(),
                MarshallingFormat.ATOMIC_DER.getFormat(),
                "MarshallingFormat.ATOMIC_DER must name the same format the ServiceLoader "
                + "provider registers, so a MarshallingFormat.ATOMIC_DER requirement resolves "
                + "to the DER MarshalFactory");
    }
}
