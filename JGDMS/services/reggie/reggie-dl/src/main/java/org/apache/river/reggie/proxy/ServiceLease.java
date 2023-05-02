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
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.rmi.RemoteException;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.core.lookup.ServiceID;
import net.jini.id.Uuid;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.ReadInput;
import org.apache.river.api.io.AtomicSerial.ReadObject;
import org.apache.river.api.io.AtomicSerial.SerialForm;

/**
 * A ServiceLease is a proxy for a service registration lease at a registrar.
 * Clients only see instances via the Lease interface.
 *
 * @author Sun Microsystems, Inc.
 *
 */
@AtomicSerial
public class ServiceLease extends RegistrarLease {

    private static final long serialVersionUID = 2L;
    /** The type of the lease used in toString() calls. */
    private static final String LEASE_TYPE = "service";   
    
    public static SerialForm[] serialForm(){
        return new SerialForm[]{};
    }
    
    public static void serialize(PutArg arg, ServiceLease sl) throws IOException{
        sl.serviceID.writeBytes(arg.output());
    }

    /**
     * The service id assigned at registration.
     */
    transient ServiceID serviceID;

    /**
     * Returns ServiceLease or ConstrainableServiceLease instance, depending on
     * whether given server implements RemoteMethodControl.
     */
    public static ServiceLease getInstance(Registrar server,
				    ServiceID registrarID,
				    ServiceID serviceID,
				    Uuid leaseID,
				    long expiration)
    {
	return (server instanceof RemoteMethodControl) ?
	    new ConstrainableServiceLease(
		server, registrarID, serviceID, leaseID, expiration, null, true) :
	    new ServiceLease(
		server, registrarID, serviceID, leaseID, expiration);
    }

    @ReadInput
    private static ReadObject getRO(){
	return new RO();
    }
    
    private static GetArg check(GetArg arg){
	RO r = (RO) arg.getReader();
	if (r.serviceID == null) throw new NullPointerException();
	return arg;
    }
    
    ServiceLease(GetArg arg) throws IOException{
	super(check(arg));
	serviceID = ((RO) arg.getReader()).serviceID;
    }
    
    /** Constructor for use by getInstance(), ConstrainableServiceLease. */
    ServiceLease(Registrar server,
		 ServiceID registrarID,
		 ServiceID serviceID,
		 Uuid leaseID,
		 long expiration)
    {
	super(server, registrarID, leaseID, expiration);
	this.serviceID = serviceID;
    }

    @Override
    public void cancel() throws UnknownLeaseException, RemoteException {
	server.cancelServiceLease(serviceID, leaseID);
    }

    /** Do the actual renew. */
    @Override
    protected long doRenew(long duration)
	throws UnknownLeaseException, RemoteException
    {
	return server.renewServiceLease(serviceID, leaseID, duration);
    }

    /** Returns the service ID */
    ServiceID getServiceID() {
	return serviceID;
    }

    @Override
    Object getRegID() {
	return serviceID;
    }
    
    // This method's javadoc is inherited from a super class of this class
    @Override
    String getLeaseType() {
	return LEASE_TYPE;
    }

    /**
     * Writes the service ID, encoded as specified by the ServiceID.writeBytes
     * method.
     */
    private void writeObject(ObjectOutputStream out) throws IOException {
	out.defaultWriteObject();
	serviceID.writeBytes(out);
    }

    /**
     * Reads the service ID, encoded as specified by the ServiceID.writeBytes
     * method.
     */
    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();
	serviceID = new ServiceID(in);
    }

    /**
     * Throws InvalidObjectException, since data for this class is required.
     */
    private void readObjectNoData() throws ObjectStreamException {
	throw new InvalidObjectException("no data");
    }
    
    private static class RO implements ReadObject{
	
	ServiceID serviceID;

	@Override
	public void read(ObjectInput input) throws IOException, ClassNotFoundException {
	    serviceID = new ServiceID(input);
}
	
    }
}
