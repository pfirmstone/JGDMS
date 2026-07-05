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
import java.util.HashSet;
import java.util.Set;

/**
 * Auto-wiring fixture (memo §8): a plain {@code Set<Foo>} field. The serial-form field name
 * {@code "tags"} matches the backing field, so {@code SchemaGenerator} reflects
 * {@code Field.getGenericType()} and derives {@code set:@AtomicSerial} automatically -- no
 * developer-supplied element wire-type, no annotation (memo §3, E1). Round-trips through the codec.
 */
@AtomicSerial
public final class AutoWiredSetRecord {

    public static AtomicSerial.SerialForm[] serialForm() {
        return new AtomicSerial.SerialForm[] {
            new AtomicSerial.SerialForm("tags", Set.class),
        };
    }

    public static void serialize(AtomicSerial.PutArg arg, AutoWiredSetRecord o) throws IOException {
        arg.put("tags", o.tags);
        arg.writeArgs();
    }

    private final Set<Foo> tags;

    public AutoWiredSetRecord(Set<Foo> tags) {
        this.tags = tags;
    }

    @SuppressWarnings("unchecked")
    public AutoWiredSetRecord(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
        Object v = arg.get("tags", null);
        this.tags = (v == null) ? null : new HashSet<>((Set<Foo>) v);
    }

    public static AtomicSerial.GetArg check(AtomicSerial.GetArg arg)
            throws IOException, ClassNotFoundException {
        return arg;
    }

    public Set<Foo> getTags() { return tags; }
}
