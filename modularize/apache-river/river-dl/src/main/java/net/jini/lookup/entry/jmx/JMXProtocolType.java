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

import net.jini.entry.AbstractEntry;

public class JMXProtocolType extends AbstractEntry {
  public static final String RMI = "rmi";
  public static final String IIOP = "iiop";
  public static final String JMXMP = "jmxmp";
  public String protocolType;

  public JMXProtocolType() {
    this(null);
  }

  public JMXProtocolType(String protocolType) {
    this.protocolType = (protocolType == null ? null : protocolType.toLowerCase());
  }
}