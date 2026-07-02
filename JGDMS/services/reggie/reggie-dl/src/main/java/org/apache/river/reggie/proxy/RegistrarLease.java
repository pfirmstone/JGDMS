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
import net.jini.core.lease.Lease;
import net.jini.core.lease.LeaseMap;
import net.jini.core.lookup.ServiceID;
import net.jini.id.ReferentUuid;
import net.jini.id.ReferentUuids;
import net.jini.id.Uuid;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
import org.apache.river.lease.AbstractLease;
import org.apache.river.lease.ID;

/**
 * The base class for lease proxies.
 *
 * @author Sun Microsystems, Inc.
 *
 */
@AtomicSerial
public abstract class RegistrarLease extends AbstractLease implements ReferentUuid, ID<Uuid> {

    private static final long serialVersionUID = 2L;
    
    public static SerialForm[] serialForm(){
        return new SerialForm[]{
            new SerialForm("server", Registrar.class),
            new SerialForm("leaseID", Uuid.class),
            new SerialForm("registrarIDMostSig", Long.TYPE),
            new SerialForm("registrarIDLeastSig", Long.TYPE)
        };
    }

    public static void serialize(PutArg arg, RegistrarLease rl) throws IOException{
        arg.put("server", rl.server);
        arg.put("leaseID", rl.leaseID);
        arg.put("registrarIDMostSig", rl.registrarID.getMostSignificantBits());
        arg.put("registrarIDLeastSig", rl.registrarID.getLeastSignificantBits());
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
     * The internal lease id.
     *
     * @serial
     */
    final Uuid leaseID;

    private static GetArg check(GetArg arg) throws IOException, ClassNotFoundException{
	Registrar server = (Registrar) arg.get("server", null);
	Uuid leaseID = (Uuid) arg.get("leaseID", null);
	if (server == null) {
	    throw new InvalidObjectException("null server");
	} else if (leaseID == null) {
	    throw new InvalidObjectException("null leaseID");
	}
	return arg;
    }

    RegistrarLease(GetArg arg) throws IOException, ClassNotFoundException{
	super(check(arg));
	server = (Registrar) arg.get("server", null);
	leaseID = (Uuid) arg.get("leaseID", null);
	registrarID = new ServiceID(
		arg.get("registrarIDMostSig", 0L),
		arg.get("registrarIDLeastSig", 0L));
    }



    /** Simple constructor. */
    RegistrarLease(Registrar server,
		   ServiceID registrarID,
		   Uuid leaseID,
		   long expiration)
    {
	super(expiration);
	this.server = server;
	this.registrarID = registrarID;
	this.leaseID = leaseID;
	this.expiration = expiration;
    }

    /** Creates a lease map. */
    @Override
    public LeaseMap<? extends Lease,Long> createLeaseMap(long duration) {
	return new RegistrarLeaseMap(this, duration);
    }

    /**
     * Two leases can be batched if they are both RegistrarLeases and
     * have the same server.
     */
    @Override
    public boolean canBatch(Lease lease) {
	return (lease instanceof RegistrarLease &&
		registrarID.equals(((RegistrarLease) lease).registrarID));
    }

    /** Returns the lease Uuid. */
    @Override
    public Uuid getReferentUuid() {
	return leaseID;
    }

    /** Returns the lease Uuid's hash code. */
    @Override
    public int hashCode() {
	return leaseID.hashCode();
    }

    /** Returns true if lease Uuids match, false otherwise. */
    @Override
    public boolean equals(Object obj) {
	return ReferentUuids.compare(this, obj);
    }

    /**
     * Returns a string created from the proxy class name, the registrar's
     * service ID, the id of the lessee or event (depending on the subclass),
     * and the result of the underlying server proxy's toString method.
     * 
     * @return String
     */
    @Override
    public String toString() {
	String className = getClass().getName();
	return className + "[registrar=" + registrarID + " " + server
	    + ", lease=" + leaseID + ", " + getLeaseType() + "=" + getRegID() 
	    + "]";
    }


    /** Returns the registrar. */
    Registrar getRegistrar() {
	return server;
    }

    /** Returns the registrar's service ID. */
    ServiceID getRegistrarID() {
	return registrarID;
    }
    
    @Override
    public Uuid identity(){
        return leaseID;
    }

    /** Returns the service ID, or the event ID as a Long. */
    abstract Object getRegID();

    /** Returns the type of the lease. */
    abstract String getLeaseType();

    void setExpiration(long expiration) {
        this.expiration = expiration;
    }
}
