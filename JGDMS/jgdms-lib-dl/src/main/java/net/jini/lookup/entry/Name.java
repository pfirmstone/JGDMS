/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.jini.lookup.entry;

import java.io.IOException;
import net.jini.core.entry.EntryWireField;
import net.jini.core.entry.GetEntryArg;
import net.jini.core.entry.PutEntryArg;
import net.jini.core.entry.SerialEntry;
import net.jini.entry.AbstractEntry;

/**
 * The name of a service as used by users.  A service may have
 * multiple names.
 * 
 * @author Sun Microsystems, Inc.
 */
@SerialEntry
public class Name extends AbstractEntry {
    private static final long serialVersionUID = 2743215148071307201L;

    /**
     * Returns the wire field schema for this entry class.
     *
     * @return array of wire fields
     */
    public static EntryWireField[] entryForm() {
        return new EntryWireField[] {
            new EntryWireField("name", String.class),
        };
    }

    /**
     * Deserialization constructor required by {@link SerialEntry @SerialEntry}.
     *
     * @param arg the source of field values
     * @throws IOException if a field value cannot be read or validated
     */
    public Name(GetEntryArg arg) throws IOException {
        name = arg.get("name", null, String.class);
    }

    /**
     * Serialization method required by {@link SerialEntry @SerialEntry}.
     *
     * @param arg the destination for field values
     * @param obj the instance to serialize
     * @throws IOException if a field value cannot be written
     */
    public static void serialize(PutEntryArg arg, Name obj) throws IOException {
        arg.put("name", obj.name);
        arg.writeArgs();
    }

    /**
     * Construct an empty instance of this class.
     */
    public Name() {
    }

    /**
     * Construct an instance of this class, with all fields
     * initialized appropriately.
     *
     * @param name  the value of the name
     */
    public Name(String name) {
	this.name = name;
    }

    /**
     * The name itself.
     *
     * @serial
     */
    public String name;
}
