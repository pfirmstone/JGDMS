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

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Provides a means to obtain a client-side proxy from a 
 * "base" service object.
 * <p>
 * This interface is typically implemented by a service implementation
 * and/or it's remote activatable reference, if any, to allow the service
 * to return the object of its choice for a client-side reference. 
 * When a non-activatable service is created,  
 * <code> NonActivatableServiceDescriptor#create(net.jini.config.Configuration)
 * NonActivatableServiceDescriptor.create()</code>, 
 * returns the result from <code>&lt;impl&gt;.getServiceProxy()</code>, 
 * if supported, where <code>&lt;impl&gt;</code> is the service implementation
 * instance.
 * When an activatable service is created,    
 * <code> SharedActivatableServiceDescriptor#create(net.jini.config.Configuration)
 * SharedActivatableServiceDescriptor.create()</code>, 
 * returns the result of <code>&lt;act_ref&gt;.getServiceProxy()</code>,
 * if supported, where <code>&lt;act_ref&gt;</code> is the service reference
 * returned by the call to
 * {@link net.jini.activation.arg.ActivationID#activate(boolean) ActivationID.activate()}.
 *
 * @author Sun Microsystems, Inc.
 *
 * @since 2.0
 *
 * <br><code>see org.apache.river.start.NonActivatableServiceDescriptor</code>
 * <br><code>see org.apache.river.start.SharedActivatableServiceDescriptor</code>
 * @see net.jini.activation.arg.ActivationID
 *
 **/
public interface ServiceProxyAccessor extends Remote {

    /**
     * Returns a proxy object for this object. This value should not be
     * <code>null</code>. 
     *
     * @return a proxy object reference
     * @throws java.rmi.RemoteException if a connection problem occurs.
     **/
     public Object getServiceProxy() throws RemoteException;
}

