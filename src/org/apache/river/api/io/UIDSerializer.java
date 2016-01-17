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
import java.io.ObjectInput;
import java.io.ObjectStreamClass;
import java.io.ObjectStreamException;
import java.io.ObjectStreamField;
import java.io.Serializable;
import java.rmi.server.UID;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.ReadInput;
import org.apache.river.api.io.AtomicSerial.ReadObject;

/**
 *
 * @author peter
 */
@AtomicSerial
class UIDSerializer implements Serializable {
    private static final long serialVersionUID = 1L;
    
    /**
     * By defining serial persistent fields, we don't need to use transient fields.
     * All fields can be final and this object becomes immutable.
     */
    private static final ObjectStreamField[] serialPersistentFields = 
	    ObjectStreamClass.NO_FIELDS;
    
    
    private final UID uid;
    
    @ReadInput
    static ReadObject get(){
	return new RO();
    }
    
    static class RO implements ReadObject {
	
	UID uid;

	@Override
	public void read(ObjectInput input) throws IOException, ClassNotFoundException {
	    uid = UID.read(input); //Only reads primitive values, thus safe.
	}
	
    }
    
    UIDSerializer(UID uid){
	this.uid = uid;
    }
    
    public UIDSerializer(GetArg arg){
	this(((RO)arg.getReader()).uid);
    }
    
    private void writeObject(java.io.ObjectOutputStream stream)
     throws IOException {
	uid.write(stream);
    }
    
    Object readResolve() throws ObjectStreamException {
	return uid;
    }
}
