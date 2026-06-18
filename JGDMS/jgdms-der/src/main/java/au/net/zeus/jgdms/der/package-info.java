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

/**
 * Language-neutral DER (X.690) wire-format codec for {@code @AtomicSerial}
 * objects, per JGDMS-STD-006.
 * <p>
 * This module provides, in layered fashion:
 * <ul>
 *   <li>pure DER TLV encoding/decoding primitives (tag, length, INTEGER,
 *       BOOLEAN, OCTET STRING, UTF8String, SEQUENCE);</li>
 *   <li>the {@code AtomicSerialSchemaRecord} representation and its SHA-256
 *       Merkle-chain schema digest;</li>
 *   <li>the {@code GetArg} DER adapter (a complete field store);</li>
 *   <li>per-class object encode/decode honouring the private-namespace
 *       invariant (STD-006 &sect;3.9, &sect;3.10);</li>
 *   <li>the {@code MarshalledInstance} container with its embedded,
 *       authoritative schema (STD-006 &sect;7.8).</li>
 * </ul>
 */
package au.net.zeus.jgdms.der;
