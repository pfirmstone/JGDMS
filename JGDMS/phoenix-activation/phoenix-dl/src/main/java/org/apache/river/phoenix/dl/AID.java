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

package org.apache.river.phoenix.dl;

import java.io.IOException;
import java.io.Serializable;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.UnmarshalException;
import net.jini.activation.arg.ActivationException;
import net.jini.activation.arg.ActivationID;
import java.rmi.server.UID;
import java.security.AccessController;
import java.security.PrivilegedAction;
import org.apache.river.api.io.Resolve;
import net.jini.export.ProxyAccessor;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.Replace;
import org.apache.river.proxy.MarshalledWrapper;

/**
 * @author Sun Microsystems, Inc.
 * 
 * @since 2.0
 */
public class AID implements Serializable, Replace, ActivationID {
    private static final long serialVersionUID = 681896091039721074L;

    protected final Activator activator;
    protected final UID uid;

    @AtomicSerial
    static final class State implements Serializable, ProxyAccessor, Resolve {
	private static final long serialVersionUID = 4479839553358267720L;

	private final Activator activator;
	private final UID uid;

	State(Activator activator, UID uid) {
	    this.activator = activator;
	    this.uid = uid;
	}
	
	public State(GetArg arg) throws IOException, ClassNotFoundException{
	    this(arg.get("activator", null, Activator.class),
		 arg.get("uid", null, UID.class));
	}

	public Object readResolve() {
	    return new AID(activator, uid);
	}

	public Object getProxy() {
	    return activator;
	}
    }

    public AID(Activator activator, UID uid) {
	super();
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
	    ClassLoader loader = classLoader();
 	    return (Remote) marshalledProxy.get(loader, loader);
 	} catch (RemoteException e) {
 	    throw e;
 	} catch (java.io.IOException e) {
 	    throw new UnmarshalException("activation failed", e);
 	} catch (java.lang.ClassNotFoundException e) {
 	    throw new UnmarshalException("activation failed", e);
	}
	
    }
    
    private ClassLoader classLoader(){
	return AccessController.doPrivileged(new PrivilegedAction<ClassLoader>(){

	    public ClassLoader run() {
		return AID.class.getClassLoader();
	    }
	    
	});
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
    
    @Override
    public String toString(){
	StringBuilder sb = new StringBuilder();
	sb.append("AID Activator ").append(activator).append(", UID ").append(uid);
	return sb.toString();
    }

    @Override
    public Object writeReplace() {
	return new State(activator, uid);
    }
}
