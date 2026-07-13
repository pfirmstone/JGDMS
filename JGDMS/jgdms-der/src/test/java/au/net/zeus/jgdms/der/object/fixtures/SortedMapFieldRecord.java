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
import java.util.SortedMap;

/**
 * Map-side sibling of {@link SortedSetFieldRecord}: a field whose ACTUAL declared Java type is
 * {@link SortedMap}, read back via {@code arg.get(name, val, SortedMap.class)}.
 */
@AtomicSerial
public final class SortedMapFieldRecord {

    public static AtomicSerial.SerialForm[] serialForm() {
        return new AtomicSerial.SerialForm[] {
            new AtomicSerial.SerialForm("items", SortedMap.class),
        };
    }

    public static void serialize(AtomicSerial.PutArg arg, SortedMapFieldRecord o) throws IOException {
        arg.put("items", o.items);
        arg.writeArgs();
    }

    private final SortedMap<Integer, String> items;

    public SortedMapFieldRecord(SortedMap<Integer, String> items) {
        this.items = items;
    }

    public SortedMapFieldRecord(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
        check(arg);
        this.items = arg.get("items", Collections.emptySortedMap(), SortedMap.class);
    }

    public static AtomicSerial.GetArg check(AtomicSerial.GetArg arg)
            throws IOException, ClassNotFoundException {
        return arg;
    }

    public SortedMap<Integer, String> getItems() {
        return items;
    }
}
