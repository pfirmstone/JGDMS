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
package net.jini.lookup.entry.jmx;

import java.io.IOException;
import net.jini.core.entry.EntryWireField;
import net.jini.core.entry.GetEntryArg;
import net.jini.core.entry.PutEntryArg;
import net.jini.core.entry.SerialEntry;
import net.jini.entry.AbstractEntry;

@SerialEntry
public class JMXProtocolType extends AbstractEntry {

  /**
   * Returns the wire field schema for this entry class.
   *
   * @return array of wire fields
   */
  public static EntryWireField[] entryForm() {
    return new EntryWireField[] {
      new EntryWireField("protocolType", String.class),
    };
  }

  /**
   * Deserialization constructor required by {@link SerialEntry @SerialEntry}.
   *
   * @param arg the source of field values
   * @throws IOException if a field value cannot be read or validated
   */
  public JMXProtocolType(GetEntryArg arg) throws IOException {
    protocolType = arg.get("protocolType", null, String.class);
  }

  /**
   * Serialization method required by {@link SerialEntry @SerialEntry}.
   *
   * @param arg the destination for field values
   * @param obj the instance to serialize
   * @throws IOException if a field value cannot be written
   */
  public static void serialize(PutEntryArg arg, JMXProtocolType obj) throws IOException {
    arg.put("protocolType", obj.protocolType);
    arg.writeArgs();
  }

  public static final String RMI = "rmi";
  public static final String IIOP = "iiop";
  public static final String JMXMP = "jmxmp";
  public String protocolType;

  public JMXProtocolType() {
    this((String) null);
  }

  public JMXProtocolType(String protocolType) {
    this.protocolType = (protocolType == null ? null : protocolType.toLowerCase());
  }
}