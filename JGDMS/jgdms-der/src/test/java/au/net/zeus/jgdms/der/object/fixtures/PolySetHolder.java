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
import java.util.Collections;
import java.util.List;
import org.apache.river.api.io.AtomicSerial;

/**
 * Legitimate-polymorphism fixture: a {@code List<NestedValue>} field whose runtime elements
 * are the {@code @AtomicSerial} subtype {@link NestedValueSub}. The recovered declared element
 * type is the supertype {@code NestedValue}; the wire leaf {@code NestedValueSub} is admitted by
 * clause 1 ({@code NestedValue.isAssignableFrom(NestedValueSub)}). A {@code list:} (preserve)
 * field so no octet-order constraint applies to the multi-element round-trip.
 */
@AtomicSerial
public final class PolySetHolder {

    public static AtomicSerial.SerialForm[] serialForm() {
        return new AtomicSerial.SerialForm[] {
            new AtomicSerial.SerialForm("values", List.class),
        };
    }

    public static void serialize(AtomicSerial.PutArg arg, PolySetHolder o) throws IOException {
        arg.put("values", o.values);
        arg.writeArgs();
    }

    private final List<NestedValue> values;

    public PolySetHolder(List<NestedValue> values) {
        this.values = values;
    }

    @SuppressWarnings("unchecked")
    public PolySetHolder(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
        this.values = (List<NestedValue>) arg.get("values", Collections.emptyList(), List.class);
    }

    public List<NestedValue> values() {
        return values;
    }
}
