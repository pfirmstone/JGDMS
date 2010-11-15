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

package com.sun.jini.mahalo;

import java.io.IOException;
import java.rmi.MarshalledObject;
import java.rmi.RemoteException;


/**
 * This class holds a <code>MarshalledObject</code> that can be stored
 * persistently.  When you invoke <code>get</code>, the object is
 * deserialized, its value cached in this object, and then returned.
 * Subsequent calls to <code>get</code> return that value.  This lets
 * you store the object and hold it around, waiting until it is
 * actually deserializable, since it may not be at any given time due
 * to various factors, such as the codebase being unavailable.
 *
 * @author Sun Microsystems, Inc.
 *
 */
public class StorableObject implements java.io.Serializable {
    /**
     * @serial
     */
    private MarshalledObject	bytes;	// the serialized bytes
    private transient Object	obj;	// the cached object reference

    private static final boolean DEBUG = false;
    private static final long serialVersionUID = -3793675220968988873L;

    /**
     * Create a <code>StorableObject</code> that will hold <code>obj</code>
     * in a <code>MarshalledObject</code>.
     */
    public StorableObject(Object obj) throws RemoteException {
	try {
	    bytes = new MarshalledObject(obj);
	    this.obj = obj;
	} catch (RemoteException e) {
	    throw e;
	} catch (IOException e) {
	    fatalError("can't encode object", e);
	}
    }

    /**
     * Return the <code>hashCode</code> of the <code>MarshalledObject</code>.
     */
    public int hashCode() {
	return bytes.hashCode(); // value of obj.hashCode()
    }

    public boolean equals(Object that) {
	try {
	    if (that instanceof StorableObject)
		return get().equals(((StorableObject) that).get());
	    else
		return get().equals(that);
	} catch (RemoteException e) {
	    return false;	//!! or should I just die?
	}
    }

    /**
     * Return the Remote reference.  Deserialize the object if we don't
     * already have an actual reference in hand.
     *
     * @exception java.rmi.RemoteException
     *		Problems re-establishing connection with remote object
     */
    public Object get() throws RemoteException {
	try {
	    if (obj == null)
		obj = bytes.get();
	    return obj;
	} catch (RemoteException e) {
	    if (DEBUG)
	        System.out.println("*****StorableObject:get:" + e.getMessage());
	    throw e;
	} catch (IOException e) {
	    fatalError("can't decode object", e);
	} catch (ClassNotFoundException e) {
	    fatalError("can't decode object", e);
	}
	fatalError("how did we get here?", null);
	return null;	// not reached, but compiler doesn't know
    }

    /**
     * Unrecoverable error happened -- show it and give up the ghost.
     */
    private static void fatalError(String msg, Throwable e)
	throws RemoteException
    {
	System.err.println(msg);
	if (e != null)
	    e.printStackTrace(System.err);
	throw new RemoteException(msg, e);
    }
}
