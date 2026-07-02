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
import java.rmi.RemoteException;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.core.lookup.ServiceID;
import net.jini.id.Uuid;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;

/**
 * A ServiceLease is a proxy for a service registration lease at a registrar.
 * Clients only see instances via the Lease interface.
 *
 * @author Sun Microsystems, Inc.
 *
 */
@AtomicSerial
public abstract class ServiceLease extends RegistrarLease {

    private static final long serialVersionUID = 2L;
    /** The type of the lease used in toString() calls. */
    private static final String LEASE_TYPE = "service";   
    
    public static SerialForm[] serialForm(){
        return new SerialForm[]{
            new SerialForm("serviceIDMostSig", Long.TYPE),
            new SerialForm("serviceIDLeastSig", Long.TYPE)
        };
    }

    public static void serialize(PutArg arg, ServiceLease sl) throws IOException{
        arg.put("serviceIDMostSig", sl.serviceID.getMostSignificantBits());
        arg.put("serviceIDLeastSig", sl.serviceID.getLeastSignificantBits());
        arg.writeArgs();
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
	// Always constrainable; fail closed when the server was not exported
	// with a constrainable endpoint.  The constrainable lease is the only
	// concrete wire form (this class is abstract).
	if (!(server instanceof RemoteMethodControl)) {
	    throw new IllegalArgumentException(
		"service must be exported with a constrainable endpoint: "
		+ "server does not implement RemoteMethodControl");
	}
	return new ConstrainableServiceLease(
	    server, registrarID, serviceID, leaseID, expiration, null, true);
    }

    ServiceLease(GetArg arg) throws IOException, ClassNotFoundException{
	super(arg);
	serviceID = new ServiceID(
		arg.get("serviceIDMostSig", 0L),
		arg.get("serviceIDLeastSig", 0L));
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

}
