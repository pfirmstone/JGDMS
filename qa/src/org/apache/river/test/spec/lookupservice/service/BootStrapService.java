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

package org.apache.river.test.spec.lookupservice.service;

import java.io.IOException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.server.ExportException;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.admin.Administrable;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.core.entry.Entry;
import net.jini.core.lease.Lease;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceRegistration;
import net.jini.export.Exporter;
import net.jini.export.ProxyAccessor;
import net.jini.lookup.ServiceAttributesAccessor;
import net.jini.lookup.ServiceIDAccessor;
import net.jini.lookup.ServiceProxyAccessor;
import net.jini.jeri.AtomicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.tcp.TcpServerEndpoint;
import org.apache.river.admin.DestroyAdmin;
import org.apache.river.api.util.Startable;
import org.apache.river.config.Config;
import org.apache.river.lookup.entry.LookupAttributes;

/**
 * Service used by all "Services" to provide a bootstrap proxy for the new
 * lookUp default method and to ensure that attribute state remains consistent
 * between lookup service and service.
 * 
 * @author peter
 */
public class BootStrapService implements ServiceIDAccessor, ServiceProxyAccessor,
	ServiceAttributesAccessor, Administrable, DestroyAdmin, ServiceRegistration,
	Startable {
    
    private final ProxyAccessor serviceProxy;
    private Remote proxy;
    private ServiceRegistration serviceRegistration;
    private Entry [] lookupAttr;
    private Exporter exporter;
//    private final Configuration config;
//    private final AccessControlContext context;
    
    public BootStrapService(ProxyAccessor originalProxy, Configuration config)
    {
	serviceProxy = originalProxy;
	this.exporter = null;
	this.lookupAttr = new Entry[0];
//	this.config = config;
//	this.context = AccessController.getContext();
	try {
	    exporter = Config.getNonNullEntry(
		    config, "test", "codebaseExporter", Exporter.class,
		    new BasicJeriExporter(
			    TcpServerEndpoint.getInstance(0),
			    new AtomicILFactory(null, BootStrapService.class),
			    true,
			    true
		    )
	    );
	} catch (ConfigurationException ex) {
	    Logger.getLogger(BootStrapService.class.getName()).log(Level.SEVERE, null, ex);
	}
    }

    public ServiceRegistration setServiceRegistration(ServiceRegistration regist, Entry [] regAttr)
    {
	synchronized (this){
	    serviceRegistration = regist;
	    if (regAttr != null) lookupAttr = regAttr.clone();
	}
	return this;
    }
    
    @Override
    public ServiceID serviceID() throws IOException 
    {
	return getServiceID();
    }

    @Override
    public Object getServiceProxy() throws RemoteException 
    {
	return serviceProxy;
    }

    @Override
    public synchronized Entry[] getServiceAttributes() throws IOException 
    {
	return lookupAttr.clone();
    }

    public Object getBootStrapProxy() 
    {
	synchronized (this) {
	    return proxy;
	}
    }

    @Override
    public Object getAdmin() throws RemoteException 
    {
	return this;
    }

    @Override
    public void destroy() throws RemoteException 
    {
	synchronized(this){
	    exporter.unexport(true);
	    exporter = null;
	    proxy = null;
	}
    }

    @Override
    public ServiceID getServiceID() {
	synchronized (this){
	    return serviceRegistration != null ? 
		    serviceRegistration.getServiceID(): null;
	}
    }

    @Override
    public Lease getLease() 
    {
	synchronized (this){
	    return serviceRegistration.getLease();
	}
    }

    @Override
    public void addAttributes(Entry[] entrys) 
	    throws UnknownLeaseException, RemoteException 
    {
	ServiceRegistration sr;
	synchronized(this){
	    sr = serviceRegistration;
	}
	sr.addAttributes(entrys);
	if (entrys != null){
	    synchronized (this){
		lookupAttr = LookupAttributes.add(lookupAttr, entrys, false);
	    }
	}
    }

    @Override
    public void modifyAttributes(Entry[] entrys, Entry[] entrys1) 
	    throws UnknownLeaseException, RemoteException 
    {
	ServiceRegistration sr;
	synchronized (this){
	    sr = serviceRegistration;
	}
	sr.modifyAttributes(entrys, entrys1);
	synchronized (this){
	    lookupAttr = LookupAttributes.modify(lookupAttr, entrys, entrys1, false);
	}
    }

    @Override
    public void setAttributes(Entry[] entrys) 
	    throws UnknownLeaseException, RemoteException 
    {
	ServiceRegistration sr;
	synchronized (this){
	    sr = serviceRegistration;
	}
	sr.setAttributes(entrys);
	if (entrys != null){//Note JoinManager throws NullPointerException, but only for setAttributes, not add.
	    synchronized (this){ // only changes if above call succeeds.
		lookupAttr = entrys.clone();
	    }
	}
    }

    @Override
    public synchronized void start() throws Exception {
	proxy = exporter.export((Remote) BootStrapService.this);
    }

}
