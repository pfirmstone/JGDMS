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
package com.sun.jini.test.share.reggie;

import net.jini.core.lookup.*;
import net.jini.core.event.*;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.core.discovery.LookupLocator;
import net.jini.core.entry.Entry;
import java.io.IOException;
import java.io.Serializable;
import java.rmi.RemoteException;
import java.rmi.UnmarshalException;
import java.rmi.MarshalledObject;
import java.lang.reflect.Field;

/**
 * A RegistrarAdminProxy is a proxy for a registrar.  Clients only see
 * instances via the RegistrarAdmin interface.
 *
 * 
 *
 */
class RegistrarAdminProxy implements RegistrarAdmin, Serializable {

    private static final long serialVersionUID = -9209068398322115525L;

    /**
     * The registrar
     *
     * @serial
     */
    private final Registrar server;
    /**
     * The registrar's service ID
     *
     * @serial
     */
    private final ServiceID serviceID;

    /** Simple constructor. */
    public RegistrarAdminProxy(Registrar server, ServiceID serviceID) {
	this.server = server;
	this.serviceID = serviceID;
    }

    // This method's javadoc is inherited from an interface of this class
    public Entry[] getLookupAttributes() throws RemoteException {
	return server.getLookupAttributes();
    }

    // This method's javadoc is inherited from an interface of this class
    public void addLookupAttributes(Entry[] attrSets) throws RemoteException {
	server.addLookupAttributes(attrSets);
    }

    // This method's javadoc is inherited from an interface of this class
    public void modifyLookupAttributes(Entry[] attrSetTemplates,
				       Entry[] attrSets)
	throws RemoteException
    {
	server.modifyLookupAttributes(attrSetTemplates, attrSets);
    }

    // This method's javadoc is inherited from an interface of this class
    public String[] getLookupGroups() throws RemoteException {
	return server.getLookupGroups();
    }

    // This method's javadoc is inherited from an interface of this class
    public void addLookupGroups(String[] groups) throws RemoteException {
	server.addLookupGroups(groups);
    }

    // This method's javadoc is inherited from an interface of this class
    public void removeLookupGroups(String[] groups) throws RemoteException {
	server.removeLookupGroups(groups);
    }

    // This method's javadoc is inherited from an interface of this class
    public void setLookupGroups(String[] groups) throws RemoteException {
	server.setLookupGroups(groups);
    }

    // This method's javadoc is inherited from an interface of this class
    public LookupLocator[] getLookupLocators() throws RemoteException {
	return server.getLookupLocators();
    }

    // This method's javadoc is inherited from an interface of this class
    public void addLookupLocators(LookupLocator[] locators)
	throws RemoteException
    {
	server.addLookupLocators(locators);
    }

    // This method's javadoc is inherited from an interface of this class
    public void removeLookupLocators(LookupLocator[] locators)
	throws RemoteException
    {
	server.removeLookupLocators(locators);
    }

    // This method's javadoc is inherited from an interface of this class
    public void setLookupLocators(LookupLocator[] locators)
	throws RemoteException
    {
	server.setLookupLocators(locators);
    }

    // This method's javadoc is inherited from an interface of this class
    public void addMemberGroups(String[] groups) throws RemoteException {
        server.addMemberGroups(groups);
    }

    // This method's javadoc is inherited from an interface of this class
    public void removeMemberGroups(String[] groups) throws RemoteException {
        server.removeMemberGroups(groups);
    }

    // This method's javadoc is inherited from an interface of this class
    public String[] getMemberGroups() throws RemoteException {
        return server.getMemberGroups();
    }

    // This method's javadoc is inherited from an interface of this class
    public void setMemberGroups(String[] groups) throws RemoteException {
        server.setMemberGroups(groups);
    }

    // This method's javadoc is inherited from an interface of this class
    public int getUnicastPort() throws RemoteException {
        return server.getUnicastPort();
    }

    // This method's javadoc is inherited from an interface of this class
    public void setUnicastPort(int port) throws IOException, RemoteException
    {
        server.setUnicastPort(port);
    }

    // This method's javadoc is inherited from an interface of this class
    public void setMinMaxServiceLease(long leaseDuration)
	throws RemoteException
    {
        server.setMinMaxServiceLease(leaseDuration);
    }

    // This method's javadoc is inherited from an interface of this class
    public long getMinMaxServiceLease() throws RemoteException {
        return server.getMinMaxServiceLease();
    }

    // This method's javadoc is inherited from an interface of this class
    public void setMinMaxEventLease(long leaseDuration) throws RemoteException
    {
        server.setMinMaxEventLease(leaseDuration);
    }

    // This method's javadoc is inherited from an interface of this class
    public long getMinMaxEventLease() throws RemoteException {
        return server.getMinMaxEventLease();
    }

    // This method's javadoc is inherited from an interface of this class
    public void setMinRenewalInterval(long interval) throws RemoteException {
        server.setMinRenewalInterval(interval);
    }

    // This method's javadoc is inherited from an interface of this class
    public long getMinRenewalInterval() throws RemoteException {
        return server.getMinRenewalInterval();
    }

    // This method's javadoc is inherited from an interface of this class
    public void setSnapshotWeight(float weight) throws RemoteException {
        server.setSnapshotWeight(weight);
    }

    // This method's javadoc is inherited from an interface of this class
    public float getSnapshotWeight() throws RemoteException {
        return server.getSnapshotWeight();
    }

    // This method's javadoc is inherited from an interface of this class
    public void setLogToSnapshotThreshold(int threshold) throws RemoteException
    {
        server.setLogToSnapshotThreshold(threshold);
    }

    // This method's javadoc is inherited from an interface of this class
    public int getLogToSnapshotThreshold() throws RemoteException {
        return server.getLogToSnapshotThreshold();
    }
    //BDJ
//      // This method's javadoc is inherited from an interface of this class
//      public String getStorageLocation() throws RemoteException {
//          return server.getStorageLocation();
//      }

//      // This method's javadoc is inherited from an interface of this class
//      public void setStorageLocation(String location)
//                                     throws IOException, RemoteException {
//          server.setStorageLocation(location);
//      }

    // This method's javadoc is inherited from an interface of this class
    public void destroy() throws RemoteException {
	server.destroy();
    }

    /** Returns the hash code generated by the hashCode method of the
     *  service ID associated with an instance of this class.
     */
    public int hashCode() {
	return serviceID.hashCode();
    }

    /** Proxies for servers with the same serviceID are considered equal. */
    public boolean equals(Object obj) {
	return (obj instanceof RegistrarAdminProxy &&
		serviceID.equals(((RegistrarAdminProxy)obj).serviceID));
    }
}
