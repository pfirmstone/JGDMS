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

package net.jini.constraint;

import net.jini.core.constraint.Integrity;
import net.jini.core.constraint.InvocationConstraints;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Empirical proof that {@link StringMethodConstraints} conveys a method's parameter
 * types <em>by name</em> (as {@code String}s), so a proxy whose constrained methods
 * reference a class the receiver does NOT have deserializes <b>without</b>
 * {@link ClassNotFoundException} -- the {@code @since 3.1} CNFE-avoidance the class
 * javadoc promises: <i>"Canonical class name strings are used in place of class
 * instances ... to avoid ClassNotFoundExceptions or the requirement to use codebase
 * annotations, when classes don't exist."</i>
 *
 * <p>This is the mechanism that lets a JGDMS 3.X-vintage client deserialize a 4.0 proxy
 * whose method signatures reference 4.0-only types it lacks locally, rather than
 * crashing -- the point under dispute (an agent claimed such a client would hit a CNFE).
 *
 * <p>Placed in {@code net.jini.constraint} to reach the package-private
 * {@code StringMethodDesc(String, String[], InvocationConstraints)} descriptor
 * constructor, which is the only way to name a parameter type that has no local class.
 */
class StringMethodConstraintsCnfeTest {

    /** A class name that exists nowhere on any classpath (keeps the test non-vacuous). */
    private static final String ABSENT_TYPE = "com.example.does.not.Exist$ParamType42";

    @Test
    void absentParameterTypeClass_roundTripsWithoutCNFE() throws Exception {
        // Precondition: the named parameter type genuinely cannot be loaded locally.
        assertThrows(ClassNotFoundException.class, () -> Class.forName(ABSENT_TYPE),
                "precondition: the parameter type must not exist locally");

        InvocationConstraints ic = new InvocationConstraints(Integrity.YES, null);
        // Method "doStuff(com.example.does.not.Exist$ParamType42)" -- parameter type by NAME.
        StringMethodConstraints.StringMethodDesc desc =
                new StringMethodConstraints.StringMethodDesc(
                        "doStuff", new String[]{ ABSENT_TYPE }, ic);
        StringMethodConstraints smc =
                new StringMethodConstraints(new StringMethodConstraints.StringMethodDesc[]{ desc });

        byte[] bytes = serialize(smc);

        // The absent type name travels verbatim as a STRING (by value), not as a Class.
        assertTrue(contains(bytes, ABSENT_TYPE.getBytes(StandardCharsets.UTF_8)),
                "parameter type must be serialized by name (string), not as a Class instance");

        // The headline: deserialization MUST NOT resolve the absent parameter-type class.
        Object back = assertDoesNotThrow(() -> {
            try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
                return ois.readObject();
            }
        }, "deserialization must not throw ClassNotFoundException for the absent parameter type");

        StringMethodConstraints recovered = assertInstanceOf(StringMethodConstraints.class, back);

        // Round-trip preserved the type by value: re-serializing still carries the name.
        assertTrue(contains(serialize(recovered), ABSENT_TYPE.getBytes(StandardCharsets.UTF_8)),
                "the absent parameter-type name must survive the round-trip by value");
    }

    private static byte[] serialize(Object o) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(bos)) { oos.writeObject(o); }
        return bos.toByteArray();
    }

    private static boolean contains(byte[] hay, byte[] needle) {
        outer:
        for (int i = 0; i + needle.length <= hay.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (hay[i + j] != needle[j]) continue outer;
            }
            return true;
        }
        return false;
    }
}
