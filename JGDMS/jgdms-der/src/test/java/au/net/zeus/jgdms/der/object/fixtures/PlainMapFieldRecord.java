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
import java.util.Map;

/**
 * Map-side sibling of {@link PlainSetFieldRecord}: a field whose ACTUAL declared Java type is the
 * bare {@link Map}, read back via {@code arg.get(name, val, Map.class)}. Contrast {@link
 * SortedMapFieldRecord}: decoding the SAME {@code orderedmap:{int}{java.lang.String}} wire bytes
 * must yield a plain (non-sorted) wrapper here, but a {@code SortedMap}-shaped wrapper there.
 */
@AtomicSerial
public final class PlainMapFieldRecord {

    public static AtomicSerial.SerialForm[] serialForm() {
        return new AtomicSerial.SerialForm[] {
            new AtomicSerial.SerialForm("items", Map.class),
        };
    }

    public static void serialize(AtomicSerial.PutArg arg, PlainMapFieldRecord o) throws IOException {
        arg.put("items", o.items);
        arg.writeArgs();
    }

    private final Map<Integer, String> items;

    public PlainMapFieldRecord(Map<Integer, String> items) {
        this.items = items;
    }

    public PlainMapFieldRecord(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
        check(arg);
        this.items = arg.get("items", Collections.emptyMap(), Map.class);
    }

    public static AtomicSerial.GetArg check(AtomicSerial.GetArg arg)
            throws IOException, ClassNotFoundException {
        return arg;
    }

    public Map<Integer, String> getItems() {
        return items;
    }
}
