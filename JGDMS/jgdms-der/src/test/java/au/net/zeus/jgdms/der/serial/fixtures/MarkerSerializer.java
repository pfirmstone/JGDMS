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

package au.net.zeus.jgdms.der.serial.fixtures;

import java.io.IOException;
import java.io.ObjectStreamException;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.Resolve;
import org.apache.river.api.io.Serializer;

/**
 * The {@code @AtomicSerial} serializer that stands in for {@link Marker} on the
 * DER wire. Annotated {@code @Serializer(replaceObType = Marker.class)} (the same
 * marker the platform serializers use) so {@code DerReplacer} maps {@code Marker}
 * to it; implements {@link Resolve} so the decode side rebuilds the original
 * {@link Marker} via {@link #readResolve()}.
 */
@Serializer(replaceObType = Marker.class)
@AtomicSerial
public final class MarkerSerializer implements Resolve {

    public static AtomicSerial.SerialForm[] serialForm() {
        return new AtomicSerial.SerialForm[] {
            new AtomicSerial.SerialForm("value", int.class),
        };
    }

    /** @AtomicSerial WRITE contract (STD-008): emit the flattened value. */
    public static void serialize(AtomicSerial.PutArg arg, MarkerSerializer o) throws IOException {
        arg.put("value", o.value);
        arg.writeArgs();
    }

    private final int value;

    public MarkerSerializer(Marker m) {
        this.value = m.value();
    }

    public MarkerSerializer(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
        this.value = arg.get("value", 0);
    }

    @Override
    public Object readResolve() throws ObjectStreamException {
        return new Marker(value);
    }
}
