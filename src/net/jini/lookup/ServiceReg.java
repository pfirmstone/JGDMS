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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.core.lookup.ServiceItem;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.entry.AbstractEntry;
import net.jini.io.MarshalledInstance;
import org.apache.river.api.io.AtomicMarshalledInstance;

/**
 * Initial replacement for ServiceItemReg, not yet ready.
 * 
 * See notes in LookupCacheImpl next to serviceIdMap for more information.
 * 
 * @author Peter Firmstone
 */
class ServiceReg {
    /* Maps ServiceRegistrars to their latest registered item */
    private final Map<ProxyReg, ServiceItem> items;
    private final ProxyReg registrar;
    private final ServiceItemContainer item;
    private final MarshalledInstance service;
    private final ServiceItemContainer filteredItem; // Not part of equality
    private final int hash;
    
    ServiceReg(ServiceRegistrar registrar, ServiceItem item, ServiceItem filteredItem){
        this.items = new HashMap<>();
        this.registrar = registrar != null ? new ProxyReg(registrar) : null;
        this.item = item != null ? new ServiceItemContainer(item): null;
        MarshalledInstance srvc = null;
        try {
            srvc = item != null? new AtomicMarshalledInstance(item.service) : null;
        } catch (IOException ex) {
            Logger.getLogger(ServiceReg.class.getName()).log(Level.SEVERE, null, ex);
        }
        service = srvc;
        this.filteredItem = filteredItem != null ? new ServiceItemContainer(filteredItem): null;
        int hash = 7;
        hash = 31 * hash + Objects.hashCode(this.registrar);
        hash = 31 * hash + Objects.hashCode(this.item);
        hash = 31 * hash + Objects.hashCode(this.service);
        this.hash = hash;
    }
    
    private ServiceReg(ProxyReg registrar, MarshalledInstance service, ServiceItemContainer item, ServiceItem filteredItem){
        this.items = new HashMap<>();
        this.registrar = registrar;
        this.item = item;
        this.service = service;
        this.filteredItem = filteredItem != null ? new ServiceItemContainer(filteredItem): null;
        int hash = 7;
        hash = 31 * hash + Objects.hashCode(this.registrar);
        hash = 31 * hash + Objects.hashCode(this.item);
        hash = 31 * hash + Objects.hashCode(this.service);
        this.hash = hash;
    }

    @Override
    public int hashCode() {
        return hash;
    }
    
    @Override
    public boolean equals(Object o){
        if (this == o) return true;
        if (!(o instanceof ServiceReg)) return false;
        ServiceReg that = (ServiceReg) o;
        if (!Objects.equals(service, that.service)) return false;
        if (!Objects.equals(registrar, that.registrar)) return false;
        return Objects.equals(item, that.item);
    }
    
    /**
     * @return the proxy
     */
    public ServiceRegistrar getProxy() {
        return registrar.getProxy();
    }

    /**
     * @return the filteredItem
     */
    public ServiceItem getFilteredItem() {
        return filteredItem.item;
    }
    
    /**
     * @param filteredItem the filteredItem to set
     */
    public ServiceReg setFilteredItem(ServiceItem filteredItem) {
        return new ServiceReg(registrar, service, item, filteredItem);
    }
    
    /**
     * @return the item
     */
    public ServiceItem getItem() {
        return item.item;
    }
    
    /**
     * For the purpose of equality, the service instance is ignored, as it may
     * not be prepared.
     */
    private static class ServiceItemContainer {
        private final ServiceItem item;
        ServiceItemContainer (ServiceItem item){
            this.item = item;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 11 * hash + Objects.hashCode(this.item.serviceID);
            if (item.attributeSets != null){
                for (int i=0, l= item.attributeSets.length; i<l; i++){
                    hash = 11 * hash + AbstractEntry.hashCode(item.attributeSets[i]);
                }
            }
            return hash;
        }
        
        @Override
        public boolean equals(Object o){
            if (this == o) return true;
            if (!(o instanceof ServiceItemContainer)) return false;
            ServiceItemContainer that = (ServiceItemContainer) o;
            if (!Objects.equals(item.serviceID, that.item.serviceID)) return false;
            if (item.attributeSets == null && that.item.attributeSets == null) return true;
            if (item.attributeSets != null && that.item.attributeSets != null){
                if (item.attributeSets.length != that.item.attributeSets.length) return false;
                for (int i=0, l=item.attributeSets.length; i<l; i++) {
                    if(!AbstractEntry.equals(item.attributeSets[i], that.item.attributeSets[i])) return false;
                }
            }
            return true;
        }
    }
}
