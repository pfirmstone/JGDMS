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

import org.apache.river.api.io.AtomicSerial.SerialForm;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.io.ObjectStreamField;
import java.io.Serializable;
import java.rmi.MarshalledObject;
import java.rmi.activation.ActivationDesc;
import java.util.Objects;
import org.apache.river.api.io.AtomicSerial.GetArg;

/**
 * Serializes ActivationDesc
 */
@Serializer(replaceObType = ActivationDesc.class)
@AtomicSerial
class ActivationDescSerializer implements Serializable, Resolve {
    private static final long serialVersionUID = 1L;
    /**
     * By defining serial persistent fields, we don't need to use transient fields.
     * All fields can be final and this object becomes immutable.
     */
    private static final ObjectStreamField[] serialPersistentFields = serialForm();
    
    /**
     * Names of serial arguments or serial persistent fields.
     */
    private static final String GROUP_ID = "groupID";
    private static final String CLASS_NAME = "className";
    private static final String LOCATION = "location";
    private static final String DATA = "data";
    private static final String RESTART = "restart";
    
    /**
     * AtomicSerial declared serial arguments.
     * @return 
     */
    public static final SerialForm [] serialForm(){
        return new SerialForm [] {
	    new SerialForm(GROUP_ID, java.rmi.activation.ActivationGroupID.class),
	    new SerialForm(CLASS_NAME, String.class),
	    new SerialForm(LOCATION, String.class),
	    new SerialForm(DATA, MarshalledObject.class),
	    new SerialForm(RESTART, boolean.class)
	};
    }
    
    public static void serialize(AtomicSerial.PutArg args, ActivationDescSerializer obj) throws IOException {
        putArgs(args, obj);
        args.writeArgs();
    }
    
    private static void putArgs(ObjectOutputStream.PutField pf, ActivationDescSerializer obj) {
        pf.put(GROUP_ID, obj.groupID);
	pf.put(CLASS_NAME, obj.className);
	pf.put(LOCATION, obj.location);
	pf.put(DATA, obj.data);
	pf.put(RESTART, obj.restart);
    }
    
    private final java.rmi.activation.ActivationGroupID groupID;
    private final String className;
    private final String location;
    private final MarshalledObject<?> data;
    private final boolean restart;
    private final /*transient*/ ActivationDesc actDesc;
    
    ActivationDescSerializer(ActivationDesc actDesc){
	groupID = actDesc.getGroupID();
	className = actDesc.getClassName();
	location = actDesc.getLocation();
	data = actDesc.getData();
	restart = actDesc.getRestartMode();
	this.actDesc = actDesc;
    }
    
    ActivationDescSerializer(GetArg arg) throws IOException, ClassNotFoundException{
	this(
	    new ActivationDesc(
		Valid.notNull(
		    arg.get(GROUP_ID, null, java.rmi.activation.ActivationGroupID.class),
		    "groupID cannot be null"
		),
		arg.get(CLASS_NAME, null, String.class),
		arg.get(LOCATION, null, String.class),
		arg.get(DATA, null, MarshalledObject.class),
		arg.get(RESTART, true)
	    )
	);
    }
    
    @Override
    public Object readResolve() throws ObjectStreamException {
	return actDesc;
    }
    
    /**
     * @serialData 
     * @param out
     * @throws IOException 
     */
    private void writeObject(ObjectOutputStream out) throws IOException {
	putArgs(out.putFields(), this);
	out.writeFields();
    }
    
    @Override
    public boolean equals(Object obj){
	if (obj == this) return true;
	if (!(obj instanceof ActivationDescSerializer)) return false;
	ActivationDescSerializer that = (ActivationDescSerializer) obj;
	if (!Objects.equals(this.groupID, that.groupID)) return false;
	if (!Objects.equals(this.className, that.className)) return false;
	if (!Objects.equals(this.location, that.location)) return false;
	if (!Objects.equals(this.data, that.data)) return false;
	return this.restart == that.restart;
    }

    @Override
    public int hashCode() {
	int hash = 7;
	hash = 67 * hash + Objects.hashCode(this.groupID);
	hash = 67 * hash + Objects.hashCode(this.className);
	hash = 67 * hash + Objects.hashCode(this.location);
	hash = 67 * hash + Objects.hashCode(this.data);
	hash = 67 * hash + (this.restart ? 1 : 0);
	return hash;
    }
}
