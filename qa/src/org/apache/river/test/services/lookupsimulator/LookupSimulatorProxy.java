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

package org.apache.river.test.services.lookupsimulator;


import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamException;
import java.rmi.MarshalledObject;
import java.rmi.RemoteException;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.discovery.LookupLocator;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceItem;
import net.jini.core.lookup.ServiceMatches;
import net.jini.core.lookup.ServiceRegistration;
import net.jini.core.lookup.ServiceTemplate;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;

/**
 * This class is a proxy to backend servers for simulations of activatable
 * lookup services that implement the LookupSimulator interface.
 */
@AtomicSerial
public class LookupSimulatorProxy implements LookupSimulatorProxyInterface {
    private static final long serialVersionUID = 977257904824022932L;

    final LookupSimulator server;
    final ServiceID serviceID;

    static LookupSimulatorProxy getInstance(LookupSimulator server, ServiceID serviceID) {
	return (server instanceof RemoteMethodControl) ?
	    new LookupSimulatorConstrainableProxy(server, serviceID, null) :
	    new LookupSimulatorProxy(server, serviceID);
    }

    /** Simple constructor. */
    public LookupSimulatorProxy(LookupSimulator server, ServiceID serviceID) {
        this.server = server;
        this.serviceID = serviceID;
    }//end constructor

    public LookupSimulatorProxy(GetArg arg)throws IOException{
	this.server = (LookupSimulator) arg.get("server", null);
	this.serviceID = (ServiceID) arg.get("serviceID", null);
	if (server == null) {
	    throw new InvalidObjectException("null server");
	} else if (serviceID == null) {
	    throw new InvalidObjectException("null serviceID");
	}
    }

    /* Administrable */
    public Object getAdmin() throws RemoteException {
        return this;
    }

    /* LookupSimulator methods */

    public void setLocator(LookupLocator newLocator) throws RemoteException {
        server.setLocator(newLocator);
    }

    /* ServiceRegistrar methods */
    public ServiceRegistration register(ServiceItem item, long leaseDuration)
                                                        throws RemoteException
    {
	return server.register(null,0);
    }

    public Object lookup(ServiceTemplate tmpl) throws RemoteException {
	return server.lookup(null);
    }

    public ServiceMatches lookup(ServiceTemplate tmpl, int maxMatches)
                                                        throws RemoteException
    {
	return server.lookup(null,0);
    }

    public EventRegistration notify(ServiceTemplate tmpl,
                                    int transitions,
                                    RemoteEventListener listener,
                                    MarshalledObject handback,
                                    long leaseDuration)	throws RemoteException
    {
	return server.notify(null,0,null,null,0);
    }

    public Class[] getEntryClasses(ServiceTemplate tmpl) throws RemoteException
    {
	return server.getEntryClasses(null);
    }

    public Object[] getFieldValues(ServiceTemplate tmpl,
				   int setIndex,
                                   String field) throws RemoteException
    {
        return server.getFieldValues(null,0,null);
    }
    public Class[] getServiceTypes(ServiceTemplate tmpl, String prefix)
                                                       throws RemoteException
    {
	return server.getServiceTypes(null,null);
    }

    public ServiceID getServiceID() {
	return serviceID;
    }

    public LookupLocator getLocator() throws RemoteException {
	return server.getLocator();
    }

    public String[] getGroups() throws RemoteException {
	return server.getMemberGroups();
    }

    /* DiscoveryAdmin methods */

    public String[] getMemberGroups() throws RemoteException {
        return server.getMemberGroups();
    }

    public void addMemberGroups(String[] groups) throws RemoteException {
        server.addMemberGroups(groups);
    }

    public void removeMemberGroups(String[] groups) throws RemoteException {
        server.removeMemberGroups(groups);
    }

    public void setMemberGroups(String[] groups) throws RemoteException {
        if(System.getProperty("org.apache.river.start.membergroups.problem")
                                                                      != null)
        {
            throw new RemoteException
                 ("Problem setting the member groups for this lookup service");
        }
        server.setMemberGroups(groups);
    }

    public int getUnicastPort() throws RemoteException {
        return server.getUnicastPort();
    }

    public void setUnicastPort(int port) throws IOException, RemoteException {
        server.setUnicastPort(port);
    }

    /* DestroyAdmin methods */

    public void destroy() throws RemoteException {
	server.destroy();
    }

    public int hashCode() {
	return serviceID.hashCode();
    }

    /** Proxies for servers with the same serviceID are considered equal. */
    public boolean equals(Object obj) {
	return (obj instanceof LookupSimulatorProxy &&
		serviceID.equals(((LookupSimulatorProxy)obj).serviceID));
    }//end equals

    /** Verifies that member fields are non-null. */
    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();
	if (server == null) {
	    throw new InvalidObjectException("null server");
	} else if (serviceID == null) {
	    throw new InvalidObjectException("null serviceID");
	}
    }

    /**
     * Throws InvalidObjectException, since data for this class is required.
     */
    private void readObjectNoData() throws ObjectStreamException {
	throw new InvalidObjectException("no data");
    }
}//end class LookupSimulatorProxy
