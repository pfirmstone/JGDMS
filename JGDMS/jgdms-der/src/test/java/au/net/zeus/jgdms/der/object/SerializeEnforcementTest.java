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

package au.net.zeus.jgdms.der.object;

import au.net.zeus.jgdms.der.DerException;
import au.net.zeus.jgdms.der.schema.SchemaChain;
import au.net.zeus.jgdms.der.schema.SchemaGenerator;
import org.apache.river.api.io.AtomicSerial;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * STD-008 full-enforcement of the {@code @AtomicSerial} WRITE contract.
 *
 * <p>An {@code @AtomicSerial} class that does NOT implement
 * {@code public static void serialize(AtomicSerial.PutArg, T)} must fail fast on
 * encode with a {@link DerException}. The DER codec NEVER reflects on private
 * fields (that would imitate Java Object Serialization and reintroduce its
 * security problems), so a class that declines the WRITE contract is simply not
 * serializable -- there is no field-reflection fallback.
 */
class SerializeEnforcementTest {

    /**
     * Encoding a conforming-shape {@code @AtomicSerial} class that lacks
     * {@code serialize(PutArg)} must throw {@link DerException}, and the message
     * must point at the missing WRITE contract.
     */
    @Test
    void encode_withoutSerialize_failsFast_withDerException() throws Exception {
        NoSerialize obj = new NoSerialize(7);
        SchemaChain.Result chain = SchemaGenerator.generateChain(NoSerialize.class);

        DerException ex = assertThrows(DerException.class,
                () -> ObjectCodec.encodeHierarchy(obj, chain),
                "Encoding an @AtomicSerial class without serialize(PutArg) must fail fast");

        assertTrue(ex.getMessage().contains("serialize"),
                "DerException must explain the missing serialize(PutArg) contract: "
                + ex.getMessage());
    }

    /**
     * A well-formed {@code @AtomicSerial} class (valid {@code serialForm()} and
     * {@code (GetArg)} constructor) that DELIBERATELY omits {@code serialize(PutArg)}.
     */
    @AtomicSerial
    public static final class NoSerialize {

        public static AtomicSerial.SerialForm[] serialForm() {
            return new AtomicSerial.SerialForm[] {
                new AtomicSerial.SerialForm("v", int.class),
            };
        }

        private final int v;

        public NoSerialize(int v) {
            this.v = v;
        }

        public NoSerialize(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
            this.v = arg.get("v", 0);
        }

        // No serialize(PutArg, NoSerialize) method -- this is the point of the fixture.

        public int getV() {
            return v;
        }
    }
}
