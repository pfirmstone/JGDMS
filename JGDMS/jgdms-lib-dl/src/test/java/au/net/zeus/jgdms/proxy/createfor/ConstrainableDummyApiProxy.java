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
package au.net.zeus.jgdms.proxy.createfor;

import net.jini.id.Uuid;

/**
 * Stand-in for the shape {@code ServiceProxyProcessor.writeProxy} generates for
 * {@link DummyApi} -- a stateless, two-argument {@code create(server, proxyID)}
 * factory -- used by {@code AbstractSmartProxyTest} to exercise
 * {@link au.net.zeus.jgdms.proxy.AbstractSmartProxy#createFor} without pulling
 * in the full annotation-processor/AtomicSerial machinery.
 */
public final class ConstrainableDummyApiProxy implements DummyApi {

    public final Object server;
    public final Uuid proxyID;

    private ConstrainableDummyApiProxy(Object server, Uuid proxyID) {
        this.server = server;
        this.proxyID = proxyID;
    }

    public static Object create(Object server, Uuid proxyID) {
        return new ConstrainableDummyApiProxy(server, proxyID);
    }
}
