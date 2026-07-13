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
package au.net.zeus.jgdms.service.support.smartfixture;

import java.rmi.RemoteException;
import net.jini.id.Uuid;

/**
 * Stand-in for the shape {@code ServiceProxyProcessor.writeProxy} generates for a
 * STATEFUL {@code @SmartProxy} delegate: {@code create(server, proxyID, label)},
 * one extra positional argument after {@code proxyID}.
 */
public final class ConstrainableStatefulServiceApiProxy implements StatefulServiceApi {

    public final Object server;
    public final Uuid proxyID;
    public final String label;

    private ConstrainableStatefulServiceApiProxy(Object server, Uuid proxyID, String label) {
        this.server = server;
        this.proxyID = proxyID;
        this.label = label;
    }

    public static Object create(Object server, Uuid proxyID, String label) {
        return new ConstrainableStatefulServiceApiProxy(server, proxyID, label);
    }

    @Override
    public String describe() throws RemoteException {
        return label;
    }
}
