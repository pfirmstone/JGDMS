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

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectOutputStream;
import java.io.ObjectOutputStream.PutField;
import java.io.ObjectStreamException;
import java.io.ObjectStreamField;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.rmi.activation.ActivationSystem;
import java.rmi.server.UID;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.river.api.io.AtomicSerial.GetArg;

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
    
    /**
     * By defining serial persistent fields, we don't need to use transient fields.
     * All fields can be final and this object becomes immutable.
     */
    private static final ObjectStreamField[] serialPersistentFields = 
	{
	    new ObjectStreamField("system", ActivationSystem.class),
	    new ObjectStreamField("uid", UID.class)
	};
    
    private static final Field uidField;
    
    static {
	uidField = AccessController.doPrivileged(new PrivilegedAction<Field>(){

	    @Override
	    public Field run() {
		try {
		    Field uidField = java.rmi.activation.ActivationGroupID.class.getDeclaredField("uid");
		    uidField.setAccessible(true);
		    return uidField;
		} catch (NoSuchFieldException ex) {
		    Logger.getLogger(ActivationGroupIDSerializer.class.getName()).log(Level.SEVERE, null, ex);
		} catch (SecurityException ex) {
		    Logger.getLogger(ActivationGroupIDSerializer.class.getName()).log(Level.SEVERE, null, ex);
		}
		return null;
	    }
	    
	});
	
    }
    
    private /*transient*/ final java.rmi.activation.ActivationGroupID resolve;
    private final ActivationSystem system;
    private final UID uid;
    
    ActivationGroupIDSerializer(java.rmi.activation.ActivationGroupID resolve){
	this.resolve = resolve;
	system = resolve.getSystem();
	// REMIND: shouldn't throw exception here
	try {
	    uid = (UID) uidField.get(resolve);
	} catch (IllegalArgumentException ex) {
	    throw new IllegalArgumentException(ex);
	} catch (IllegalAccessException ex) {
	    throw new IllegalArgumentException(ex);
	}
    }
    
    ActivationGroupIDSerializer(GetArg arg) throws IOException{
	this(
	    check(
		Valid.notNull(
		    arg.get("system", null, ActivationSystem.class),
		    "system cannot be null"
		),
	        Valid.notNull(
		    arg.get("uid", null, UID.class), 
		    "uid cannot be null"
		),
	        Valid.notNull(
		    uidField, 
		    "Permission is not granted to deserialize ActivationGroupID"
		)
	    )   
	);
    }
    
    private static java.rmi.activation.ActivationGroupID check(
			    ActivationSystem system, 
			    UID uid, 
			    Field field) throws InvalidObjectException
    {
	java.rmi.activation.ActivationGroupID actGroupID =
		new java.rmi.activation.ActivationGroupID(system);
	try {
	    field.set(actGroupID, uid);
	} catch (IllegalArgumentException ex) {
	    throw new InvalidObjectException(ex.toString());
	} catch (IllegalAccessException ex) {
	    throw new InvalidObjectException(ex.toString());
	}
	return actGroupID;
    }
    
    Object readResolve() throws ObjectStreamException {
	if (resolve != null) return resolve;
	return check(system, uid, uidField);
    }
    
    private void writeObject(ObjectOutputStream out) throws IOException {
	PutField pf = out.putFields();
	pf.put("system", system);
	pf.put("uid", uid);
	out.writeFields();
    }
    
}
