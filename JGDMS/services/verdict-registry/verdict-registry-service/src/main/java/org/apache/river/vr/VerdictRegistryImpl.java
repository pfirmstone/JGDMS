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
package org.apache.river.vr;

import java.rmi.RemoteException;
import java.security.PublicKey;
import java.util.Set;
import org.apache.river.api.codebase.CrashReport;
import org.apache.river.api.codebase.RegistryVerdict;
import org.apache.river.api.codebase.SignedVerdict;
import org.apache.river.api.codebase.VerdictRegistry;
import org.apache.river.api.net.Uri;

/**
 * Server-side implementation of the {@link VerdictRegistry} service.
 *
 * <p>This is the authoritative, low-risk component in the safe-codebase
 * architecture.  It manages {@code BytecodeAnalysisEngine} registration,
 * applies quorum policy over submitted {@link SignedVerdict} objects, and
 * produces authoritative {@link RegistryVerdict} results for clients.
 *
 * @see VerdictRegistry
 * @since 3.1.1
 */
public class VerdictRegistryImpl implements VerdictRegistry {

    /**
     * Creates a new {@code VerdictRegistryImpl}.
     */
    public VerdictRegistryImpl() {
    }

    @Override
    public void registerAnalysisEngine(String engineId,
                                        PublicKey engineKey,
                                        String sigAlgorithm) throws RemoteException {
        if (engineId == null) throw new NullPointerException("engineId");
        if (engineId.isEmpty()) throw new IllegalArgumentException("engineId must not be empty");
        if (engineKey == null) throw new NullPointerException("engineKey");
        if (sigAlgorithm == null) throw new NullPointerException("sigAlgorithm");
        if (sigAlgorithm.isEmpty()) throw new IllegalArgumentException("sigAlgorithm must not be empty");
        // TODO: store engineId -> (engineKey, sigAlgorithm) mapping.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void revokeAnalysisEngine(String engineId) throws RemoteException {
        if (engineId == null) throw new NullPointerException("engineId");
        if (engineId.isEmpty()) throw new IllegalArgumentException("engineId must not be empty");
        // TODO: remove engineId from registry and re-evaluate affected verdicts.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void submitVerdict(String engineId, SignedVerdict verdict) throws RemoteException {
        if (engineId == null) throw new NullPointerException("engineId");
        if (engineId.isEmpty()) throw new IllegalArgumentException("engineId must not be empty");
        if (verdict == null) throw new NullPointerException("verdict");
        // TODO: verify signature, apply quorum policy, publish RegistryVerdict.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void reportCrash(CrashReport report) throws RemoteException {
        if (report == null) throw new NullPointerException("report");
        // TODO: verify Phoenix identity, publish DANGEROUS RegistryVerdict.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public RegistryVerdict getVerdict(Set<Uri> codebaseUrls) throws RemoteException {
        if (codebaseUrls == null) throw new NullPointerException("codebaseUrls");
        if (codebaseUrls.isEmpty()) throw new IllegalArgumentException("codebaseUrls must not be empty");
        // TODO: return the current authoritative RegistryVerdict, or null.
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
