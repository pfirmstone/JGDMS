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

package au.net.zeus.jgdms.der.object.fixtures;

import org.apache.river.api.io.AtomicSerial;

import java.io.IOException;

/**
 * Field-position {@code Any} fixture (STD-006 memo §2.1/§3): a minimal {@code @AtomicSerial}
 * class with a single serial field {@code "value"} declared <b>exactly</b> {@code Object.class}
 * -- the same shape as {@code net.jini.core.event.RemoteEvent.source} (inherited from
 * {@code java.util.EventObject}), but standalone so a test can drive {@link
 * au.net.zeus.jgdms.der.schema.SchemaGenerator#generate(Class)} for real (not a hand-built
 * schema) and confirm the field resolves to the {@code "any"} wire-type end-to-end through the
 * real {@code DerFieldStore}/{@code DerGetArg} dispatch.
 *
 * <p>Distinct from {@code CollectionRecord} (also an {@code Object.class} field, but purpose-built
 * so a test can supply an arbitrary Collection/Map value under a hand-picked collection token):
 * this fixture is for exercising the FIELD-position {@code Any} rule itself, including real schema
 * generation, not just the element-position collection codec.
 */
@AtomicSerial
public final class AnyFieldRecord {

    public static AtomicSerial.SerialForm[] serialForm() {
        return new AtomicSerial.SerialForm[] {
            new AtomicSerial.SerialForm("value", Object.class),
        };
    }

    public static void serialize(AtomicSerial.PutArg arg, AnyFieldRecord o) throws IOException {
        arg.put("value", o.value);
        arg.writeArgs();
    }

    private final Object value;

    public AnyFieldRecord(Object value) {
        this.value = value;
    }

    public AnyFieldRecord(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
        check(arg);
        this.value = arg.get("value", null);
    }

    public static AtomicSerial.GetArg check(AtomicSerial.GetArg arg)
            throws IOException, ClassNotFoundException {
        return arg; // no mandatory invariants -- allow null and any closed-subset value
    }

    public Object getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "AnyFieldRecord{value=" + value + '}';
    }
}
