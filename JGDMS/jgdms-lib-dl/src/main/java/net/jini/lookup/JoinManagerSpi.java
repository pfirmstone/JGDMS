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
package net.jini.lookup;

import net.jini.core.entry.Entry;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.discovery.DiscoveryManagement;
import net.jini.lease.LeaseRenewalManager;

/**
 * Service provider interface implemented by the join implementation that backs
 * the {@link JoinManager} facade.
 * <p>
 * This interface mirrors, exactly, the public method surface of
 * {@link JoinManager}. The facade holds a delegate of this type, obtained once
 * through a {@link DiscoveryProviderFactory} resolved via
 * {@link org.apache.river.resource.Service}, and forwards every public method
 * call to the delegate. The implementation lives in a separate,
 * non-downloadable jar so that the {@code net.jini.lookup} package (this
 * download jar) can be compiled for the widest range of client JVMs while the
 * implementation retains access to newer runtime facilities (for example,
 * virtual threads).
 *
 * @see JoinManager
 * @see DiscoveryProviderFactory
 * @since 3.1.1
 */
public interface JoinManagerSpi {

    /**
     * Implements {@link JoinManager#getDiscoveryManager()}.
     */
    DiscoveryManagement getDiscoveryManager();

    /**
     * Implements {@link JoinManager#getLeaseRenewalManager()}.
     */
    LeaseRenewalManager getLeaseRenewalManager();

    /**
     * Implements {@link JoinManager#getJoinSet()}.
     */
    ServiceRegistrar[] getJoinSet();

    /**
     * Implements {@link JoinManager#getAttributes()}.
     */
    Entry[] getAttributes();

    /**
     * Implements {@link JoinManager#addAttributes(Entry[])}.
     */
    void addAttributes(Entry[] attrSets);

    /**
     * Implements {@link JoinManager#addAttributes(Entry[], boolean)}.
     */
    void addAttributes(Entry[] attrSets, boolean checkSC);

    /**
     * Implements {@link JoinManager#setAttributes(Entry[])}.
     */
    void setAttributes(Entry[] attrSets);

    /**
     * Implements {@link JoinManager#modifyAttributes(Entry[], Entry[])}.
     */
    void modifyAttributes(Entry[] attrSetTemplates, Entry[] attrSets);

    /**
     * Implements {@link JoinManager#modifyAttributes(Entry[], Entry[], boolean)}.
     */
    void modifyAttributes(Entry[] attrSetTemplates,
            Entry[] attrSets,
            boolean checkSC);

    /**
     * Implements {@link JoinManager#terminate()}.
     */
    void terminate();

    /**
     * Implements {@link JoinManager#replaceRegistration(Object)}.
     */
    void replaceRegistration(Object serviceProxy);

    /**
     * Implements {@link JoinManager#replaceRegistration(Object, Entry[])}.
     */
    void replaceRegistration(Object serviceProxy, Entry[] attrSets);
}
