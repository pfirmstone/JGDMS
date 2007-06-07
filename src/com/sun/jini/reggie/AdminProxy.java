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
package com.sun.jini.reggie;

import com.sun.jini.admin.DestroyAdmin;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.rmi.RemoteException;
import net.jini.admin.JoinAdmin;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.discovery.LookupLocator;
import net.jini.core.entry.Entry;
import net.jini.core.lookup.ServiceID;
import net.jini.id.ReferentUuid;
import net.jini.id.ReferentUuids;
import net.jini.id.Uuid;
import net.jini.id.UuidFactory;
import net.jini.lookup.DiscoveryAdmin;

/**
 * Proxy for administering a registrar, returned from the getAdmin method of
 * the main registrar proxy.  Clients only see instances via the
 * DiscoveryAdmin, JoinAdmin, DestroyAdmin and ReferentUuid interfaces.
 *
 * @author Sun Microsystems, Inc.
 *
 */
class AdminProxy
    implements DiscoveryAdmin, JoinAdmin, DestroyAdmin,
	       ReferentUuid, Serializable
{
    private static final long serialVersionUID = 2L;

    /**
     * The registrar.
     *
     * @serial
     */
    final Registrar server;
    /**
     * The registrar's service ID.
     */
    transient ServiceID registrarID;

    /**
     * Returns AdminProxy or ConstrainableAdminProxy instance, depending on
     * whether given server implements RemoteMethodControl.
     */
    static AdminProxy getInstance(Registrar server, ServiceID registrarID) {
	return (server instanceof RemoteMethodControl) ?
	    new ConstrainableAdminProxy(server, registrarID, null) :
	    new AdminProxy(server, registrarID);
    }

    /** Constructor for use by getInstance(), ConstrainableAdminProxy. */
    AdminProxy(Registrar server, ServiceID registrarID) {
	this.server = server;
	this.registrarID = registrarID;
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
    public void setUnicastPort(int port) throws IOException, RemoteException {
        server.setUnicastPort(port);
    }

    // This method's javadoc is inherited from an interface of this class
    public void destroy() throws RemoteException {
	server.destroy();
    }

    // This method's javadoc is inherited from an interface of this class
    public Uuid getReferentUuid() {
	return UuidFactory.create(registrarID.getMostSignificantBits(),
				  registrarID.getLeastSignificantBits());
    }

    /** Returns service ID hash code. */
    public int hashCode() {
	return registrarID.hashCode();
    }

    /** Proxies for servers with the same service ID are considered equal. */
    public boolean equals(Object obj) {
	return ReferentUuids.compare(this, obj);
    }

    /**
     * Returns a string created from the proxy class name, the registrar's
     * service ID, and the result of the underlying proxy's toString method.
     * 
     * @return String
     */
    public String toString() {
	return getClass().getName() + "[registrar=" + registrarID
	    + " " + server + "]";
    }

    /**
     * Writes the default serializable field value for this instance, followed
     * by the registrar's service ID encoded as specified by the
     * ServiceID.writeBytes method.
     */
    private void writeObject(ObjectOutputStream out) throws IOException {
	out.defaultWriteObject();
	registrarID.writeBytes(out);
    }

    /**
     * Reads the default serializable field value for this instance, followed
     * by the registrar's service ID encoded as specified by the
     * ServiceID.writeBytes method.  Verifies that the deserialized registrar
     * reference is non-null.
     */
    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();
	registrarID = new ServiceID(in);
	if (server == null) {
	    throw new InvalidObjectException("null server");
	}
    }

    /**
     * Throws InvalidObjectException, since data for this class is required.
     */
    private void readObjectNoData() throws ObjectStreamException {
	throw new InvalidObjectException("no data");
    }
}
