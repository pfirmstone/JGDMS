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
package org.apache.river.extra.discovery;

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.logging.Logger;

import net.jini.core.lookup.ServiceItem;
import net.jini.core.lookup.ServiceTemplate;
import net.jini.discovery.DiscoveryManagement;
import net.jini.discovery.LookupDiscovery;
import net.jini.lease.LeaseRenewalManager;
import net.jini.lookup.ServiceDiscoveryManager;
import net.jini.lookup.ServiceItemFilter;

/**
 * Implementation which uses multicast to find all the lookup services in the
 * local subnet.  Discovery of new/replacement services is then handed to the
 * internal <code>ServiceDiscoveryManager</code>.
 * 
 */
public class MulticastServiceFinder implements ServiceFinder {

    private static final Logger logger = Logger.getLogger(MulticastServiceFinder.class.getSimpleName());

    private static final long SETTLE_DOWN_WAIT_TIME = 500;

    private final ServiceDiscoveryManager serviceDiscovery;

    /**
     * Searches for lookup services in all lookup groups and uses a default
     * <code>LeaseRenewalManager</code>
     * 
     * @throws IOException
     */
    public MulticastServiceFinder() throws IOException {
        this(LookupDiscovery.ALL_GROUPS);
    }

    /**
     * Searches for lookup services in the specified lookup groups and uses a
     * default <code>LeaseRenewalManager</code>
     * 
     * @param lookupGroups
     * @throws IOException
     */
    public MulticastServiceFinder(final String[] lookupGroups) throws IOException {
        this(new LookupDiscovery(lookupGroups), new LeaseRenewalManager());
    }

    public MulticastServiceFinder(final DiscoveryManagement dm, final LeaseRenewalManager lrm) throws IOException {
        this.serviceDiscovery = new ServiceDiscoveryManager(dm, lrm);
        try {
            Thread.sleep(SETTLE_DOWN_WAIT_TIME);
        } catch (InterruptedException ie) {}
    }

    /**
     * Locates a new service using a default <code>ServiceItemFilter</code>
     * 
     * @see #findNewService(net.jini.core.lookup.ServiceTemplate, net.jini.lookup.ServiceItemFilter)
     */
    public Object findNewService(final ServiceTemplate template) throws RemoteException {
        return findNewService(template, new ServiceItemFilter() {
            public boolean check(final ServiceItem item) {
                return true;
            }
        });
    }

    /**
     * Locates a new service which matches the supplied filter.
     * 
     * @param template
     * @param filter
     * @return
     * @throws RemoteException if no valid service can be found
     */
    public Object findNewService(final ServiceTemplate template, final ServiceItemFilter filter) throws RemoteException {
        ServiceItem[] services = this.serviceDiscovery.lookup(template, 1, filter);

        if(null == services || 0 == services.length) {
            throw new RemoteException("Cannot find valid service");
        }

        return services[0].service;
    }

    /**
     * Terminates the multicast listeners etc.
     */
    public void terminate() {
        logger.info("Terminating service finder");
        this.serviceDiscovery.terminate();
    }
}