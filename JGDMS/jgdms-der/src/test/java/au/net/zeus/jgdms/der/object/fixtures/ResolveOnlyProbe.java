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

/**
 * A clause-3 representative: {@code @AtomicSerial} + {@link Resolve} but NOT {@code @Serializer}
 * (no statically declared {@code replaceObType}). This is the shape of the java.io
 * serialization-proxy classes the admission gate admits by its third clause (e.g.
 * {@code net.jini.core.constraint.Integrity}, {@code ServerAuthentication},
 * {@code au.net.zeus.jgdms.der.object.DerProxySerializer}), whose resolved runtime type is
 * unknowable before construction. Its decode ctor records that it ran, so a test can show the
 * clause-3 residual (ctor runs) is nonetheless BOUNDED: {@code readResolve()} yields a value
 * whose type is checked by the caller's typed {@code get()} — a wrong-typed slot cannot be
 * populated with it.
 */
@AtomicSerial
public final class ResolveOnlyProbe implements Resolve {

    public static final AtomicBoolean DECODE_CONSTRUCTED = new AtomicBoolean(false);

    public static AtomicSerial.SerialForm[] serialForm() {
        return new AtomicSerial.SerialForm[] {
            new AtomicSerial.SerialForm("tag", int.class),
        };
    }

    public static void serialize(AtomicSerial.PutArg arg, ResolveOnlyProbe o) throws IOException {
        arg.put("tag", o.tag);
        arg.writeArgs();
    }

    private final int tag;

    public ResolveOnlyProbe() {
        this.tag = 3;
    }

    public ResolveOnlyProbe(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
        DECODE_CONSTRUCTED.set(true);
        this.tag = arg.get("tag", 0);
    }

    /** Resolves to an unrelated value type, so a wrong-typed slot's cast rejects it. */
    @Override
    public Object readResolve() throws ObjectStreamException {
        return new ProbeValue();
    }
}
