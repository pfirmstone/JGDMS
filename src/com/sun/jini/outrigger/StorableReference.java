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
package com.sun.jini.outrigger;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.rmi.MarshalledObject;
import java.rmi.RemoteException;
import net.jini.security.ProxyPreparer;

/**
 * This class holds a proxy for some remote resource. When
 * persisted the proxy is marshalled in its own 
 * {@link MarshalledObject} so this object can be unmarshalled
 * even if the proxy can't be (say because it codebase is 
 * unavailable). The {@link #get} method can
 * be used to retrieve the proxy on demand.
 *
 * @author Sun Microsystems, Inc.
 *
 */
class StorableReference implements Externalizable {
    /** The proxy in marshalled form */
    private MarshalledObject bytes;

    /** A cached copy of the unmarshalled proxy */
    private transient Object obj;

    /** True if the <code>obj</code> has been prepared */
    private transient boolean prepared;

    private static final boolean DEBUG = false;
    private static final long serialVersionUID = -3793675220968988873L;

    /**
     * Create a <code>StorableReference</code> that will hold
     * <code>obj</code>.
     */
    public StorableReference(Object obj) {
	if (obj == null)
	    throw new NullPointerException("obj cannot be null");
	this.obj = obj;
	prepared = true;
    }

    /** Used by the object output stream. */
    public StorableReference() { }

    /**
     * Return the proxy.  If necessary deserialize the 
     * proxy and optionally prepare it. Will only deserialize the
     * reference if it has not already been deserialized. Will
     * only prepare the object if <code>preparer</code> is non-null
     * and no previous call to get has succeeded. If this method
     * throws an exception, preparation has not succeeded. If a
     * previous call to this method has succeed, all future
     * calls will succeed and return the same object as the 
     * first successful call.
     *
     * @param preparer the <code>ProxyPreparer</code> to
     *                 be used to prepare the reference. May
     *                 be <code>null</code>.
     * @return the remote reference contained in this object
     * @throws IOException if the unmarshalling fails. Will
     *                 also throw {@link RemoteException}
     *                 if <code>preparer.prepareProxy</code>
     *                 does.
     * @throws ClassNotFoundException if unmarshalling fails
     *                 with one.
     * @throws SecurityException if <code>preparer</code> does.
     */
    public synchronized Object get(ProxyPreparer preparer)
	throws IOException, ClassNotFoundException 
    {
	/* Even though we may be doing remote ops, it is
	 * ok that we are synchronized.  It does not
	 * matter if a competing thread waits on this
	 * lock, or waits doing the same remote operation
	 * the owner of the lock is doing (in fact it
	 * is better for the second thread to wait instead
	 * of duplicating the work of the first).
	 */

	if (obj == null)
	    obj = bytes.get();

	if (!prepared) {
	    if (preparer != null)
		obj = preparer.prepareProxy(obj);
	    prepared = true;
	}
       
	return obj;
    }

    /**
     * @serialData
     */  
    // inherit doc comment
    public void writeExternal(ObjectOutput out) throws IOException {
	synchronized (this) {
	    if (bytes == null)
		bytes = new MarshalledObject(obj);
	}

	out.writeObject(bytes);
    }

    // inherit doc comment
    public void readExternal(ObjectInput in)
	throws IOException, ClassNotFoundException 
    {
	bytes = (MarshalledObject)in.readObject();
    }
}
