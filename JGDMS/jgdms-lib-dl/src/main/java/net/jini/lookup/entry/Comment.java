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
 * A free-form comment about a service.
 * 
 * @author Sun Microsystems, Inc.
 */
@SerialEntry
public class Comment extends AbstractEntry {
    private static final long serialVersionUID = 7138608904371928208L;

    /**
     * Returns the wire field schema for this entry class.
     *
     * @return array of wire fields
     */
    public static EntryWireField[] entryForm() {
        return new EntryWireField[] {
            new EntryWireField("comment", String.class),
        };
    }

    /**
     * Deserialization constructor required by {@link SerialEntry @SerialEntry}.
     *
     * @param arg the source of field values
     * @throws IOException if a field value cannot be read or validated
     */
    public Comment(GetEntryArg arg) throws IOException {
        comment = arg.get("comment", null, String.class);
    }

    /**
     * Serialization method required by {@link SerialEntry @SerialEntry}.
     *
     * @param arg the destination for field values
     * @param obj the instance to serialize
     * @throws IOException if a field value cannot be written
     */
    public static void serialize(PutEntryArg arg, Comment obj) throws IOException {
        arg.put("comment", obj.comment);
        arg.writeArgs();
    }

    /**
     * Construct an empty instance of this class.
     */
    public Comment() {
    }
    
    /**
     * Construct an instance of this class, with all fields
     * initialized appropriately.
     * @param comment the contents of the comment
     */
    public Comment(String comment) {
	this.comment = comment;
    }

    /**
     * The comment itself.
     *
     * @serial
     */
    public String comment;
}
