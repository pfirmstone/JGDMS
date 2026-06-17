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

package org.apache.river.api.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.rmi.server.UID;
import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

import org.junit.Test;

/**
 * Engine-swap belt-and-suspenders (STD-008 G1): explicitly round-trips
 * {@code @AtomicExternal} / {@link java.io.Externalizable} values through the
 * atomic codec now that {@link AtomicMarshalOutputStream} writes via
 * {@code ObjOutputStream} (serialize(PutArg)) rather than JDK Object Serialization.
 *
 * <p>These types are NOT {@code @AtomicSerial} and have no {@code serialize(PutArg)} --
 * they are handled by the {@code writeExternal(ObjectOutput)} / {@code (ObjectInput)} ctor
 * path via their registered {@code @AtomicExternal} serializers (DateSerializer, UIDSerializer,
 * the boxed-primitive serializers). This test proves {@code ObjOutputStream} drives that path
 * correctly (the suite already exercises it implicitly; this makes it explicit).
 */
public class AtomicExternalRoundTripTest {

    private static Object roundTrip(Object o) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new AtomicMarshalOutputStream(baos, null);
        oos.writeObject(o);
        oos.flush();
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ObjectInputStream in = AtomicMarshalInputStream.create(bais, null, false, null, null, false);
        return in.readObject();
    }

    /** java.util.Date -> DateSerializer (@AtomicExternal, writeExternal/readExternal). */
    @Test
    public void dateRoundTrips() throws Exception {
        Date d = new Date(1_234_567_890_123L);
        Object result = roundTrip(d);
        assertEquals(d, result);
        assertNotSame("decode must reconstruct a fresh instance", d, result);
    }

    /** java.rmi.server.UID -> UIDSerializer (@AtomicExternal). */
    @Test
    public void uidRoundTrips() throws Exception {
        UID uid = new UID();
        assertEquals(uid, roundTrip(uid));
    }

    /** Boxed primitives -> Byte/Short/Integer/Long/Float/Double/Character/Boolean serializers. */
    @Test
    public void boxedPrimitivesRoundTrip() throws Exception {
        assertEquals(Byte.valueOf((byte) 7),        roundTrip(Byte.valueOf((byte) 7)));
        assertEquals(Short.valueOf((short) 300),    roundTrip(Short.valueOf((short) 300)));
        assertEquals(Integer.valueOf(42),           roundTrip(Integer.valueOf(42)));
        assertEquals(Long.valueOf(9_999_999_999L),  roundTrip(Long.valueOf(9_999_999_999L)));
        assertEquals(Float.valueOf(2.5f),           roundTrip(Float.valueOf(2.5f)));
        assertEquals(Double.valueOf(3.14159d),      roundTrip(Double.valueOf(3.14159d)));
        assertEquals(Character.valueOf('Z'),        roundTrip(Character.valueOf('Z')));
        assertEquals(Boolean.TRUE,                  roundTrip(Boolean.TRUE));
    }
}
