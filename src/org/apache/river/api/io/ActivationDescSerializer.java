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
class ActivationDescSerializer implements Serializable {
    private static final long serialVersionUID = 1L;
    /**
     * By defining serial persistent fields, we don't need to use transient fields.
     * All fields can be final and this object becomes immutable.
     */
    private static final ObjectStreamField[] serialPersistentFields = 
	{
	    new ObjectStreamField("groupID", java.rmi.activation.ActivationGroupID.class),
	    new ObjectStreamField("className", String.class),
	    new ObjectStreamField("location", String.class),
	    new ObjectStreamField("data", MarshalledObject.class),
	    new ObjectStreamField("restart", boolean.class)
	};
    
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
    
    ActivationDescSerializer(GetArg arg) throws IOException{
	this(
	    new ActivationDesc(
		Valid.notNull(
		    arg.get("groupID", null, java.rmi.activation.ActivationGroupID.class),
		    "groupID cannot be null"
		),
		arg.get("className", null, String.class),
		arg.get("location", null, String.class),
		arg.get("data", null, MarshalledObject.class),
		arg.get("restart", true)
	    )
	);
    }
    
    Object readResolve() throws ObjectStreamException {
	return actDesc;
    }
    
    /**
     * @serialData 
     * @param out
     * @throws IOException 
     */
    private void writeObject(ObjectOutputStream out) throws IOException {
	ObjectOutputStream.PutField pf = out.putFields();
	pf.put("groupID", groupID);
	pf.put("className", className);
	pf.put("location", location);
	pf.put("data", data);
	pf.put("restart", restart);
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
