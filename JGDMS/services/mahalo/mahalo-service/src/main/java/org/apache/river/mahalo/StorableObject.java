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

package org.apache.river.mahalo;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamField;
import java.rmi.MarshalledObject;
import java.rmi.RemoteException;
import java.util.Collections;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.MarshallingFormat;
import net.jini.io.MarshalledInstance;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;


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
@AtomicSerial
public class StorableObject implements java.io.Serializable {
    /**
     * The marshalled object, in canonical MarshalledInstance form. Non-final so a
     * legacy (java.rmi.MarshalledObject "bytes") record can be normalized into it on
     * read. Always written as the new "instance" slot; "bytes" is a read-only legacy
     * slot retained for in-place upgrade from pre-4.0.0 logs.
     */
    private MarshalledInstance instance;
    private volatile transient Object	obj;	// the cached object reference

    private static final boolean DEBUG = false;
    private static final long serialVersionUID = -3793675220968988873L;

    // JOSS dual-read: legacy "bytes" (java.rmi.MarshalledObject) OR new "instance".
    private static final ObjectStreamField[] serialPersistentFields = {
        new ObjectStreamField("bytes", MarshalledObject.class),
        new ObjectStreamField("instance", MarshalledInstance.class)
    };

    public static SerialForm[] serialForm() {
        return new SerialForm[] {
            new SerialForm("bytes", MarshalledObject.class, true),       // legacy, optional
            new SerialForm("instance", MarshalledInstance.class, true)   // canonical, optional
        };
    }

    public static void serialize(PutArg arg, StorableObject o) throws IOException {
        arg.put("bytes", null);            // legacy slot retired on write
        arg.put("instance", o.instance);
        arg.writeArgs();
    }

    /** Read the canonical instance, accepting either the new or the legacy slot. */
    private static MarshalledInstance readInstance(MarshalledInstance instance,
	    MarshalledObject bytes) {
	if (instance != null) return instance;
	return (bytes == null) ? null : new MarshalledInstance(bytes);
    }

    /**
     * Create a <code>StorableObject</code> that will hold <code>obj</code>
     * in a <code>MarshalledObject</code>.
     */
    public StorableObject(Object obj) throws RemoteException {
	this(obj, toMI(obj));
    }

    StorableObject(GetArg arg) throws IOException, ClassNotFoundException {
	this(null, readInstance(
		arg.get("instance", null, MarshalledInstance.class),
		arg.get("bytes", null, MarshalledObject.class)));
    }

    private static MarshalledInstance toMI(Object obj) throws RemoteException{
	try {
            // Dual-read upgrade: write via DER (MarshallingFormat.ATOMIC_DER);
            // old JOSS-encoded entries still decode via readInstance()/get()
            // below (payloadFormat dispatch happens inside
            // MarshalledInstance.get(), no code change needed there).
            return new MarshalledInstance(obj, Collections.EMPTY_SET,
                    new InvocationConstraints(MarshallingFormat.ATOMIC_DER, null));
        } catch (RemoteException e){
	    throw e;
        } catch (IOException e){
	    fatalError("can't encode object", e);
	}
        return null; //Unreachable.
    }

    private StorableObject (Object obj, MarshalledInstance instance){
        this.instance = instance;
        this.obj = obj;
    }
    
    /**
     * Return the <code>hashCode</code> of the <code>MarshalledObject</code>.
     */
    public int hashCode() {
	return instance.hashCode(); // value of obj.hashCode()
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
		obj = instance.get(false);
	    return obj;
	} catch (RemoteException e) {
	    if (DEBUG)
	        System.out.println("*****StorableObject:get:" + e.getMessage());
	    throw e;
	} catch (IOException e) {
	    fatalError("can't decode object", e);
	} catch (ClassNotFoundException e) {
	    fatalError("can't decode object", e);
	} catch (IllegalStateException e) {
	    // MarshalledInstance.get()'s payloadFormat could not be
	    // resolved (e.g. jgdms-der missing from the classpath).
	    fatalError("can't decode object", e);
	}
	fatalError("how did we get here?", null);
	return null;	// not reached, but compiler doesn't know
    }

    private void writeObject(ObjectOutputStream s) throws IOException {
	ObjectOutputStream.PutField pf = s.putFields();
	pf.put("bytes", null);            // legacy slot retired on write
	pf.put("instance", instance);
	s.writeFields();
    }

    private void readObject(ObjectInputStream s)
                                   throws IOException, ClassNotFoundException
        {
	    ObjectInputStream.GetField gf = s.readFields();
	    instance = readInstance(
		    (MarshalledInstance) gf.get("instance", null),
		    (MarshalledObject) gf.get("bytes", null));
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
