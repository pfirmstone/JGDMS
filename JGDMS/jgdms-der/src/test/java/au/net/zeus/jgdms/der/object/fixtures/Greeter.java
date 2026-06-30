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

/**
 * Test fixture: a remote-like interface used as the declared type of an
 * {@code @AtomicSerial} field whose runtime value is a dynamic
 * {@code java.lang.reflect.Proxy} (the nested-proxy DER case, mirroring
 * e.g. {@code AdminProxy.admin} declared as the {@code OutriggerAdmin}
 * interface). An interface declared field maps to wireType {@code "@AtomicSerial"}.
 */
public interface Greeter {
    String greet(String who);
}
