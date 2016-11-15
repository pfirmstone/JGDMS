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

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.lang.reflect.Proxy;
import java.rmi.MarshalledObject;
import java.rmi.RemoteException;
import java.rmi.UnmarshalException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.admin.Administrable;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.discovery.LookupLocator;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceItem;
import net.jini.core.lookup.ServiceMatches;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.core.lookup.ServiceRegistration;
import net.jini.core.lookup.ServiceTemplate;
import net.jini.lookup.SafeServiceRegistrar;
import net.jini.export.ProxyAccessor;
import net.jini.export.ServiceAttributesAccessor;
import net.jini.export.ServiceProxyAccessor;
import net.jini.id.ReferentUuid;
import net.jini.id.ReferentUuids;
import net.jini.id.Uuid;
import net.jini.id.UuidFactory;
import net.jini.io.MarshalledInstance;
import net.jini.security.proxytrust.TrustEquivalence;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.ReadInput;
import org.apache.river.api.io.AtomicSerial.ReadObject;
import org.apache.river.proxy.MarshalledWrapper;

/**
 * A RegistrarProxy is a proxy for a registrar.  Clients only see instances
 * via the ServiceRegistrar, Administrable and ReferentUuid interfaces.
 *
 * @author Sun Microsystems, Inc.
 *
 */
@AtomicSerial
class RegistrarProxy 
    implements ServiceRegistrar, SafeServiceRegistrar, ProxyAccessor, Administrable, ReferentUuid, Serializable
{
    private static final long serialVersionUID = 2L;

    private static final Logger logger = 
	Logger.getLogger("org.apache.river.reggie");

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
     * Returns RegistrarProxy or ConstrainableRegistrarProxy instance,
     * depending on whether given server implements RemoteMethodControl.
     */
    static RegistrarProxy getInstance(Registrar server,
				      ServiceID registrarID)
    {
	return (server instanceof RemoteMethodControl) ?
	    new ConstrainableRegistrarProxy(server, registrarID, null) :
	    new RegistrarProxy(server, registrarID);
    }

    @ReadInput
    private static RO getRO(){
	return new RO();
    }
    
    private static boolean check(GetArg arg) throws IOException{
	Registrar server = (Registrar) arg.get("server", null);
	if (server == null) throw new InvalidObjectException("null server");
	RO r = (RO) arg.getReader();
	if (r.registrarID == null) throw new InvalidObjectException("null ServiceID");
	return true;
    }
    
    RegistrarProxy(GetArg arg) throws IOException{
	this(arg, check(arg));
    }
    
    RegistrarProxy(GetArg arg, boolean check) throws IOException{
	server = (Registrar) arg.get("server", null);
	RO r = (RO) arg.getReader();
	registrarID = r.registrarID;
    }
    
    /** Constructor for use by getInstance(), ConstrainableRegistrarProxy. */
    RegistrarProxy(Registrar server, ServiceID registrarID) {
	this.server = server;
	this.registrarID = registrarID;
    }

    // Inherit javadoc
    @Override
    public Object getAdmin() throws RemoteException {
        return server.getAdmin();
    }

    // Inherit javadoc
    @Override
    public ServiceRegistration register(ServiceItem srvItem,
					long leaseDuration)
	throws RemoteException
    {
	Item item = new Item(srvItem);
	if (item.getServiceID() != null) {
	    Util.checkRegistrantServiceID(
		    item.getServiceID(), logger, Level.WARNING);
	}
	return server.register(item, leaseDuration);
    }

    // Inherit javadoc
    @Override
    public Object lookup(ServiceTemplate tmpl) throws RemoteException {
	MarshalledWrapper wrapper = server.lookup(new Template(tmpl));
	if (wrapper == null)
	    return null;
	try {
	    return wrapper.get();
	} catch (IOException e) {
	    throw new UnmarshalException("error unmarshalling return", e);
	} catch (ClassNotFoundException e) {
	    throw new UnmarshalException("error unmarshalling return", e);
	}
    }

    // Inherit javadoc
    @Override
    public ServiceMatches lookup(ServiceTemplate tmpl, int maxMatches)
	throws RemoteException
    {
	return server.lookup(new Template(tmpl), maxMatches).get();
    }
    
    @Override
    public Object [] lookUp(
	    ServiceTemplate tmpl, int maxProxies) throws RemoteException
    {
	Object [] proxys = server.lookUp(new Template(tmpl), maxProxies);
	List result = new ArrayList(proxys.length);
	for (int i = 0, l = proxys.length; i < l; i++){
	    if(!(proxys[i] instanceof RemoteMethodControl)) continue;
	    if(!(proxys[i] instanceof TrustEquivalence)) continue;
	    if(!(proxys[i] instanceof ServiceProxyAccessor)) continue;
	    if(!(proxys[i] instanceof ServiceAttributesAccessor)) continue;
	    if(!Proxy.isProxyClass(proxys[i].getClass())) continue;
	    result.add(proxys[i]);
	}
	return result.toArray();
    }

    // Inherit javadoc
    @Override
    public EventRegistration notify(ServiceTemplate tmpl,
				    int transitions,
				    RemoteEventListener listener,
				    MarshalledObject handback,
				    long leaseDuration)
	throws RemoteException
    {
	return server.notify(new Template(tmpl), transitions, listener,
			     handback, leaseDuration);
    }
    
    // Inherit javadoc
    @Override
    public EventRegistration notiFy(ServiceTemplate tmpl,
				    int transitions,
				    RemoteEventListener listener,
				    MarshalledInstance handback,
				    long leaseDuration)
	throws RemoteException
    {
	return server.notiFy(new Template(tmpl), transitions, listener,
			     handback, leaseDuration);
    }

    // Inherit javadoc
    @Override
    public Class[] getEntryClasses(ServiceTemplate tmpl)
	throws RemoteException
    {
	return EntryClassBase.toClass(
				  server.getEntryClasses(new Template(tmpl)));
    }

    // Inherit javadoc
    @Override
    public Object[] getFieldValues(ServiceTemplate tmpl,
				   int setIndex, String field)
	throws NoSuchFieldException, RemoteException
    {
	/* check that setIndex and field are valid, convert field to index */
	ClassMapper.EntryField[] efields =
	    ClassMapper.getFields(
			     tmpl.attributeSetTemplates[setIndex].getClass());
	int fidx;
	for (fidx = efields.length; --fidx >= 0; ) {
	    if (field.equals(efields[fidx].field.getName()))
		break;
	}
	if (fidx < 0)
	    throw new NoSuchFieldException(field);
	Object[] values = server.getFieldValues(new Template(tmpl),
						setIndex, fidx);
	/* unmarshal each value, replacing with null on exception */
	if (values != null && efields[fidx].marshal) {
	    for (int i = values.length; --i >= 0; ) {
		try {
		    values[i] = ((MarshalledWrapper) values[i]).get();
		    continue;
		} catch (Throwable e) {
		    handleException(e);
		}
		values[i] = null;
	    }
	}
	return values;
    }

    /**
     * Rethrow the exception if it is an Error, unless it is a LinkageError,
     * OutOfMemoryError, or StackOverflowError.  Otherwise print the
     * exception stack trace if debugging is enabled.
     */
    static void handleException(final Throwable e) {
	if (e instanceof Error &&
	    !(e instanceof LinkageError ||
	      e instanceof OutOfMemoryError ||
	      e instanceof StackOverflowError))
	{
	    throw (Error)e;
	}
	logger.log(Level.INFO, "unmarshalling failure", e);
    }

    // Inherit javadoc
    @Override
    public Class[] getServiceTypes(ServiceTemplate tmpl, String prefix)
	throws RemoteException
    {
	return ServiceTypeBase.toClass(
				   server.getServiceTypes(new Template(tmpl),
							  prefix));
    }

    @Override
    public ServiceID getServiceID() {
	return registrarID;
    }

    // Inherit javadoc
    @Override
    public LookupLocator getLocator() throws RemoteException {
	return server.getLocator();
    }

    // Inherit javadoc
    @Override
    public String[] getGroups() throws RemoteException {
	return server.getMemberGroups();
    }

    // Inherit javadoc
    @Override
    public Uuid getReferentUuid() {
	return UuidFactory.create(registrarID.getMostSignificantBits(),
				  registrarID.getLeastSignificantBits());
    }

    // Inherit javadoc
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
	return this.getClass().getName() + "[registrar=" + registrarID
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

    @Override
    public Object getProxy() {
	return server;
    }
    
    private static class RO implements ReadObject{
	
	ServiceID registrarID;
	    
	@Override
	public void read(ObjectInput in) throws IOException, ClassNotFoundException {
	    registrarID = new ServiceID(in);
}
	
    }
}
