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
package org.apache.river.reggie;

import org.apache.river.admin.DestroyAdmin;
import org.apache.river.proxy.ConstrainableProxyUtil;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.rmi.RemoteException;
import net.jini.admin.JoinAdmin;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.discovery.LookupLocator;
import net.jini.core.entry.Entry;
import net.jini.core.lookup.ServiceID;
import net.jini.id.ReferentUuid;
import net.jini.id.ReferentUuids;
import net.jini.id.Uuid;
import net.jini.id.UuidFactory;
import net.jini.lookup.DiscoveryAdmin;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.ReadInput;
import org.apache.river.api.io.AtomicSerial.ReadObject;

/**
 * Proxy for administering a registrar, returned from the getAdmin method of
 * the main registrar proxy.  Clients only see instances via the
 * DiscoveryAdmin, JoinAdmin, DestroyAdmin and ReferentUuid interfaces.
 *
 * @author Sun Microsystems, Inc.
 *
 */
@AtomicSerial
class AdminProxy
    implements DiscoveryAdmin, JoinAdmin, DestroyAdmin,
	       ReferentUuid, Serializable
{
    private static final long serialVersionUID = 2L;

    /** Mappings between public admin methods and Registrar methods */
    private static final Method[] methodMappings = {
	Util.getMethod(DiscoveryAdmin.class, "addMemberGroups",
		       new Class[]{ String[].class }),
	Util.getMethod(DiscoveryAdmin.class, "addMemberGroups",
		       new Class[]{ String[].class }),

	Util.getMethod(DiscoveryAdmin.class, "getMemberGroups", new Class[0]),
	Util.getMethod(DiscoveryAdmin.class, "getMemberGroups", new Class[0]),

	Util.getMethod(DiscoveryAdmin.class, "getUnicastPort", new Class[0]),
	Util.getMethod(DiscoveryAdmin.class, "getUnicastPort", new Class[0]),

	Util.getMethod(DiscoveryAdmin.class, "removeMemberGroups",
		       new Class[]{ String[].class }),
	Util.getMethod(DiscoveryAdmin.class, "removeMemberGroups",
		       new Class[]{ String[].class }),

	Util.getMethod(DiscoveryAdmin.class, "setMemberGroups",
		       new Class[]{ String[].class }),
	Util.getMethod(DiscoveryAdmin.class, "setMemberGroups",
		       new Class[]{ String[].class }),

	Util.getMethod(DiscoveryAdmin.class, "setUnicastPort",
		       new Class[]{ int.class }),
	Util.getMethod(DiscoveryAdmin.class, "setUnicastPort",
		       new Class[]{ int.class }),

	Util.getMethod(JoinAdmin.class, "addLookupAttributes",
		       new Class[]{ Entry[].class }),
	Util.getMethod(JoinAdmin.class, "addLookupAttributes",
		       new Class[]{ Entry[].class }),

	Util.getMethod(JoinAdmin.class, "addLookupGroups",
		       new Class[]{ String[].class }),
	Util.getMethod(JoinAdmin.class, "addLookupGroups",
		       new Class[]{ String[].class }),

	Util.getMethod(JoinAdmin.class, "addLookupLocators",
		       new Class[]{ LookupLocator[].class }),
	Util.getMethod(JoinAdmin.class, "addLookupLocators",
		       new Class[]{ LookupLocator[].class }),

	Util.getMethod(JoinAdmin.class, "getLookupAttributes", new Class[0]),
	Util.getMethod(JoinAdmin.class, "getLookupAttributes", new Class[0]),

	Util.getMethod(JoinAdmin.class, "getLookupGroups", new Class[0]),
	Util.getMethod(JoinAdmin.class, "getLookupGroups", new Class[0]),

	Util.getMethod(JoinAdmin.class, "getLookupLocators", new Class[0]),
	Util.getMethod(JoinAdmin.class, "getLookupLocators", new Class[0]),

	Util.getMethod(JoinAdmin.class, "modifyLookupAttributes",
		       new Class[]{ Entry[].class, Entry[].class }),
	Util.getMethod(JoinAdmin.class, "modifyLookupAttributes",
		       new Class[]{ Entry[].class, Entry[].class }),

	Util.getMethod(JoinAdmin.class, "removeLookupGroups",
		       new Class[]{ String[].class }),
	Util.getMethod(JoinAdmin.class, "removeLookupGroups",
		       new Class[]{ String[].class }),

	Util.getMethod(JoinAdmin.class, "removeLookupLocators",
		       new Class[]{ LookupLocator[].class }),
	Util.getMethod(JoinAdmin.class, "removeLookupLocators",
		       new Class[]{ LookupLocator[].class }),

	Util.getMethod(JoinAdmin.class, "setLookupGroups",
		       new Class[]{ String[].class }),
	Util.getMethod(JoinAdmin.class, "setLookupGroups",
		       new Class[]{ String[].class }),

	Util.getMethod(JoinAdmin.class, "setLookupLocators",
		       new Class[]{ LookupLocator[].class }),
	Util.getMethod(JoinAdmin.class, "setLookupLocators",
		       new Class[]{ LookupLocator[].class }),

	Util.getMethod(DestroyAdmin.class, "destroy", new Class[0]),
	Util.getMethod(DestroyAdmin.class, "destroy", new Class[0])
    };
    
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

    static MethodConstraints translateConstraints(MethodConstraints constraints){
	return ConstrainableProxyUtil.translateConstraints(
		constraints, methodMappings);
    }
    
    static void verifyConsistentConstraints(MethodConstraints constraints, Object server) 
	    throws InvalidObjectException{
	ConstrainableProxyUtil.verifyConsistentConstraints(
	    constraints, server, methodMappings);
    }
    
    @ReadInput
    static RO getRO(){
	return new RO();
    }
    
    private static boolean check(GetArg arg) throws IOException{
	Registrar server = (Registrar) arg.get("server", null);
	if (server == null) throw new NullPointerException();
	if (((RO) arg.getReader()).registrarID == null) throw new NullPointerException();
	return true;
    }

    AdminProxy(GetArg arg) throws IOException{
	this(arg, check(arg));
    }
    
    private AdminProxy(GetArg arg, boolean check) throws IOException {
	server = (Registrar) arg.get("server", null);
	registrarID = ((RO) arg.getReader()).registrarID;
    }
    
    /** Constructor for use by getInstance(), ConstrainableAdminProxy. */
    AdminProxy(Registrar server, ServiceID registrarID) {
	this.server = server;
	this.registrarID = registrarID;
    }

    // This method's javadoc is inherited from an interface of this class
    @Override
    public Entry[] getLookupAttributes() throws RemoteException {
	return server.getLookupAttributes();
    }

    // This method's javadoc is inherited from an interface of this class
    @Override
    public void addLookupAttributes(Entry[] attrSets) throws RemoteException {
	server.addLookupAttributes(attrSets);
    }

    // This method's javadoc is inherited from an interface of this class
    @Override
    public void modifyLookupAttributes(Entry[] attrSetTemplates,
				       Entry[] attrSets)
	throws RemoteException
    {
	server.modifyLookupAttributes(attrSetTemplates, attrSets);
    }

    // This method's javadoc is inherited from an interface of this class
    @Override
    public String[] getLookupGroups() throws RemoteException {
	return server.getLookupGroups();
    }

    // This method's javadoc is inherited from an interface of this class
    @Override
    public void addLookupGroups(String[] groups) throws RemoteException {
	server.addLookupGroups(groups);
    }

    // This method's javadoc is inherited from an interface of this class
    @Override
    public void removeLookupGroups(String[] groups) throws RemoteException {
	server.removeLookupGroups(groups);
    }

    // This method's javadoc is inherited from an interface of this class
    @Override
    public void setLookupGroups(String[] groups) throws RemoteException {
	server.setLookupGroups(groups);
    }

    // This method's javadoc is inherited from an interface of this class
    @Override
    public LookupLocator[] getLookupLocators() throws RemoteException {
	return server.getLookupLocators();
    }

    // This method's javadoc is inherited from an interface of this class
    @Override
    public void addLookupLocators(LookupLocator[] locators)
	throws RemoteException
    {
	server.addLookupLocators(locators);
    }

    // This method's javadoc is inherited from an interface of this class
    @Override
    public void removeLookupLocators(LookupLocator[] locators)
	throws RemoteException
    {
	server.removeLookupLocators(locators);
    }

    // This method's javadoc is inherited from an interface of this class
    @Override
    public void setLookupLocators(LookupLocator[] locators)
	throws RemoteException
    {
	server.setLookupLocators(locators);
    }

    // This method's javadoc is inherited from an interface of this class
    @Override
    public void addMemberGroups(String[] groups) throws RemoteException {
        server.addMemberGroups(groups);
    }

    // This method's javadoc is inherited from an interface of this class
    @Override
    public void removeMemberGroups(String[] groups) throws RemoteException {
        server.removeMemberGroups(groups);
    }

    // This method's javadoc is inherited from an interface of this class
    @Override
    public String[] getMemberGroups() throws RemoteException {
        return server.getMemberGroups();
    }

    // This method's javadoc is inherited from an interface of this class
    @Override
    public void setMemberGroups(String[] groups) throws RemoteException {
        server.setMemberGroups(groups);
    }

    // This method's javadoc is inherited from an interface of this class
    @Override
    public int getUnicastPort() throws RemoteException {
        return server.getUnicastPort();
    }

    // This method's javadoc is inherited from an interface of this class
    @Override
    public void setUnicastPort(int port) throws IOException, RemoteException {
        server.setUnicastPort(port);
    }

    // This method's javadoc is inherited from an interface of this class
    @Override
    public void destroy() throws RemoteException {
	server.destroy();
    }

    // This method's javadoc is inherited from an interface of this class
    @Override
    public Uuid getReferentUuid() {
	return UuidFactory.create(registrarID.getMostSignificantBits(),
				  registrarID.getLeastSignificantBits());
    }

    /** Returns service ID hash code. */
    @Override
    public int hashCode() {
	return registrarID.hashCode();
    }

    /** Proxies for servers with the same service ID are considered equal. */
    @Override
    public boolean equals(Object obj) {
	return ReferentUuids.compare(this, obj);
    }

    /**
     * Returns a string created from the proxy class name, the registrar's
     * service ID, and the result of the underlying proxy's toString method.
     * 
     * @return String
     */
    @Override
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
    
    private static class RO implements ReadObject {

	ServiceID registrarID;
	
	@Override
	public void read(ObjectInput in) throws IOException, ClassNotFoundException {
	    registrarID = new ServiceID(in);
}
	
    }
}
