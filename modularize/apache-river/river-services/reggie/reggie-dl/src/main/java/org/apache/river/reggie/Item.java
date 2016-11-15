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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamField;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.rmi.MarshalException;
import java.rmi.NoSuchObjectException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.server.RemoteObject;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.constraint.BasicMethodConstraints;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceItem;
import net.jini.export.ProxyAccessor;
import net.jini.export.ServiceAttributesAccessor;
import net.jini.export.ServiceCodebaseAccessor;
import net.jini.export.ServiceIDAccessor;
import net.jini.export.ServiceProxyAccessor;
import net.jini.security.Security;
import net.jini.security.proxytrust.TrustEquivalence;
import org.apache.river.action.GetBooleanAction;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.Valid;
import org.apache.river.logging.Levels;
import org.apache.river.proxy.MarshalledWrapper;

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
final class Item implements Serializable, Cloneable {

    private static final long serialVersionUID = 2L;
    private static final ObjectStreamField[] serialPersistentFields = 
    { 
        /** @serialField ServiceItem.serviceID. */
        new ObjectStreamField("serviceID", ServiceID.class),
        /** @serialField The Class of ServiceItem.service converted to ServiceType. */
        new ObjectStreamField("serviceType", ServiceType.class),
	/** @serialField The codebase of the service object. */
        new ObjectStreamField("codebase", String.class),
	/** @serialField ServiceItem.service as a MarshalledWrapper. */
        new ObjectStreamField("service", MarshalledWrapper.class),
	/** @serialField ServiceItem.attributeSets converted to EntryReps. */
        new ObjectStreamField("attributeSets", EntryRep[].class),
	/** @serialField ServiceItem.attributeSets converted to EntryReps. */
        new ObjectStreamField("bootstrapProxy", Proxy.class)   
    };

    /** Logger for Reggie. */
    private static final Logger logger = 
	Logger.getLogger("org.apache.river.reggie");
    
    /**
     * The bootstrap proxy must be limited to the following interfaces, in case
     * additional interfaces implemented by the proxy aren't available remotely.
     */
    private static final Class[] bootStrapProxyInterfaces = 
	{
	    ServiceProxyAccessor.class, 
	    ServiceAttributesAccessor.class,
	    ServiceIDAccessor.class,
	    RemoteMethodControl.class,
	    TrustEquivalence.class
	};
    
    /**
     * The bootstrap proxy for smart proxy's must be limited to the following
     * interfaces, in case additional interfaces implemented by the proxy
     * aren't available remotely.
     */
    private static final Class[] bootStrapSmartProxyInterfaces = 
	{
	    ServiceCodebaseAccessor.class,
	    ServiceProxyAccessor.class, 
	    ServiceAttributesAccessor.class,
	    ServiceIDAccessor.class,
	    RemoteMethodControl.class,
	    TrustEquivalence.class
	};
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
     * At this stage we're not concerned about client constraints, just the
     * servers.
     */
    private static final Collection context = Collections.singleton(
	new BasicMethodConstraints(
		new InvocationConstraints((InvocationConstraint []) null, null))
    );
    
    /**
     * Returns the class loader for the specified proxy class.
     */
    private static ClassLoader getProxyLoader(final Class proxyClass) {
	return (ClassLoader)
	    AccessController.doPrivileged(new PrivilegedAction() {
		public Object run() {
		    return proxyClass.getClassLoader();
		}
	    });
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
    final ServiceType serviceType;
    /**
     * The codebase of the service object.
     *
     * @serial
     */
    final String codebase;
    /**
     * ServiceItem.service as a MarshalledWrapper.
     *
     * @serial
     */
    final MarshalledWrapper service;
    /**
     * ServiceItem.attributeSets converted to EntryReps.
     *
     * @serial
     */
    private EntryRep[] attributeSets; // mutated by RegistrarImpl
    
    /**
     * Bootstrap proxy for registrar default method.
     * @serial
     */
    Proxy bootstrapProxy;
     
    /**
     * Checks all invariants are satisfied during de-serialization.
     * @param arg
     * @return
     * @throws IOException 
     * @throws ClassCastException
     * @throws NullPointerException
     */
    private static boolean check(GetArg arg) throws IOException{
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
	Proxy bootstrapProxy = arg.get("bootstrapProxy", null, Proxy.class);
	if (bootstrapProxy != null) {
	    if (Proxy.isProxyClass(bootstrapProxy.getClass())) return true;
	}
	return true;
    }
    
    /**
     * Failure is atomic, if any invariants aren't satisfied, construction fails
     * before an instance can be created.
     * @param arg
     * @throws IOException 
     */
    public Item(GetArg arg) throws IOException{
	this(arg, check(arg));
    };
    
    private Item(GetArg arg, boolean check) throws IOException{
	super(); // instance has been created here.
	serviceID = arg.get("serviceID", null, ServiceID.class);
	serviceType = arg.get("serviceType", null, ServiceType.class);
	codebase = arg.get("codebase", null, String.class);
	service = arg.get("service", null, MarshalledWrapper.class);
	attributeSets = Valid.copy(arg.get("attributeSets", null, EntryRep[].class));
	bootstrapProxy = arg.get("bootstrapProxy", null, Proxy.class);
    }

    /**
     * Converts a ServiceItem to an Item.  Any exception that results
     * is bundled up into a MarshalException.
     */
    public Item(ServiceItem item) throws RemoteException {
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
	Object proxy;
	if (svc instanceof ProxyAccessor){ // Obtain exported proxy
	    proxy = ((ProxyAccessor)svc).getProxy();
	} else {
	    proxy = svc;
	    // Proxy may not have been exported yet, it is legal for
	    // a Serializable object to use writeReplace to export and serialize a proxy.
	    // This occurs locally, so we don't require a codebase download.
	    // REMIND: Should we set the context ClassLoader?
	    if (!Proxy.isProxyClass(proxy.getClass()) && proxy instanceof Serializable){
		try {
		    ByteArrayOutputStream baos = new ByteArrayOutputStream();
		    ObjectOutput out = new ObjectOutputStream(baos);
		    out.writeObject(proxy);
		    ByteArrayInputStream bain = new ByteArrayInputStream(baos.toByteArray());
		    ObjectInput in = new ObjectInputStream(bain);
		    proxy = in.readObject();
		} catch (IOException ex) {
		    Logger.getLogger(Item.class.getName()).log(Level.SEVERE, null, ex);
		} catch (ClassNotFoundException ex) {
		    Logger.getLogger(Item.class.getName()).log(Level.SEVERE, null, ex);
		}
	    }
	}
	Class proxyClass = proxy.getClass();
	if (proxy instanceof RemoteMethodControl //JERI
	    && proxy instanceof TrustEquivalence //JERI
	    // REMIND: The next three interfaces may not be available locally.
	    // so must be found in jsk-dl.jar
	    && proxy instanceof ServiceIDAccessor
	    && proxy instanceof ServiceProxyAccessor
	    && proxy instanceof ServiceAttributesAccessor
	    && Proxy.isProxyClass(proxyClass)
	    ) 
	{
	    // REMIND: InvocationHandler must be available locally, for now
	    // it must be an instance of BasicInvocationHandler.
	    InvocationHandler h = Proxy.getInvocationHandler(proxy);
	    if (proxy instanceof ServiceCodebaseAccessor){
		bootstrapProxy = (Proxy) 
		    Proxy.newProxyInstance(getProxyLoader(proxyClass), bootStrapSmartProxyInterfaces, h);
	    } else {
		bootstrapProxy = (Proxy) 
		    Proxy.newProxyInstance(getProxyLoader(proxyClass), bootStrapProxyInterfaces, h);		    
	    }
	}
	serviceID = item.serviceID;
	ServiceTypeBase stb = ClassMapper.toServiceTypeBase(svc.getClass());
	serviceType = stb.type;
	codebase = stb.codebase;
	try {
	    service = new MarshalledWrapper(svc);
	} catch (IOException e) {
	    throw new MarshalException("error marshalling arguments", e);
	}
	attributeSets = EntryRep.toEntryRep(item.attributeSets, true);
    }
    
    Item(ServiceID serviceID, ServiceType serviceType, String codebase, MarshalledWrapper service, EntryRep[] attrSets, Proxy bootstrap)
    {
        this.serviceID = serviceID;
        this.serviceType = serviceType;
        this.codebase = codebase;
        this.service = service;
        attributeSets = attrSets;
	this.bootstrapProxy = bootstrap;
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
    
    public Proxy getProxy() {
	return bootstrapProxy;
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
	    return new Item(serviceID, serviceType, codebase, service, attrSets, null);
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
    
    private synchronized void writeObject(ObjectOutputStream out) throws IOException {
	out.defaultWriteObject();
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

    /**
     * @param attributeSets the attributeSets to set
     */
    public synchronized void setAttributeSets(EntryRep[] attributeSets) {
	this.attributeSets = attributeSets;
    }
}
