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

import net.jini.core.lookup.ServiceRegistrar;

/**
 * A wrapper class for a ServiceRegistrar.
 */
final class ProxyReg {
    private final ServiceRegistrar proxy;
    private final int hash;

    public ProxyReg(ServiceRegistrar proxy) {
	if (proxy == null) {
	    throw new IllegalArgumentException("proxy cannot be null");
	}
	this.proxy = proxy;
	hash = proxy.hashCode();
    } //end constructor

    @Override
    public boolean equals(Object obj) {
	if (obj instanceof ProxyReg) {
	    return getProxy().equals(((ProxyReg) obj).getProxy());
	} else {
	    return false;
	}
    } //end equals

    @Override
    public int hashCode() {
	return hash;
    } //end hashCode

    /**
     * @return the proxy
     */
    public ServiceRegistrar getProxy() {
	return proxy;
    }
    
} //end class ServiceDiscoveryManager.ProxyReg
