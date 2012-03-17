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

package org.apache.river.api.lookup;

import java.net.URI;
import java.security.CodeSource;
import net.jini.core.entry.Entry;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceItem;

/**
 * <p>
 * ServiceItemClasspathSub is intended for client side filtering of lookup
 * service results prior to clients using a service, the lookup service
 * that implements this class, implements #getServiceItem(), so clients
 * can obtain a complete ServiceItem when required after filtering.
 * </p><p>
 * ServiceItemClasspathSub extends ServiceItem and can be used anywhere a 
 * ServiceItem is required for querying or inspecting Entry fields that are
 * resolvable from the local classpath.  If dynamically downloaded code is 
 * required, Remote or Serializable object references are not resolved, 
 * instead, such fields are set to null to avoid codebase download.
 * </p><p>
 * ServiceItemClasspathSub inherits all fields from ServiceItem.
 * </p><p>
 * Some fields in ServiceItemClasspathSub may be null or fields in Entry's may 
 * be null or even the service reference may be null, these fields would be 
 * non-null in a ServiceItem that resolves classes from dynamically downloaded 
 * code or a remote codebase.
 * </p><p>
 * The serviceID field shall be non-null always.
 * </p><p>
 * ServiceItem's toString() method will return a different result for
 * ServiceItemClasspathSub instances.
 * </p><p>
 * When required, a new ServiceItem that is unmarshalled 
 * using remote codebases and dynamically downloaded code can be obtained 
 * by calling #getServiceItem().
 * </p>
 * @author Peter Firmstone.
 */
public abstract class ServiceItemClasspathSub extends ServiceItem{
    private static final long SerialVersionUID = 1L;
    protected ServiceItemClasspathSub(ServiceID id, Entry[] unmarshalledEntries){
        super(id, (Object) null, unmarshalledEntries);
    }
    
    /* Default constructor for serializable sub class.
     */ 
    protected ServiceItemClasspathSub(){
        super(null, null, null);
    }
    /**
     * Using remote and local code as required getServiceItem returns a
     * new ServiceItem. 
     * 
     * The returned ServiceItem must not be an instance of this class.
     * 
     * @return ServiceItem, totally unmarshalled, using remote codebase resources
     * in addition to any local classpath or resources.
     */
    public abstract ServiceItem getServiceItem();
}
