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

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.river.api.io.AtomicSerial;

/**
 * The {@code PutArg} the DER encoder hands to a class's
 * {@code public static void serialize(PutArg, T)} method so the class can
 * declare its serial form -- the {@code @AtomicSerial} WRITE contract.
 *
 * <p>It simply captures each {@code put(name, value)} into an ordered map keyed
 * by field name (primitives are boxed). The DER encoder then encodes each
 * {@code serialForm()} field from the captured value. This is the deliberate
 * alternative to reading private fields by reflection: the codec NEVER imitates
 * Java Object Serialization's field grabbing -- a class controls exactly what is
 * written, and a class that declines to implement {@code serialize(PutArg)} is
 * not serialized (the encoder fails fast).
 *
 * <p>{@link #writeArgs()} is a no-op (the values are already captured) and
 * {@link #output()} is unsupported -- there is no underlying object stream,
 * by design.
 *
 * <p>Construction goes through the {@code protected PutArg()} ctor, which (like
 * {@code DerGetArg}'s use of {@code protected GetArg()}) is a no-op as of the
 * 4.0.0 Java-Serialization uncoupling: the
 * {@code SerializablePermission("enableSubclassImplementation")} guard has been
 * dropped (idempotency of the memoizing GetArg accessors makes check-then-construct
 * sound without it).
 */
final class DerPutArg extends AtomicSerial.PutArg {

    private final Map<String, Object> values = new LinkedHashMap<>();

    DerPutArg() {
        super();
    }

    /** The captured {@code put(name, value)} entries, in put order. */
    Map<String, Object> captured() {
        return values;
    }

    @Override
    public void put(String name, boolean val) {
        values.put(name, val);
    }

    @Override
    public void put(String name, byte val) {
        values.put(name, val);
    }

    @Override
    public void put(String name, char val) {
        values.put(name, val);
    }

    @Override
    public void put(String name, short val) {
        values.put(name, val);
    }

    @Override
    public void put(String name, int val) {
        values.put(name, val);
    }

    @Override
    public void put(String name, long val) {
        values.put(name, val);
    }

    @Override
    public void put(String name, float val) {
        values.put(name, val);
    }

    @Override
    public void put(String name, double val) {
        values.put(name, val);
    }

    @Override
    public void put(String name, Object val) {
        values.put(name, val);
    }

    @Override
    public void writeArgs() {
        // No-op: put(name, value) already captured every field.
    }

    @Override
    public Collection getObjectStreamContext() {
        return Collections.emptyList();
    }
}
