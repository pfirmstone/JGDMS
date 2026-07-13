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
import java.util.Collections;
import java.util.Set;

/**
 * Declared-type shape-selection fixture: a field whose ACTUAL declared Java type is the bare
 * {@link Set} (e.g. a {@code LinkedHashSet}-style field), read back via the typed {@code
 * arg.get(name, val, Set.class)} accessor. Contrast {@link SortedSetFieldRecord}, whose declared
 * field type is {@link java.util.SortedSet}: decoding the SAME {@code orderedset:int} wire bytes
 * must yield a plain (non-sorted) wrapper here, but a {@code SortedSet}-shaped wrapper there.
 */
@AtomicSerial
public final class PlainSetFieldRecord {

    public static AtomicSerial.SerialForm[] serialForm() {
        return new AtomicSerial.SerialForm[] {
            new AtomicSerial.SerialForm("items", Set.class),
        };
    }

    public static void serialize(AtomicSerial.PutArg arg, PlainSetFieldRecord o) throws IOException {
        arg.put("items", o.items);
        arg.writeArgs();
    }

    private final Set<Integer> items;

    public PlainSetFieldRecord(Set<Integer> items) {
        this.items = items;
    }

    public PlainSetFieldRecord(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
        check(arg);
        this.items = arg.get("items", Collections.emptySet(), Set.class);
    }

    public static AtomicSerial.GetArg check(AtomicSerial.GetArg arg)
            throws IOException, ClassNotFoundException {
        return arg;
    }

    public Set<Integer> getItems() {
        return items;
    }
}
