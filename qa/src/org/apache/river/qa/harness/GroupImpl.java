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

package org.apache.river.qa.harness;

import org.apache.river.start.NonActivatableServiceDescriptor;
import java.rmi.RemoteException;
import java.rmi.server.ExportException;
import java.util.ArrayList;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.config.ConfigurationProvider;
import net.jini.export.Exporter;
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.tcp.TcpServerEndpoint;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;

/**
 * The implementation of NonActivatableGroup.
 */
class GroupImpl implements NonActivatableGroup {
    
    /**
     * Note the selection of cipher suite provider must be determined at the
     * server end, so the client can use a compatible provider.
     */
   static {
//	java.security.Security.addProvider(new BouncyCastleProvider());
//	java.security.Security.addProvider(new BouncyCastleJsseProvider());
//	java.security.Security.setProperty("ssl.KeyManagerFactory.algorithm", "PKIX");
   }
    /** the proxy resulting from exporting the group */
    private Object proxy;
    /** the groups exporter */
    private final Exporter exporter;
    /** store service references here to ensure no GC interference */
    private final ArrayList serviceList;

    /**
     * Construct a <code>NonActivatableGroup</code>. Instances export themselves
     * at construction time using a <code>BasicJeriExporter</code>.
     */
    public GroupImpl() {
        this(new BasicJeriExporter(TcpServerEndpoint.getInstance(0), new BasicILFactory()));
    }

    private GroupImpl(Exporter exporter) {
        this.serviceList = new ArrayList();
        this.exporter = exporter;
    }

    void export() {
        try {
            synchronized (this) {
                proxy = exporter.export(this);
            }
        } catch (ExportException e) {
            e.printStackTrace();
            try {
                Thread.sleep(5000);
            } catch (Exception e2) {
            }
            throw new RuntimeException("Export of group failed", e);
        }
    }

    /**
     * Return the proxy for the NonActivatableGroup remote object
     */
    synchronized Object getProxy() {
        return proxy;
    }

    /**
     * Stop the group. A thread is started to perform the destroy.
     *
     * @throws RemoteException never
     */
    @Override
    public void stop() throws RemoteException {
        new DestroyThread(exporter).start();
    }

    /**
     * Start a service. A NonActivatableServiceDescriptor is constructed
     * using the given codebase, policyFile, classpath, serviceImpl,
     * and configArgs. The descriptors create method is called passing
     * a configuration obtained using the given starterConfigName.
     * The proxy is extracted from the returned Created object and
     * returned.
     *
     * @param codebase the service codebase
     * @param policyFile the service policy file
     * @param classpath the service classpath
     * @param serviceImpl the service implementation class name
     * @param configArgs the configuration arguments passed to the service
     * @param starterConfigName the name of the starter configuration file
     *
     * @return the proxy of the started service
     * @throws RemoteException if the service cannot be started
     */
    @Override
    public Object startService(String codebase, String policyFile, String classpath, String serviceImpl, String[] configArgs, String starterConfigName, ServiceDescriptorTransformer transformer) throws RemoteException {
        NonActivatableServiceDescriptor desc = new NonActivatableServiceDescriptor(codebase, policyFile, classpath, serviceImpl, configArgs);
        Configuration starterConfig = null;
        if (starterConfigName != null) {
            try {
                starterConfig = ConfigurationProvider.getInstance(new String[]{starterConfigName});
            } catch (ConfigurationException e) {
                throw new RemoteException("Starter configuration problem", e);
            }
        }
        if (transformer != null) {
            desc = (NonActivatableServiceDescriptor) transformer.transform(desc);
        }
        try {
            NonActivatableServiceDescriptor.Created created = (NonActivatableServiceDescriptor.Created) desc.create(starterConfig);
            synchronized (this) {
                serviceList.add(created);
            }
            return created.proxy;
        } catch (Exception e) {
            throw new RemoteException("Create failed", e);
        }
    }
    
}
