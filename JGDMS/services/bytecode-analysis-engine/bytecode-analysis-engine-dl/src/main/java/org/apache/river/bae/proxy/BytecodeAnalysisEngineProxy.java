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
package org.apache.river.bae.proxy;

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.Set;
import net.jini.id.Uuid;
import org.apache.river.api.codebase.BytecodeAnalysisEngine;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.net.Uri;
import org.apache.river.proxy.AbstractSmartProxy;

/**
 * Client-side smart proxy for the {@link BytecodeAnalysisEngine} service.
 *
 * <p>This class is downloaded to client JVMs and forwards
 * {@link #requestAnalysis} calls to the remote server-side implementation
 * over a JERI transport channel.
 *
 * <p>The proxy is {@link AtomicSerial} and extends {@link AbstractSmartProxy},
 * which provides safe deserialization, {@link net.jini.export.ProxyAccessor},
 * and {@link net.jini.id.ReferentUuid} identity based on the service UUID.
 *
 * @see BytecodeAnalysisEngine
 * @see AbstractSmartProxy
 * @since 3.1.1
 */
@AtomicSerial
public class BytecodeAnalysisEngineProxy
        extends AbstractSmartProxy
        implements BytecodeAnalysisEngine {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new proxy wrapping the given server stub.
     *
     * @param server  the remote server stub; must be non-null
     * @param proxyID the service's stable unique identifier; must be non-null
     */
    public BytecodeAnalysisEngineProxy(BytecodeAnalysisEngine server, Uuid proxyID) {
        super(server, proxyID);
    }

    /**
     * {@link AtomicSerial} deserialization constructor.
     *
     * @param arg the deserialization argument bag
     * @throws IOException if deserialization validation fails
     */
    BytecodeAnalysisEngineProxy(GetArg arg) throws IOException {
        super(arg);
    }

    @Override
    public void requestAnalysis(Set<Uri> codebaseUrls) throws RemoteException {
        ((BytecodeAnalysisEngine) server).requestAnalysis(codebaseUrls);
    }
}
