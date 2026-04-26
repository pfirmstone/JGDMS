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

import java.io.IOException;
import java.rmi.RemoteException;
import java.security.PublicKey;
import java.util.Set;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.id.Uuid;
import net.jini.io.MarshalledInstance;
import org.apache.river.api.codebase.CrashReport;
import org.apache.river.api.codebase.RegistryVerdict;
import org.apache.river.api.codebase.SignedVerdict;
import org.apache.river.api.codebase.VerdictRegistry;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.net.Uri;
import org.apache.river.proxy.AbstractSmartProxy;

/**
 * Client-side smart proxy for the {@link VerdictRegistry} service.
 *
 * <p>This class is downloaded to client JVMs and forwards all
 * {@link VerdictRegistry} method calls to the remote server-side
 * implementation over a JERI transport channel.
 *
 * <p>The proxy is {@link AtomicSerial} and extends {@link AbstractSmartProxy},
 * which provides safe deserialization, {@link net.jini.export.ProxyAccessor},
 * and {@link net.jini.id.ReferentUuid} identity based on the service UUID.
 *
 * @see VerdictRegistry
 * @see AbstractSmartProxy
 * @since 3.1.1
 */
@AtomicSerial
public class VerdictRegistryProxy
        extends AbstractSmartProxy
        implements VerdictRegistry {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new proxy wrapping the given server stub.
     *
     * @param server  the remote server stub; must be non-null
     * @param proxyID the service's stable unique identifier; must be non-null
     */
    public VerdictRegistryProxy(VerdictRegistry server, Uuid proxyID) {
        super(server, proxyID);
    }

    /**
     * {@link AtomicSerial} deserialization constructor.
     *
     * @param arg the deserialization argument bag
     * @throws IOException if deserialization validation fails
     */
    VerdictRegistryProxy(GetArg arg) throws IOException {
        super(arg);
    }

    @Override
    protected Class<?>[] getServiceInterfaces() {
        return new Class<?>[]{ VerdictRegistry.class };
    }

    @Override
    public void registerAnalysisEngine(String engineId,
                                        PublicKey engineKey,
                                        String sigAlgorithm) throws RemoteException {
        ((VerdictRegistry) server).registerAnalysisEngine(engineId, engineKey, sigAlgorithm);
    }

    @Override
    public void revokeAnalysisEngine(String engineId) throws RemoteException {
        ((VerdictRegistry) server).revokeAnalysisEngine(engineId);
    }

    @Override
    public void submitVerdict(String engineId, SignedVerdict verdict) throws RemoteException {
        ((VerdictRegistry) server).submitVerdict(engineId, verdict);
    }

    @Override
    public void reportCrash(CrashReport report) throws RemoteException {
        ((VerdictRegistry) server).reportCrash(report);
    }

    @Override
    public RegistryVerdict getVerdict(Set<Uri> codebaseUrls) throws RemoteException {
        return ((VerdictRegistry) server).getVerdict(codebaseUrls);
    }

    @Override
    public EventRegistration registerVerdictListener(RemoteEventListener listener,
                                                     Set<Uri> codebaseUrls,
                                                     MarshalledInstance handback,
                                                     long leaseDuration)
            throws RemoteException {
        return ((VerdictRegistry) server).registerVerdictListener(
                listener, codebaseUrls, handback, leaseDuration);
    }

    @Override
    public long renewEventLease(Uuid leaseId, long duration)
            throws UnknownLeaseException, RemoteException {
        return ((VerdictRegistry) server).renewEventLease(leaseId, duration);
    }

    @Override
    public void cancelEventLease(Uuid leaseId)
            throws UnknownLeaseException, RemoteException {
        ((VerdictRegistry) server).cancelEventLease(leaseId);
    }
}
