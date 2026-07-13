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
import java.util.SortedSet;

/**
 * Declared-type shape-selection fixture: a field whose ACTUAL declared Java type is {@link
 * SortedSet}, read back via the typed {@code arg.get(name, val, SortedSet.class)} accessor
 * (which requires the decoded value to actually be a {@code SortedSet}, not merely a plain {@code
 * Set}). Contrast {@link PlainSetFieldRecord}, whose declared field type is the bare {@link
 * java.util.Set}.
 *
 * <p>Tests build the schema by hand (an {@code orderedset:int} token, exactly as {@code
 * CollectionOrderingTest} does), so {@code serialForm()}'s declared type here is not what drives
 * the wire token -- it is what {@code ObjectCodec.decodeCollection}'s declared-type consultation
 * (via reflecting this class's {@code "items"} field) reads to choose the {@code SortedSet}-
 * shaped immutable wrapper over the plain one.
 */
@AtomicSerial
public final class SortedSetFieldRecord {

    public static AtomicSerial.SerialForm[] serialForm() {
        return new AtomicSerial.SerialForm[] {
            new AtomicSerial.SerialForm("items", SortedSet.class),
        };
    }

    public static void serialize(AtomicSerial.PutArg arg, SortedSetFieldRecord o) throws IOException {
        arg.put("items", o.items);
        arg.writeArgs();
    }

    private final SortedSet<Integer> items;

    public SortedSetFieldRecord(SortedSet<Integer> items) {
        this.items = items;
    }

    public SortedSetFieldRecord(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
        check(arg);
        this.items = arg.get("items", Collections.emptySortedSet(), SortedSet.class);
    }

    public static AtomicSerial.GetArg check(AtomicSerial.GetArg arg)
            throws IOException, ClassNotFoundException {
        return arg;
    }

    public SortedSet<Integer> getItems() {
        return items;
    }
}
