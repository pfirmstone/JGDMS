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

package org.apache.river.api.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectOutputStream.PutField;
import java.io.ObjectStreamClass;
import java.io.ObjectStreamException;
import java.io.ObjectStreamField;
import java.io.Serializable;
import java.rmi.activation.ActivationSystem;
import java.rmi.server.UID;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;

/**
 * Serializer for java.rmi.activation.ActivationGroupID.
 * 
 * Doesn't work for subclasses.
 * 
 */
@Serializer(replaceObType = java.rmi.activation.ActivationGroupID.class)
@AtomicSerial
class ActivationGroupIDSerializer implements Serializable {
    
    private static final long serialVersionUID = -1648432278909740833L;
    private static final String SYSTEM = "system";
    private static final String UID = "uid";
    
    /**
     * By defining serial persistent fields, we don't need to use transient fields.
     * All fields can be final and this object becomes immutable.
     */
    private static final ObjectStreamField[] serialPersistentFields = 
	serialForm();
    
    public static SerialForm [] serialForm(){
        return new SerialForm []{
            new SerialForm (SYSTEM, ActivationSystem.class),
	    new SerialForm (UID, UID.class)
        };
    }
    
    public static void serialize(PutArg arg, ActivationGroupIDSerializer e) throws IOException{
        putArgs(arg, e);
        arg.writeArgs();
    }
    
    public static void putArgs(PutField pf, ActivationGroupIDSerializer e){
        pf.put(SYSTEM, e.system);
	pf.put(UID, e.uid);
    }
   
    private final ActivationSystem system;
    private final UID uid;
    
    ActivationGroupIDSerializer(java.rmi.activation.ActivationGroupID resolve){
	system = resolve.getSystem();
        // Use serialization to get access to private uid field that will no longer
        // be accessible using reflection in future version of Java.
        ActivationGroupID mo = null;
	try {
	    ByteArrayOutputStream baos = new ByteArrayOutputStream();
	    ObjectOutputStream oos = new ObjectOutputStream(baos);
	    oos.writeObject(resolve);
	    oos.flush();
	    byte[] bytes = baos.toByteArray();
	    ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
	    ObjectInputStream ois = new ToShellActivationGroupID(bais);
	    mo = (ActivationGroupID) ois.readObject();
	} catch (IOException ioe) {
	    throw new AssertionError(ioe);
	} catch (ClassNotFoundException ioe){
	    throw new AssertionError(ioe);
	}
        uid = mo.uid;
    }
    
    ActivationGroupIDSerializer(ActivationSystem system, UID uid){
        this.system = system;
        this.uid = uid;
    }
    
    ActivationGroupIDSerializer(GetArg arg) throws IOException, ClassNotFoundException{
	this(Valid.notNull(
		    arg.get(SYSTEM, null, ActivationSystem.class),
		    "system cannot be null"
		),
	        Valid.notNull(
		    arg.get(UID, null, UID.class), 
		    "uid cannot be null"
		)   
	);
    }
    
    private static java.rmi.activation.ActivationGroupID convert(
            ActivationSystem system, UID uid) throws InvalidObjectException
    {
        ActivationGroupID groupID = new ActivationGroupID(system, uid);
        try {
	    ByteArrayOutputStream baos = new ByteArrayOutputStream();
	    ObjectOutputStream oos = new ObjectOutputStream(baos);
	    oos.writeObject(groupID);
	    oos.flush();
	    byte[] bytes = baos.toByteArray();
	    ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
	    ObjectInputStream ois = new ToRmiActivationGroupID(bais);
	    return (java.rmi.activation.ActivationGroupID) ois.readObject();
	} catch (IOException ioe) {
	    throw new AssertionError(ioe);
	} catch (ClassNotFoundException ioe){
	    throw new AssertionError(ioe);
	}
    }
    
    Object readResolve() throws ObjectStreamException {
	return convert(system, uid);
    }
    
    private void writeObject(ObjectOutputStream out) throws IOException {
	putArgs(out.putFields(), this);
	out.writeFields();
    }
    
    private static class ToRmiActivationGroupID extends ObjectInputStream {

	ToRmiActivationGroupID(InputStream in) throws IOException {
	    super(in);
        }

	/**
	 * Overrides <code>ObjectInputStream.resolveClass</code> to change
	 * an occurence of class <code>java.rmi.MarshalledObject</code> to
	 * class <code>net.jini.io.MarshalledObject</code>.
	 */
        @Override
	protected Class resolveClass(ObjectStreamClass desc)
	    throws IOException, ClassNotFoundException
	{
	    if (desc.getName().equals("org.apache.river.api.io.ActivationGroupID")) {
		return java.rmi.activation.ActivationGroupID.class;
	    }
	    return super.resolveClass(desc);
	}
    }

    private static class ToShellActivationGroupID extends ObjectInputStream {

	ToShellActivationGroupID(InputStream in) throws IOException {
	    super(in);
        }

	/**
	 * Overrides <code>ObjectInputStream.resolveClass</code>
	 * to change an occurence of class
	 * <code>net.jini.io.MarshalledObject</code>
	 * to class <code>java.rmi.MarshalledObject</code>.
	 */
        @Override
	protected Class resolveClass(ObjectStreamClass desc)
	    throws IOException, ClassNotFoundException
	{
	    if (desc.getName().equals("java.rmi.activation.ActivationGroupID")) {
		return ActivationGroupID.class;
	    }
	    return super.resolveClass(desc);
	}
    }
    
    
}
