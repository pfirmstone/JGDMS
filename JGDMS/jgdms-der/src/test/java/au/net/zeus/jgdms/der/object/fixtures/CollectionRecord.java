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
 * STD-006 §3.8 collection-codec fixture: an {@code @AtomicSerial} class with a single
 * generic {@code Object} payload field named {@code "coll"}. The declared field type is
 * {@code Object.class} so a test can supply <em>any</em> {@code Collection} or {@code Map}
 * value and drive the wire-type discipline from a hand-built schema token
 * ({@code set:}/{@code orderedset:}/{@code list:}/{@code map:}/{@code orderedmap:}).
 *
 * <p>The codec reads the field value via {@code serialize(PutArg)} and encodes it per the
 * <em>schema's</em> wire-type (not the field's Java type), so this one fixture exercises
 * every collection discipline. On decode the {@code (GetArg)} constructor simply takes the
 * reconstructed collection back as an {@code Object}.
 */
@AtomicSerial
public final class CollectionRecord {

    public static AtomicSerial.SerialForm[] serialForm() {
        return new AtomicSerial.SerialForm[] {
            new AtomicSerial.SerialForm("coll", Object.class),
        };
    }

    public static void serialize(AtomicSerial.PutArg arg, CollectionRecord o) throws IOException {
        arg.put("coll", o.coll);
        arg.writeArgs();
    }

    private final Object coll;

    public CollectionRecord(Object coll) {
        this.coll = coll;
    }

    public CollectionRecord(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
        check(arg);
        this.coll = arg.get("coll", null);
    }

    public static AtomicSerial.GetArg check(AtomicSerial.GetArg arg)
            throws IOException, ClassNotFoundException {
        return arg; // no mandatory invariants -- allow null and any collection kind
    }

    public Object getColl() {
        return coll;
    }

    @Override
    public String toString() {
        return "CollectionRecord{coll=" + coll + '}';
    }
}
