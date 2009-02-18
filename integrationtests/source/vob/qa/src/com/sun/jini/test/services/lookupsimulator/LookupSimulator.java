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
package com.sun.jini.test.services.lookupsimulator;

import com.sun.jini.admin.DestroyAdmin;

import net.jini.lookup.DiscoveryAdmin;

import net.jini.core.discovery.LookupLocator;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceItem;
import net.jini.core.lookup.ServiceMatches;
import net.jini.core.lookup.ServiceRegistration;
import net.jini.core.lookup.ServiceTemplate;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.MarshalledObject;

import com.sun.jini.start.ServiceProxyAccessor;

/**
 * This interface defines the private protocol between client-side
 * proxies and implementations of backend servers for simulations of
 * activatable lookup services. Such simulations are useful a smart
 * proxy to a lookup service with minimal functionality is needed
 * (for example, testing).
 */
interface LookupSimulator extends Remote, 
				  DiscoveryAdmin,
				  DestroyAdmin,
				  ServiceProxyAccessor
{
    ServiceRegistration register(ServiceItem item, long duration)
                                                       throws RemoteException;
    Object lookup(ServiceTemplate tmpl) throws RemoteException;
    ServiceMatches lookup(ServiceTemplate tmpl, int maxMatches)
                                                       throws RemoteException;
    EventRegistration notify(ServiceTemplate tmpl,
			     int transitions,
			     RemoteEventListener listener,
			     MarshalledObject handback,
			     long leaseDuration) throws RemoteException;
    Class[] getEntryClasses(ServiceTemplate tmpl) throws RemoteException;
    Object[] getFieldValues(ServiceTemplate tmpl, int setIndex, String field)
                                                       throws RemoteException;
    Class[] getServiceTypes(ServiceTemplate tmpl, String prefix)
                                                       throws RemoteException;

    ServiceID getServiceID() throws RemoteException;
    LookupLocator getLocator() throws RemoteException;
    void setLocator(LookupLocator newLocator) throws RemoteException;

}//end class LookupSimulator
