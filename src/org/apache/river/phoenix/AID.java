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

package org.apache.river.phoenix;

import java.io.IOException;
import java.io.Serializable;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.UnmarshalException;
import java.rmi.activation.ActivationException;
import java.rmi.activation.ActivationID;
import java.rmi.server.UID;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.proxy.MarshalledWrapper;

/**
 * @author Sun Microsystems, Inc.
 * 
 * @since 2.0
 */
@AtomicSerial //To prevent replacement by serializer in stream constructor not implemented.
class AID extends ActivationID {
    private static final long serialVersionUID = 681896091039721074L;

    protected final Activator activator;
    protected final UID uid;

    @AtomicSerial
    static final class State implements Serializable {
	private static final long serialVersionUID = 4479839553358267720L;

	private final Activator activator;
	private final UID uid;

	State(Activator activator, UID uid) {
	    this.activator = activator;
	    this.uid = uid;
	}
	
	public State(GetArg arg) throws IOException{
	    this(arg.get("activator", null, Activator.class),
		 arg.get("uid", null, UID.class));
	}

	private Object readResolve() {
	    return new AID(activator, uid);
	}
    }

    public AID(Activator activator, UID uid) {
	super(null);
	this.activator = activator;
	this.uid = uid;
    }

    /**
     * Activate the object corresponding to this instance.
     */
    @Override
    public Remote activate(boolean force)
	throws ActivationException, RemoteException
    {
 	try {
 	    MarshalledWrapper marshalledProxy =
		activator.activate(this, force);
 	    return (Remote) marshalledProxy.get();
 	} catch (RemoteException e) {
 	    throw e;
 	} catch (java.io.IOException e) {
 	    throw new UnmarshalException("activation failed", e);
 	} catch (java.lang.ClassNotFoundException e) {
 	    throw new UnmarshalException("activation failed", e);
	}
	
    }
    
    public UID getUID() {
	return uid;
    }

    @Override
    public int hashCode() {
	return uid.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
	if (obj != null && obj.getClass() == getClass()) {
	    AID id = (AID) obj;
	    return (uid.equals(id.uid) && activator.equals(id.activator));
	}
	return false;
    }

    private Object writeReplace() {
	return new State(activator, uid);
    }
}
