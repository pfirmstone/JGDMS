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
import java.rmi.MarshalException;
import java.rmi.NoSuchObjectException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.server.RemoteObject;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.MarshallingFormat;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceItem;
import net.jini.io.MarshalledInstance;
import net.jini.security.Security;
import org.apache.river.action.GetBooleanAction;
import org.apache.river.api.io.AtomicMarshalledInstance;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
import org.apache.river.api.io.Valid;
import org.apache.river.logging.Levels;
import org.apache.river.proxy.MarshalledWrapper;
import org.apache.river.proxy.Bootstrap;

/**
 * An Item contains the fields of a ServiceItem packaged up for
 * transmission between client-side proxies and the registrar server.
 * Instances are never visible to clients, they are private to the
 * communication between the proxies and the server.
 * <p>
 * This class only has a bare minimum of methods, to minimize
 * the amount of code downloaded into clients.
 *
 * @author Sun Microsystems, Inc.
 *
 */
@AtomicSerial
public final class Item implements Cloneable {

    public static SerialForm[] serialForm(){
        return new SerialForm[]{
            /** @serialField ServiceItem.serviceID. */
            new SerialForm("serviceID", ServiceID.class),
            /** @serialField The Class of ServiceItem.service converted to ServiceType. */
            new SerialForm("serviceType", ServiceType.class),
            /** @serialField The codebase of the service object. */
            new SerialForm("codebase", String.class),
            /** @serialField ServiceItem.service as a MarshalledWrapper. */
            new SerialForm("service", MarshalledWrapper.class),
            /** @serialField ServiceItem.attributeSets converted to EntryReps. */
            new SerialForm("attributeSets", EntryRep[].class),
            /** @serialField The bootstrap {@code Proxy}, opaque -- marshalled into a
             * MarshalledWrapper (like {@code service}), never decoded by the
             * registrar. A bare {@code Proxy.class} field has no DER schema rule
             * (SchemaGenerator rejects it); MarshalledWrapper is DER-encodable
             * (already proven by {@code service}) and, being opaque, also means
             * the registrar never needs to reconstruct an arbitrary dynamic-proxy
             * class merely to accept/relay an Item -- materialization happens only
             * where the bootstrap proxy is actually consumed, always client-side
             * (see {@link #getProxy()}). */
            new SerialForm("bootstrapProxy", MarshalledWrapper.class),
            /** @serialField The registrar's born marshalling format this item's
             * attribute sets/service payload were marshalled under. */
            new SerialForm("entryFormat", MarshallingFormat.class)
        };
    }

    public static void serialize(PutArg arg, Item item) throws IOException{
        arg.put("serviceID", item.serviceID);
        arg.put("serviceType", item.serviceType);
        arg.put("codebase", item.codebase);
        arg.put("service", item.service);
        arg.put("attributeSets", item.attributeSets);
        arg.put("bootstrapProxy", item.bootstrapProxy);
        arg.put("entryFormat", item.entryFormat);
        arg.writeArgs();
    }

    /** Logger for Reggie. */
    private static final Logger logger = 
	Logger.getLogger("org.apache.river.reggie");
    
    /**
     * Flag to enable JRMP impl-to-stub replacement during marshalling of
     * service proxy.
     */
    private static final boolean enableImplToStubReplacement;
    static {
	Boolean b;
	try {
	    b = (Boolean) Security.doPrivileged(new GetBooleanAction(
		"org.apache.river.reggie.enableImplToStubReplacement"));
	} catch (SecurityException e) {
	    logger.log(Levels.HANDLED, "failed to read system property", e);
	    b = Boolean.FALSE;
	}
	enableImplToStubReplacement = b.booleanValue();
    }

    /**
     * ServiceItem.serviceID.
     *
     * @serial
     */
    private ServiceID serviceID; // mutated by RegistrarImpl
    /**
     * The Class of ServiceItem.service converted to ServiceType.
     *
     * @serial
     */
    public final ServiceType serviceType;
    /**
     * The codebase of the service object.
     *
     * @serial
     */
    public final String codebase;
    /**
     * ServiceItem.service as a MarshalledWrapper.
     *
     * @serial
     */
    public final MarshalledWrapper service;
    /**
     * ServiceItem.attributeSets converted to EntryReps.
     *
     * @serial
     */
    private EntryRep[] attributeSets; // mutated by RegistrarImpl
    
    /**
     * Bootstrap proxy for registrar default method, relayed opaque: marshalled
     * once (from a locally-owned, not wire-decoded, service object -- see
     * {@link #Item(ServiceItem, boolean)}) into a {@code MarshalledWrapper},
     * exactly like {@link #service}. The registrar never decodes this field
     * merely to accept or relay an {@code Item} -- see {@link #getProxy()}
     * and {@code RegistrarImpl.copyItem}, which passes this field through
     * unread. {@code public} (like {@link #service}) so relay code in a
     * different package can forward it without materializing it.
     * @serial
     */
    public final MarshalledWrapper bootstrapProxy;

    /**
     * The registrar's born marshalling format (JGDMS-STD-006 sec.3 item 5)
     * this item's attribute sets and service payload were marshalled
     * under. Populated at construction from the caller-supplied
     * {@code useDer} flag (itself sourced from the proxy/registrar's own
     * immutable {@code entryFormat} field, never {@code getConstraints()}),
     * or carried over unchanged when copying an existing {@code Item}.
     *
     * @serial
     */
    public final MarshallingFormat entryFormat;

    /**
     * Sentinel distinguishing "the {@code entryFormat} field is absent from
     * this stream's persistent schema" (a pre-A3 {@code Item}) from "the
     * field is present and its serialized value is {@code null}" -- see
     * {@link RegistrarProxy#resolveEntryFormat} for the full rationale.
     */
    private static final Object ENTRY_FORMAT_ABSENT = new Object();

    private static MarshallingFormat resolveEntryFormat(GetArg arg)
	    throws IOException, ClassNotFoundException
    {
	Object raw = arg.get("entryFormat", ENTRY_FORMAT_ABSENT);
	if (raw == ENTRY_FORMAT_ABSENT) return MarshallingFormat.JOSS;
	if (raw == null || raw instanceof MarshallingFormat) return (MarshallingFormat) raw;
	throw new InvalidObjectException("entryFormat field is a "
	    + raw.getClass().getName() + ", not assignable to MarshallingFormat");
    }

    /**
     * Checks all invariants are satisfied during de-serialization.
     * @param arg
     * @return
     * @throws IOException
     * @throws ClassCastException
     * @throws NullPointerException
     */
    private static boolean check(GetArg arg)
	    throws IOException, ClassNotFoundException{
	arg.get("serviceID", null, ServiceID.class);
	// serviceID is assigned by Reggie if null.
	arg.get("serviceType", null, ServiceType.class);
	// serviceType allowed to be null
	arg.get("codebase", null, String.class);
	// codebase allowed to be null
	Valid.notNull(
		arg.get("service", null, MarshalledWrapper.class),
		"service cannot be null");
	// attributeSets can be null and can contain null
	arg.get("attributeSets", null, EntryRep[].class);
	// bootstrapProxy may be null (Bootstrap.create returns null when the
	// service isn't bootstrap-eligible); opaque, so not decoded/validated
	// here -- see getProxy() for the (client-side-only) materialization
	// that validates it actually unwraps to a Proxy.
	arg.get("bootstrapProxy", null, MarshalledWrapper.class);
	if (resolveEntryFormat(arg) == null)
	    throw new InvalidObjectException("null entryFormat");
	return true;
    }

    /**
     * Failure is atomic, if any invariants aren't satisfied, construction fails
     * before an instance can be created.
     * @param arg
     * @throws IOException
     */
    public Item(GetArg arg) throws IOException, ClassNotFoundException{
	this(arg, check(arg));
    };

    private Item(GetArg arg, boolean check) throws IOException, ClassNotFoundException{
	super(); // instance has been created here.
	serviceID = arg.get("serviceID", null, ServiceID.class);
	serviceType = arg.get("serviceType", null, ServiceType.class);
	codebase = arg.get("codebase", null, String.class);
	service = arg.get("service", null, MarshalledWrapper.class);
	attributeSets = Valid.copy(arg.get("attributeSets", null, EntryRep[].class));
	bootstrapProxy = arg.get("bootstrapProxy", null, MarshalledWrapper.class);
	entryFormat = resolveEntryFormat(arg);
    }

    /**
     * Client-side-only reconstrain: invoked exclusively from
     * {@code ConstrainableRegistrarEvent#setConstraints} (a client-side
     * narrowing operation on an already-decoded event), never by the
     * registrar. Materializes the opaque bootstrap-proxy wrapper, re-applies
     * {@code constraints} to the live {@code RemoteMethodControl} proxy it
     * contains, then re-wraps the result under the same {@code entryFormat}
     * the original was marshalled under. On any failure, falls back to
     * relaying the original wrapper unchanged (matching this class's
     * general tolerant-degrade style, e.g. {@link #get()}).
     */
    private static MarshalledWrapper reconstrainBootstrapProxy(
	    MarshalledWrapper wrapper, MethodConstraints constraints, MarshallingFormat format) {
	if (wrapper == null) return null;
	try {
	    Object obj = wrapper.get();
	    if (!(obj instanceof RemoteMethodControl)) return wrapper;
	    Object reconstrained = ((RemoteMethodControl) obj).setConstraints(constraints);
	    return new MarshalledWrapper(marshal(reconstrained, format == MarshallingFormat.ATOMIC_DER));
	} catch (Throwable e) {
	    RegistrarProxy.handleException(e);
	    return wrapper;
	}
    }

    public Item(Item item, MethodConstraints constraints){
	this(item.serviceID,
	    item.serviceType,
	    item.codebase,
	    item.service,
	    item.attributeSets.clone(),
	    reconstrainBootstrapProxy(item.bootstrapProxy, constraints, item.entryFormat),
	    item.entryFormat
	);
    }

    /**
     * Converts a ServiceItem to an Item.  Any exception that results
     * is bundled up into a MarshalException.
     *
     * @param useDer if {@code true}, the service object and its attribute
     *     set entries are marshalled via DER ({@code MarshalledInstance} +
     *     {@code MarshallingFormat.ATOMIC_DER}); if {@code false}, the legacy
     *     JOSS {@code AtomicMarshalledInstance} form is used (unchanged
     *     pre-existing behavior). Callers derive this from the relevant
     *     proxy/registration's own immutable, born {@code entryFormat}
     *     field -- never from {@code getConstraints()}, since that is a
     *     client-mutable, silently-droppable value and
     *     {@code MarshalledWrapper.equals()}'s raw byte comparison means
     *     mixing formats between two sides of a match silently breaks
     *     matching.
     */
    public Item(ServiceItem item, boolean useDer) throws RemoteException {
	Object svc = item.service;
	if (enableImplToStubReplacement && svc instanceof Remote) {
	    try {
		svc = RemoteObject.toStub((Remote) svc);
		if (logger.isLoggable(Level.FINER)) {
		    logger.log(Level.FINER, "replacing {0} with {1}",
			       new Object[]{ item.service, svc });
		}
	    } catch (NoSuchObjectException e) {
	    }
	}
	//  Now we need to create the bootstrap proxy for the new lookup method.
	Proxy bp = Bootstrap.create(svc);
	serviceID = item.serviceID;
	ServiceTypeBase stb = ClassMapper.toServiceTypeBase(svc.getClass());
	serviceType = stb.type;
	codebase = stb.codebase;
	try {
	    service = new MarshalledWrapper(marshal(svc, useDer));
	    // Marshalled here (never left as a live Proxy field) so the
	    // registrar can relay bootstrapProxy opaque, exactly like
	    // service, without ever needing to reconstruct it -- see
	    // getProxy() and RegistrarImpl.copyItem.
	    bootstrapProxy = bp != null ? new MarshalledWrapper(marshal(bp, useDer)) : null;
	} catch (IOException e) {
	    throw new MarshalException("error marshalling arguments", e);
	}
	attributeSets = EntryRep.toEntryRep(item.attributeSets, true, useDer);
	entryFormat = useDer ? MarshallingFormat.ATOMIC_DER : MarshallingFormat.JOSS;
    }

    /**
     * Marshals {@code val} either via DER ({@code useDer == true}) or the legacy
     * JOSS {@code AtomicMarshalledInstance} form ({@code useDer == false}), for
     * wrapping in a {@link MarshalledWrapper} (used for both {@link #service}
     * and {@link #bootstrapProxy}).
     */
    private static MarshalledInstance marshal(Object val, boolean useDer) throws IOException {
        return useDer
            ? new MarshalledInstance(val, Collections.EMPTY_SET,
                new InvocationConstraints(MarshallingFormat.ATOMIC_DER, null))
            : new AtomicMarshalledInstance(val);
    }

    /**
     * @param entryFormat the born marshalling format {@code service}/
     *     {@code attrSets} were already marshalled under; carried over
     *     unchanged, never re-derived, by callers that copy an existing
     *     {@code Item} (e.g. {@link #clone()}).
     * @param bootstrap the opaque, still-marshalled bootstrap-proxy wrapper
     *     -- callers building a relayed copy of an existing {@code Item}
     *     (e.g. {@code RegistrarImpl.copyItem}) must pass the source
     *     {@code Item}'s {@link #bootstrapProxy} field straight through,
     *     never a materialized {@code Proxy}.
     */
    public Item(ServiceID serviceID, ServiceType serviceType, String codebase, MarshalledWrapper service, EntryRep[] attrSets, MarshalledWrapper bootstrap, MarshallingFormat entryFormat)
    {
        this.serviceID = serviceID;
        this.serviceType = serviceType;
        this.codebase = codebase;
        this.service = service;
        attributeSets = attrSets != null ? attrSets.clone() : new EntryRep[0];
	this.bootstrapProxy = bootstrap;
	this.entryFormat = entryFormat != null ? entryFormat : MarshallingFormat.JOSS;
    }

    /**
     * Convert back to a ServiceItem.  If the service object cannot be
     * constructed, it is set to null.  If an Entry cannot be constructed,
     * it is set to null.  If a field of an Entry cannot be unmarshalled,
     * it is set to null.
     */
    public ServiceItem get() {
	Object obj = null;
	try {
	    obj = service.get();
	} catch (Throwable e) {
	    RegistrarProxy.handleException(e);
	}
	synchronized (this){
	    return new ServiceItem(serviceID,
			       obj,
			       EntryRep.toEntry(attributeSets));
	}
    }
    
    public ServiceItem get(Collection context){
	Object obj = null;
	try {
	    obj = service.get(null, null, context);
	} catch (Throwable e) {
	    RegistrarProxy.handleException(e);
	}
	synchronized (this){
	    return new ServiceItem(serviceID,
			       obj,
			       EntryRep.toEntry(attributeSets));
	}
    }
    
    /**
     * Materializes the bootstrap proxy from its opaque wire wrapper.
     * Client-side consumption only ({@link RegistrarEvent}, {@link Matches
     * #getProxys()}) -- and the registrar's own direct fulfillment of the
     * {@code lookUp(Template,int)} remote method, whose contract is to hand
     * back live proxies. Server-side relay code building a new {@code Item}
     * to forward to another client must NOT call this -- it must pass
     * {@link #bootstrapProxy} through unread instead (see
     * {@code RegistrarImpl.copyItem}), so accepting/relaying an {@code Item}
     * never itself triggers reconstruction of an attacker-supplied dynamic
     * proxy class.
     */
    public Proxy getProxy() {
	if (bootstrapProxy == null) return null;
	try {
	    Object obj = bootstrapProxy.get();
	    if (obj instanceof Proxy && Proxy.isProxyClass(obj.getClass())) {
		return (Proxy) obj;
	    }
	} catch (Throwable e) {
	    RegistrarProxy.handleException(e);
	}
	return null;
    }

    /**
     * Deep clone.  This is really only needed in the server,
     * but it's very convenient to have here.
     */
    @Override
    public Object clone() {
	    EntryRep[] attrSets = (EntryRep[])attributeSets.clone();
	    for (int i = attrSets.length; --i >= 0; ) {
		attrSets[i] = (EntryRep)attrSets[i].clone();
	    }
	    return new Item(serviceID, serviceType, codebase, service, attrSets, bootstrapProxy, entryFormat);
    }

    /**
     * Converts an ArrayList of Item to an array of ServiceItem.
     */
    public static ServiceItem[] toServiceItem(List reps)
    {
	ServiceItem[] items = null;
	if (reps != null) {
	    items = new ServiceItem[reps.size()];
	    for (int i = items.length; --i >= 0; ) {
		items[i] = ((Item)reps.get(i)).get();
	    }
	}
	return items;
    }

    /**
     * @return the serviceID
     */
    public synchronized ServiceID getServiceID() {
	return serviceID;
    }

    /**
     * @param serviceID the serviceID to set
     */
    public synchronized void setServiceID(ServiceID serviceID) {
	this.serviceID = serviceID;
    }

    /**
     * @return the attributeSets
     */
    public synchronized EntryRep[] getAttributeSets() {
	return attributeSets == null ? null : attributeSets.clone();
    }
    
    public synchronized EntryRep getAttributeSetAtIndex(int i){
	return attributeSets == null ? null : attributeSets[i];
    }
    
    public synchronized int getAttributeSetsLength(){
	return attributeSets == null ? 0 : attributeSets.length;
    }

    /**
     * @param attributeSets the attributeSets to set
     */
    public synchronized void setAttributeSets(EntryRep[] attributeSets) {
	this.attributeSets = attributeSets;
    }
}
