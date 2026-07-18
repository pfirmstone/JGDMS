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

import java.io.IOException;
import java.io.ObjectStreamException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.Resolve;
import org.apache.river.api.io.Serializer;

/**
 * A hostile-model {@code @AtomicSerial} <em>serializer</em> annotated
 * {@code @Serializer(replaceObType = ProbeValue.class)} and implementing {@link Resolve}
 * -- structurally exactly like the platform serializers (and, decisively, like
 * {@code ThrowableSerializer}, whose {@code replaceObType} is {@code Throwable}). Its
 * {@code (GetArg)} <b>decode</b> constructor records that it ran in
 * {@link #DECODE_CONSTRUCTED}, standing in for a serializer whose decode ctor has a
 * dangerous side effect (e.g. {@code ThrowableSerializer} reflectively instantiating an
 * attacker-named {@code Throwable} subclass).
 *
 * <p>The decode-admission gate must REJECT this serializer when it is named on the wire
 * for a slot declared as an incompatible concrete type (its {@code replaceObType}
 * {@code ProbeValue} is not assignable to that slot) <b>before</b> the {@code (GetArg)}
 * ctor runs, i.e. with {@link #DECODE_CONSTRUCTED} still {@code false}.
 */
@Serializer(replaceObType = ProbeValue.class)
@AtomicSerial
public final class ForeignSerializer implements Resolve {

    /** Set true the instant the decode {@code (GetArg)} ctor runs -- the probe. */
    public static final AtomicBoolean DECODE_CONSTRUCTED = new AtomicBoolean(false);

    public static AtomicSerial.SerialForm[] serialForm() {
        return new AtomicSerial.SerialForm[] {
            new AtomicSerial.SerialForm("tag", int.class),
        };
    }

    public static void serialize(AtomicSerial.PutArg arg, ForeignSerializer o) throws IOException {
        arg.put("tag", o.tag);
        arg.writeArgs();
    }

    private final int tag;

    /** Encode-side ctor (the sender): does NOT trip the decode probe. */
    public ForeignSerializer(ProbeValue v) {
        this.tag = 7;
    }

    /** Decode-side ctor (the attack surface): trips the probe. */
    public ForeignSerializer(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
        DECODE_CONSTRUCTED.set(true);
        this.tag = arg.get("tag", 0);
    }

    @Override
    public Object readResolve() throws ObjectStreamException {
        return new ProbeValue();
    }
}
