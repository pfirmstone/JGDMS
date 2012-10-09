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
package com.sun.jini.test.share.reggie;

import java.rmi.RemoteException;
import net.jini.core.entry.Entry;
import net.jini.core.lookup.*;
import net.jini.core.lease.*;

/**
 * Implementation class for the ServiceRegistration interface.
 *
 * 
 *
 */
class Registration implements ServiceRegistration, java.io.Serializable
{
    private static final long serialVersionUID = -276552004282140614L;

    /**
     * The registrar
     *
     * @serial
     */
    private final Registrar server;
    /**
     * The service lease
     *
     * @serial
     */
    private final ServiceLease lease;

    /** Simple constructor */
    public Registration(Registrar server, ServiceLease lease) {
	this.server = server;
	this.lease = lease;
    }

    // This method's javadoc is inherited from an interface of this class
    public ServiceID getServiceID() {
	return lease.getServiceID();
    }

    // This method's javadoc is inherited from an interface of this class
    public Lease getLease() {
	return lease;
    }

    // This method's javadoc is inherited from an interface of this class
    public void addAttributes(Entry[] attrSets)
	throws UnknownLeaseException, RemoteException
    {
	server.addAttributes(lease.getServiceID(),
			     lease.getLeaseID(),
			     EntryRep.toEntryRep(attrSets, true));
    }

    // This method's javadoc is inherited from an interface of this class
    public void modifyAttributes(Entry[] attrSetTmpls, Entry[] attrSets)
	throws UnknownLeaseException, RemoteException
    {
	server.modifyAttributes(lease.getServiceID(),
				lease.getLeaseID(),
				EntryRep.toEntryRep(attrSetTmpls, false),
				EntryRep.toEntryRep(attrSets, false));
    }

    // This method's javadoc is inherited from an interface of this class
    public void setAttributes(Entry[] attrSets)
	throws UnknownLeaseException, RemoteException
    {
	server.setAttributes(lease.getServiceID(),
			     lease.getLeaseID(),
			     EntryRep.toEntryRep(attrSets, true));
    }
}
