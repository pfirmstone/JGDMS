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

import net.jini.admin.Administrable;
import com.sun.jini.admin.DestroyAdmin;

import net.jini.lookup.DiscoveryAdmin;

import net.jini.core.discovery.LookupLocator;
import net.jini.core.entry.Entry;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceItem;
import net.jini.core.lookup.ServiceMatches;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.core.lookup.ServiceRegistration;
import net.jini.core.lookup.ServiceTemplate;

import java.io.IOException;
import java.io.Serializable;

import java.lang.reflect.Field;

import java.rmi.RemoteException;
import java.rmi.UnmarshalException;
import java.rmi.MarshalledObject;

import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * This class is an interface to backend servers for simulations of activatable
 * lookup services that implement the LookupSimulator interface.
 */
public interface LookupSimulatorProxyInterface extends ServiceRegistrar,
                                             Administrable,
                                             DiscoveryAdmin, DestroyAdmin,
                                             Serializable
{
//    private static final long serialVersionUID = 977257904824022932L;


    /* LookupSimulator methods */

    public void setLocator(LookupLocator newLocator) throws RemoteException;
}
