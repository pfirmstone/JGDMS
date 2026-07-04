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
import java.util.Set;

/**
 * Fail-closed lock fixture: an {@code @AtomicSerial} class declaring a single plain
 * <em>interface</em> collection field ({@code Set}). It exists so a test can assert that the
 * AUTOMATIC path does NOT silently downgrade a plain collection field to a non-canonical
 * encoding: {@code toWireType(Set.class)} returns {@code "@AtomicSerial"}, and encoding a
 * {@code HashSet} value then fails fast because a {@code HashSet} is neither {@code @AtomicSerial}
 * nor a registered DER serializer -- so no non-canonical bytes are ever produced.
 *
 * <p>(A CONCRETE collection field type such as {@code HashSet.class} throws even earlier, at
 * schema generation -- that case is exercised directly via {@code SchemaGenerator.toWireType}
 * rather than through a fixture, since a class carrying such a field cannot be schema-generated
 * at all.)
 */
@AtomicSerial
public final class PlainCollectionFieldRecord {

    public static AtomicSerial.SerialForm[] serialForm() {
        return new AtomicSerial.SerialForm[] {
            // Interface collection type -> toWireType returns "@AtomicSerial"; encode then throws
            // because the runtime HashSet is neither @AtomicSerial nor a registered DER serializer.
            new AtomicSerial.SerialForm("ifaceSet", Set.class),
        };
    }

    public static void serialize(AtomicSerial.PutArg arg, PlainCollectionFieldRecord o)
            throws IOException {
        arg.put("ifaceSet", o.ifaceSet);
        arg.writeArgs();
    }

    private final Set<Integer> ifaceSet;

    public PlainCollectionFieldRecord(Set<Integer> ifaceSet) {
        this.ifaceSet = ifaceSet;
    }

    public PlainCollectionFieldRecord(AtomicSerial.GetArg arg)
            throws IOException, ClassNotFoundException {
        this.ifaceSet = null;
    }

    public static AtomicSerial.GetArg check(AtomicSerial.GetArg arg)
            throws IOException, ClassNotFoundException {
        return arg;
    }
}
