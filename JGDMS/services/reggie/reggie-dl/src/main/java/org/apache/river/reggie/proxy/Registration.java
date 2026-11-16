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

import org.apache.river.proxy.ConstrainableProxyUtil;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.lang.reflect.Method;
import java.rmi.RemoteException;
import net.jini.core.constraint.MarshallingFormat;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.entry.Entry;
import net.jini.core.lease.Lease;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceRegistration;
import net.jini.id.ReferentUuid;
import net.jini.id.ReferentUuids;
import net.jini.id.Uuid;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;

/**
 * Implementation class for the ServiceRegistration interface.
 *
 * @author Sun Microsystems, Inc.
 *
 */
@AtomicSerial
public abstract class Registration implements ServiceRegistration, ReferentUuid
{

    public static SerialForm[] serialForm(){
        return new SerialForm[]{
            new SerialForm("server", Registrar.class),
            new SerialForm("lease", ServiceLease.class),
            new SerialForm("entryFormat", MarshallingFormat.class)
        };
    }

    public static void serialize(PutArg arg, Registration r) throws IOException{
        arg.put("server", r.server);
        arg.put("lease", r.lease);
        arg.put("entryFormat", r.entryFormat);
        arg.writeArgs();
    }

    /** Mappings between ServiceRegistration and Registrar methods */
    static final Method[] methodMappings = {
	Util.getMethod(ServiceRegistration.class, "addAttributes",
		       new Class[]{ Entry[].class }),
	Util.getMethod(Registrar.class, "addAttributes",
		       new Class[]{ ServiceID.class, Uuid.class,
				    EntryRep[].class }),

	Util.getMethod(ServiceRegistration.class, "modifyAttributes",
		       new Class[]{ Entry[].class, Entry[].class }),
	Util.getMethod(Registrar.class, "modifyAttributes",
		       new Class[]{ ServiceID.class, Uuid.class,
				    EntryRep[].class, EntryRep[].class }),

	Util.getMethod(ServiceRegistration.class, "setAttributes",
		       new Class[]{ Entry[].class }),
	Util.getMethod(Registrar.class, "setAttributes",
		       new Class[]{ ServiceID.class, Uuid.class,
				    EntryRep[].class }),
    };

    /**
     * The registrar
     *
     * @serial
     */
    final Registrar server;
    /**
     * The service lease
     *
     * @serial
     */
    final ServiceLease lease;

    /**
     * The marshalling format this registrar was born with, fixed at the
     * registrar's instantiation and immutable for its life
     * (JGDMS-STD-006 sec.3 item 5). Every attribute set this registration
     * marshals ({@link #addAttributes}/{@link #modifyAttributes}/
     * {@link #setAttributes}) uses this format -- <em>not</em> a value
     * derived from {@code ((RemoteMethodControl) this).getConstraints()}
     * (the former {@code Util.requiresDerFormat(this)} bug: a client-set,
     * wholesale-replaceable, silently-droppable value).
     *
     * @serial
     */
    final MarshallingFormat entryFormat;

//    @Override
//    public void write(PutArg arg) {
//	arg.put("server", server);
//	arg.put("lease", lease);
//    }

    /**
     * Sentinel distinguishing "the {@code entryFormat} field is absent from
     * this stream's persistent schema" from "the field is present and its
     * serialized value is {@code null}" -- see
     * {@link RegistrarProxy#resolveEntryFormat} for the full rationale;
     * mirrored here since {@code Registration} has its own independent
     * serial form.
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

    private static boolean check(GetArg arg) throws IOException, ClassNotFoundException {
	Registrar server = (Registrar) arg.get("server", null);
	if (server == null) throw new InvalidObjectException("null server");
	ServiceLease lease = (ServiceLease) arg.get("lease", null);
	if (lease == null) throw new InvalidObjectException("null lease");
	if (resolveEntryFormat(arg) == null)
	    throw new InvalidObjectException("null entryFormat");
	return true;
    }

    Registration(GetArg arg) throws IOException, ClassNotFoundException {
	this(arg, check(arg));
    }

    private Registration(GetArg arg, boolean check) throws IOException, ClassNotFoundException {
	server = (Registrar) arg.get("server", null);
	lease = (ServiceLease) arg.get("lease", null);
	entryFormat = resolveEntryFormat(arg);
    }

    /**
     * Returns Registration or ConstrainableRegistration instance, depending on
     * whether given server implements RemoteMethodControl.
     *
     * @param entryFormat the marshalling format this registrar was born
     *              with (fixed at the registrar's instantiation, immutable
     *              for its life); every attribute set this registration
     *              marshals uses this format.
     * @param initialConstraints wire-layer belt-and-braces (JGDMS-STD-006
     *              sec.3 item 5): when non-null, baked into this
     *              registration's underlying server reference as the
     *              initial client method constraints.
     */
    public static Registration getInstance(Registrar server, ServiceLease lease,
	    MarshallingFormat entryFormat, MethodConstraints initialConstraints) {
	// Always constrainable; fail closed when the server was not exported
	// with a constrainable endpoint.  The constrainable proxy is the only
	// concrete wire form (this class is abstract).
	if (!(server instanceof RemoteMethodControl)) {
	    throw new IllegalArgumentException(
		"service must be exported with a constrainable endpoint: "
		+ "server does not implement RemoteMethodControl");
	}
	return new ConstrainableRegistration(
	    server, lease, entryFormat, initialConstraints, true);
    }

    /**
     * Portable factory
     * Returns Registration or ConstrainableRegistration instance, depending on
     * whether given server implements RemoteMethodControl.
     */
    public static Object getInstance(Object server, ServiceLease lease,
	    MarshallingFormat entryFormat, MethodConstraints constraints)
	    throws InvalidObjectException {
	if (server instanceof Registrar){
	    // Always constrainable; fail closed when the server was not exported
	    // with a constrainable endpoint.  The constrainable proxy is the only
	    // concrete wire form (this class is abstract).
	    if (!(server instanceof RemoteMethodControl)) {
		throw new IllegalArgumentException(
		    "service must be exported with a constrainable endpoint: "
		    + "server does not implement RemoteMethodControl");
	    }
	    ConstrainableProxyUtil.verifyConsistentConstraints(
	    constraints, server, methodMappings);
	    return new ConstrainableRegistration(
		(Registrar)server, lease, entryFormat, constraints, false);
	}
	throw new ClassCastException("server must be an instance of Registrar");
    }

    static MethodConstraints translateConstraints(MethodConstraints constraints){
	return ConstrainableProxyUtil.translateConstraints(
		      constraints, methodMappings);
    }

    /** Constructor for use by getInstance(), ConstrainableRegistration. */
    Registration(Registrar server, ServiceLease lease, MarshallingFormat entryFormat) {
	if (entryFormat == null) throw new NullPointerException("entryFormat cannot be null");
	this.server = server;
	this.lease = lease;
	this.entryFormat = entryFormat;
    }

    // This method's javadoc is inherited from an interface of this class
    @Override
    public ServiceID getServiceID() {
	return lease.getServiceID();
    }

    // This method's javadoc is inherited from an interface of this class
    @Override
    public Lease getLease() {
	return lease;
    }

    /**
     * Returns {@code true} if this registration's born {@link #entryFormat}
     * is {@link MarshallingFormat#ATOMIC_DER}, {@code false} for legacy
     * JOSS -- derived from the immutable {@link #entryFormat} field set
     * once at construction, never from this registration's own (droppable)
     * {@code getConstraints()} (the former {@code Util.requiresDerFormat}
     * bug this replaces).
     */
    final boolean useDer() {
	return entryFormat.equals(MarshallingFormat.ATOMIC_DER);
    }

    // This method's javadoc is inherited from an interface of this class
    @Override
    public void addAttributes(Entry[] attrSets)
	throws UnknownLeaseException, RemoteException
    {
	server.addAttributes(lease.getServiceID(),
			     lease.getReferentUuid(),
			     EntryRep.toEntryRep(attrSets, true, useDer()));
    }

    // This method's javadoc is inherited from an interface of this class
    @Override
    public void modifyAttributes(Entry[] attrSetTmpls, Entry[] attrSets)
	throws UnknownLeaseException, RemoteException
    {
	boolean useDer = useDer();
	server.modifyAttributes(lease.getServiceID(),
				lease.getReferentUuid(),
				EntryRep.toEntryRep(attrSetTmpls, false, useDer),
				EntryRep.toEntryRep(attrSets, false, useDer));
    }

    // This method's javadoc is inherited from an interface of this class
    @Override
    public void setAttributes(Entry[] attrSets)
	throws UnknownLeaseException, RemoteException
    {
	server.setAttributes(lease.getServiceID(),
			     lease.getReferentUuid(),
			     EntryRep.toEntryRep(attrSets, true, useDer()));
    }

    // This method's javadoc is inherited from an interface of this class
    @Override
    public Uuid getReferentUuid() {
	return lease.getReferentUuid();
    }

    /** Returns the registration Uuid's hash code. */
    @Override
    public int hashCode() {
	return lease.getReferentUuid().hashCode();
    }

    /** Returns true if registration Uuids match, false otherwise. */
    @Override
    public boolean equals(Object obj) {
	return ReferentUuids.compare(this, obj);
    }

    /**
     * Returns a string created from the proxy class name and the result
     * of calling toString on the contained lease.
     * 
     * @return String
     */
    @Override
    public String toString() {
	return getClass().getName() + "[" + lease + "]";
    }
}
