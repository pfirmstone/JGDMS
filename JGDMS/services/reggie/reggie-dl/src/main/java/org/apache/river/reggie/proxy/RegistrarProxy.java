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
package org.apache.river.reggie.proxy;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.lang.reflect.Proxy;
import java.rmi.MarshalledObject;
import java.rmi.RemoteException;
import java.rmi.UnmarshalException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.admin.Administrable;
import net.jini.core.constraint.MarshallingFormat;
import net.jini.core.constraint.MethodConstraints;
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
import net.jini.lookup.ServiceAttributesAccessor;
import net.jini.lookup.ServiceProxyAccessor;
import net.jini.id.ReferentUuid;
import net.jini.id.ReferentUuids;
import net.jini.id.Uuid;
import net.jini.id.UuidFactory;
import net.jini.io.MarshalledInstance;
import net.jini.security.proxytrust.TrustEquivalence;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
import org.apache.river.proxy.MarshalledWrapper;

/**
 * A RegistrarProxy is a proxy for a registrar.  Clients only see instances
 * via the ServiceRegistrar, Administrable and ReferentUuid interfaces.
 *
 * @author Sun Microsystems, Inc.
 *
 */
@AtomicSerial
public abstract class RegistrarProxy
    implements ServiceRegistrar, SafeServiceRegistrar, ProxyAccessor, Administrable, ReferentUuid
{
    private static final Logger logger =
	Logger.getLogger("org.apache.river.reggie");
    
    public static SerialForm[] serialForm(){
        return new SerialForm[]{
            new SerialForm("server", Registrar.class),
            new SerialForm("registrarIDMostSig", Long.TYPE),
            new SerialForm("registrarIDLeastSig", Long.TYPE),
            new SerialForm("entryFormat", MarshallingFormat.class)
        };
    }

    public static void serialize(PutArg arg, RegistrarProxy rp) throws IOException{
        arg.put("server", rp.server);
        arg.put("registrarIDMostSig", rp.registrarID.getMostSignificantBits());
        arg.put("registrarIDLeastSig", rp.registrarID.getLeastSignificantBits());
        arg.put("entryFormat", rp.entryFormat);
        arg.writeArgs();
    }

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
     * The marshalling format this registrar was born with, fixed at the
     * registrar's instantiation and immutable for its life
     * (JGDMS-STD-006 sec.3 item 5: a registrar-wide format epoch inherited
     * uniformly by every client). Every registrant attribute set and
     * lookup template this proxy marshals uses this format -- <em>not</em>
     * a value derived from {@code ((RemoteMethodControl) this)
     * .getConstraints()}, since client-set constraints are wholesale
     * replaceable via {@code setConstraints} and would make the format
     * silently droppable, reintroducing the per-relationship optionality
     * the born-immutable model forbids (this is exactly the bug the
     * former {@code Util.requiresDerFormat(this)} call sites had).
     * Package protected so it can be read by subclasses.
     *
     * @serial
     */
    final MarshallingFormat entryFormat;

    /**
     * Returns RegistrarProxy or ConstrainableRegistrarProxy instance,
     * depending on whether given server implements RemoteMethodControl.
     *
     * @param entryFormat the marshalling format this registrar was born
     *              with (fixed at the registrar's instantiation, immutable
     *              for its life); every registrant attribute set/template
     *              this proxy marshals uses this format.
     * @param initialConstraints wire-layer belt-and-braces (JGDMS-STD-006
     *              sec.3 item 5): when non-null, baked into the proxy's
     *              underlying server reference as the initial client
     *              method constraints, so the JERI invocation layer itself
     *              enforces the required {@link MarshallingFormat} for
     *              every call this proxy makes, independent of the
     *              non-droppable {@code entryFormat} field above.
     */
    public static RegistrarProxy getInstance(Registrar server,
				      ServiceID registrarID,
				      MarshallingFormat entryFormat,
				      MethodConstraints initialConstraints)
    {
	// Always constrainable; fail closed when the server was not exported
	// with a constrainable endpoint.  The constrainable proxy is the only
	// concrete wire form (this class is abstract).
	if (!(server instanceof RemoteMethodControl)) {
	    throw new IllegalArgumentException(
		"service must be exported with a constrainable endpoint: "
		+ "server does not implement RemoteMethodControl");
	}
	return new ConstrainableRegistrarProxy(
	    server, registrarID, entryFormat, initialConstraints);
    }

    /**
     * Sentinel distinguishing "the {@code entryFormat} field is absent from
     * this stream's persistent schema" from "the field is present and its
     * serialized value is {@code null}" -- {@link GetArg}'s type-checked
     * {@code get(name, val, type)} overload conflates both into the same
     * default-return path, so the untyped {@code get(name, Object)}
     * overload is used here with a private sentinel default instead, which
     * only substitutes on true absence.
     */
    private static final Object ENTRY_FORMAT_ABSENT = new Object();

    /**
     * Resolves the born {@link #entryFormat} from a {@link GetArg} stream,
     * treating an absent field (a proxy serialized before this field
     * existed, i.e. before DER support) as implicit legacy
     * {@link MarshallingFormat#JOSS} rather than rejecting it (STD-006
     * sec.11.8 graceful degradation: an absent old-format marker means
     * implicit legacy JOSS, not a reject). A field that IS present but
     * whose serialized value is actually {@code null} is left as
     * {@code null} here -- that is corruption, and {@link #check} still
     * rejects it.
     */
    private static MarshallingFormat resolveEntryFormat(GetArg arg)
	    throws IOException, ClassNotFoundException
    {
	Object raw = arg.get("entryFormat", ENTRY_FORMAT_ABSENT);
	if (raw == ENTRY_FORMAT_ABSENT) return MarshallingFormat.JOSS;
	if (raw == null || raw instanceof MarshallingFormat) return (MarshallingFormat) raw;
	throw new InvalidObjectException("entryFormat field is a "
	    + raw.getClass().getName() + ", not assignable to MarshallingFormat");
    }

    private static boolean check(GetArg arg) throws IOException, ClassNotFoundException{
	Registrar server = (Registrar) arg.get("server", null);
	if (server == null) throw new InvalidObjectException("null server");
	if (resolveEntryFormat(arg) == null)
	    throw new InvalidObjectException("null entryFormat");
	return true;
    }

    RegistrarProxy(GetArg arg) throws IOException, ClassNotFoundException{
	this(arg, check(arg));
    }

    RegistrarProxy(GetArg arg, boolean check) throws IOException, ClassNotFoundException{
	server = (Registrar) arg.get("server", null);
	registrarID = new ServiceID(
		arg.get("registrarIDMostSig", 0L),
		arg.get("registrarIDLeastSig", 0L));
	entryFormat = resolveEntryFormat(arg);
    }

    /** Constructor for use by getInstance(), ConstrainableRegistrarProxy. */
    RegistrarProxy(Registrar server, ServiceID registrarID, MarshallingFormat entryFormat) {
	if (entryFormat == null) throw new NullPointerException("entryFormat cannot be null");
	this.server = server;
	this.registrarID = registrarID;
	this.entryFormat = entryFormat;
    }

    // Inherit javadoc
    @Override
    public Object getAdmin() throws RemoteException {
        return server.getAdmin();
    }

    /**
     * Returns {@code true} if this registrar's born {@link #entryFormat} is
     * {@link MarshallingFormat#ATOMIC_DER}, {@code false} for legacy JOSS.
     * This is the single source of truth for whether entry/template fields
     * marshalled through this proxy use DER -- derived from the immutable
     * {@link #entryFormat} field set once at construction (mirroring
     * {@link RegistrarImpl#entryFormat()} on the server side), <em>never</em>
     * from this proxy's own (droppable) {@code getConstraints()}.
     */
    final boolean useDer() {
	return entryFormat.equals(MarshallingFormat.ATOMIC_DER);
    }

    // Inherit javadoc
    @Override
    public ServiceRegistration register(ServiceItem srvItem,
					long leaseDuration)
	throws RemoteException
    {
	Item item = new Item(srvItem, useDer());
	if (item.getServiceID() != null) {
	    Util.checkRegistrantServiceID(
		    item.getServiceID(), logger, Level.WARNING);
	}
	return server.register(item, leaseDuration);
    }

    // Inherit javadoc
    @Override
    public Object lookup(ServiceTemplate tmpl) throws RemoteException {
	MarshalledWrapper wrapper = server.lookup(new Template(tmpl, useDer()));
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
	return server.lookup(new Template(tmpl, useDer()), maxMatches).get();
    }

    @Override
    public Object [] lookUp(
	    ServiceTemplate tmpl, int maxProxies) throws RemoteException
    {
	Object [] proxys = server.lookUp(new Template(tmpl, useDer()), maxProxies);
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
	return server.notify(new Template(tmpl, useDer()), transitions, listener,
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
	return server.notiFy(new Template(tmpl, useDer()), transitions, listener,
			     handback, leaseDuration);
    }

    // Inherit javadoc
    @Override
    public Class[] getEntryClasses(ServiceTemplate tmpl)
	throws RemoteException
    {
	return EntryClassBase.toClass(
				  server.getEntryClasses(new Template(tmpl, useDer())));
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
	Object[] values = server.getFieldValues(new Template(tmpl, useDer()),
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
				   server.getServiceTypes(new Template(tmpl, useDer()),
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

    @Override
    public Object getProxy() {
	return server;
    }
}
