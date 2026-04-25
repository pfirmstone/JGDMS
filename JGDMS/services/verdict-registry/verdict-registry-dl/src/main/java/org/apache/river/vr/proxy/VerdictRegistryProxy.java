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
package org.apache.river.vr.proxy;

import java.rmi.RemoteException;
import java.security.PublicKey;
import java.util.Set;
import org.apache.river.api.codebase.CrashReport;
import org.apache.river.api.codebase.RegistryVerdict;
import org.apache.river.api.codebase.SignedVerdict;
import org.apache.river.api.codebase.VerdictRegistry;
import org.apache.river.api.net.Uri;

/**
 * Client-side smart proxy for the {@link VerdictRegistry} service.
 *
 * <p>This class is downloaded to client JVMs and forwards all
 * {@link VerdictRegistry} method calls to the remote server-side
 * implementation over a JERI transport channel.
 *
 * @see VerdictRegistry
 * @since 3.1.1
 */
public class VerdictRegistryProxy implements VerdictRegistry {

    private final VerdictRegistry server;

    /**
     * Creates a new proxy that delegates to the given remote {@code server}.
     *
     * @param server the remote server endpoint; must be non-null
     */
    public VerdictRegistryProxy(VerdictRegistry server) {
        if (server == null) throw new NullPointerException("server");
        this.server = server;
    }

    @Override
    public void registerAnalysisEngine(String engineId,
                                        PublicKey engineKey,
                                        String sigAlgorithm) throws RemoteException {
        server.registerAnalysisEngine(engineId, engineKey, sigAlgorithm);
    }

    @Override
    public void revokeAnalysisEngine(String engineId) throws RemoteException {
        server.revokeAnalysisEngine(engineId);
    }

    @Override
    public void submitVerdict(String engineId, SignedVerdict verdict) throws RemoteException {
        server.submitVerdict(engineId, verdict);
    }

    @Override
    public void reportCrash(CrashReport report) throws RemoteException {
        server.reportCrash(report);
    }

    @Override
    public RegistryVerdict getVerdict(Set<Uri> codebaseUrls) throws RemoteException {
        return server.getVerdict(codebaseUrls);
    }
}
