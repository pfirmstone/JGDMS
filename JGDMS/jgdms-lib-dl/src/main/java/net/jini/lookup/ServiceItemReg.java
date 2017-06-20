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

import java.util.HashMap;
import java.util.Map;
import net.jini.core.lookup.ServiceItem;
import net.jini.core.lookup.ServiceRegistrar;

/**
 * Used in the LookupCache. For each LookupCache, there is a HashMap that
 * maps ServiceId to a ServiceItemReg. The ServiceItemReg class helps track
 * where the ServiceItem comes from.
 */
final class ServiceItemReg {
    /* Maps ServiceRegistrars to their latest registered item */
    private final Map<ServiceRegistrar, ServiceItem> items;
    /* The ServiceRegistrar currently being used to track changes */
    private volatile ServiceRegistrar proxy;
    /* Flag that indicates that the ServiceItem has been discarded. */
    private volatile boolean bDiscarded;
    /* The discovered service, prior to filtering. */
    private volatile ServiceItem item;
    /* The discovered service, after filtering. */
    private volatile ServiceItem filteredItem;
    /* Creates an instance of this class, and associates it with the given
     * lookup service proxy.
     */

    public ServiceItemReg(ServiceRegistrar proxy, ServiceItem item) {
        this.bDiscarded = false;
        items = new HashMap<ServiceRegistrar, ServiceItem>();
        this.proxy = proxy;
        items.put(proxy, item);
        this.item = item;
        filteredItem = null;
    }

    /* Adds the given proxy to the 'proxy-to-item' map. This method is
     * called by the newOldService method.  Returns false if the proxy is being used
     * to track changes, true otherwise.
     */
    public boolean proxyNotUsedToTrackChange(ServiceRegistrar proxy, ServiceItem item) {
        items.put(proxy, item);
        return !proxy.equals(this.proxy);
    }

    /**
     * Replaces the proxy used to track change if the proxy passed in is non
     * null, also replaces the ServiceItem.
     *
     * @param proxy replacement proxy
     * @param item replacement item.
     */
    public void replaceProxyUsedToTrackChange(ServiceRegistrar proxy, ServiceItem item) {
        if (proxy != null) {
            this.proxy = proxy;
        }
        this.item = item;
    }
    /* Removes the given proxy from the 'proxy-to-item' map. This method
     * is called from the lookup, handleMatchNoMatch methods and
     * ProxyRegDropTask.  If this proxy was being used to track changes,
     * then pick a new one and return its current item, else return null.
     */

    public ServiceItem removeProxy(ServiceRegistrar proxy) {
        items.remove(proxy);
        if (proxy.equals(this.proxy)) {
            if (items.isEmpty()) {
                this.proxy = null;
            } else {
                Map.Entry ent = (Map.Entry) items.entrySet().iterator().next();
                this.proxy = (ServiceRegistrar) ent.getKey();
                return (ServiceItem) ent.getValue();
            } //endif
        } //endif
        return null;
    }
    /* Determines if the 'proxy-to-item' map contains any mappings.
     */

    public boolean hasNoProxys() {
        return items.isEmpty();
    }
    /* Returns the flag indicating whether the ServiceItem is discarded. */

    public boolean isDiscarded() {
        return bDiscarded;
    }

    /* Discards if not discarded and returns true if successful */
    public boolean discard() {
        if (!bDiscarded) {
            bDiscarded = true;
            return true;
        }
        return false;
    }

    /* Undiscards if discarded and returns true if successful */
    public boolean unDiscard() {
        if (bDiscarded) {
            bDiscarded = false;
            return true;
        }
        return false;
    }

    /**
     * @return the proxy
     */
    public ServiceRegistrar getProxy() {
        return proxy;
    }

    /**
     * @return the filteredItem
     */
    public ServiceItem getFilteredItem() {
        return filteredItem == null ? null : filteredItem.clone();
    }

    /**
     * @param filteredItem the filteredItem to set
     */
    public void setFilteredItem(ServiceItem filteredItem) {
        this.filteredItem = filteredItem == null ? null : filteredItem.clone();
    }

    /**
     * @return the item
     */
    public ServiceItem getItem() {
        return item;
    }
    
}
