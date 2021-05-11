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
package net.jini.core.lookup;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamField;
import net.jini.core.entry.CloneableEntry;
import net.jini.core.entry.Entry;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;

/**
 * Items are stored in and retrieved from the lookup service using
 * instances of this class.
 *
 * @author Sun Microsystems, Inc.
 *
 * @since 1.0
 */
@AtomicSerial
public class ServiceItem implements java.io.Serializable, Cloneable {

    private static final long serialVersionUID = 717395451032330758L;
    private static final String SERVICE_ID = "serviceID";
    private static final String SERVICE = "service";
    private static final String ATTRIBUTE_SETS = "attributeSets";
    
    private static final ObjectStreamField[] serialPersistentFields = 
            serialForm();

    public static SerialForm [] serialForm(){
        return new SerialForm[]{
            /** @serialField serviceID ServiceID Universally unique identifier for services */
            new SerialForm(SERVICE_ID, ServiceID.class),
            /** @serialField service Object A service proxy */
            new SerialForm(SERVICE, Object.class),
            /** @serialField attributeSets Entry[] Attribute sets */
            new SerialForm(ATTRIBUTE_SETS, Entry[].class)
        };
    }
    
    public static void serialize(PutArg arg, ServiceItem s) throws IOException{
        arg.put(SERVICE_ID, s.serviceID);
        arg.put(SERVICE, s.service);
        arg.put(ATTRIBUTE_SETS, s.attributeSets.clone());
        arg.writeArgs();
    }
    
    /** A service ID, or null if registering for the first time. */
    public ServiceID serviceID;
    /** A service object. */
    public Object service;
    /** Attribute sets. */
    public Entry[] attributeSets;

    /**
     * {@link AtomicSerial} constructor.  This object should be cloned 
     * during de-serialization.
     * 
     * @param arg atomic deserialization parameter 
     * @throws IOException if there are I/O errors while reading from GetArg's
     *         underlying <code>InputStream</code>
     * @throws java.lang.ClassNotFoundException
     */
    public ServiceItem(GetArg arg) throws IOException, ClassNotFoundException {
	this( arg == null ? null: arg.get(SERVICE_ID, null, ServiceID.class),
	      arg == null ? null: arg.get(SERVICE, null),
	      arg == null ? null: arg.get(ATTRIBUTE_SETS, null, Entry[].class));
    }
    
    /**
     * Simple constructor.
     *
     * @param serviceID service ID, or null if registering for the first time
     * @param service service object
     * @param attrSets attribute sets
     */
    public ServiceItem(ServiceID serviceID, Object service, Entry[] attrSets)
    {
	this.serviceID = serviceID;
	this.service = service;
	this.attributeSets = attrSets;
    }
    
    /**
     * Returns a <code>String</code> representation of this 
     * <code>ServiceItem</code>.
     * @return <code>String</code> representation of this 
     * <code>ServiceItem</code>
     */
    @Override
    public String toString() 
    {
	StringBuilder sBuffer = new StringBuilder(256);
	sBuffer.append(
	       getClass().getName()).append(
	       "[serviceID=").append(serviceID).append(
	       ", service=").append(service).append(
	       ", attributeSets=");
	if (attributeSets != null) {
            sBuffer.append("[");
            if (attributeSets.length > 0) {
                for (int i = 0; i < attributeSets.length - 1; i++)
                    sBuffer.append(attributeSets[i]).append(" ");
                sBuffer.append(attributeSets[attributeSets.length - 1]);
            }
            sBuffer.append("]");
	} else {
	    sBuffer.append((Object)null);
	}
	return sBuffer.append("]").toString();
    }
    
    /**
     * Clone has been implemented to allow utilities such as
     * <code> net.jini.lookup.ServiceDiscoveryManager </code> to avoid sharing 
     * internally stored instances with client code.
     * 
     * A deep copy clone is made
     * 
     * @return a clone of the original ServiceItem
     */
    @Override
    public ServiceItem clone() 
    {
        try {
            ServiceItem clone = (ServiceItem) super.clone();
	    if (clone.attributeSets != null){
		clone.attributeSets = clone.attributeSets.clone();
		for (int i = 0, l = clone.attributeSets.length; i < l; i++){
		    Entry e = clone.attributeSets[i];
		    if (e instanceof CloneableEntry){
			clone.attributeSets[i] = ((CloneableEntry) e).clone();
		    }

		}
	    }
            return clone;
        } catch (CloneNotSupportedException ex) {
            throw new AssertionError();
        }
    }
    
    /**
     * @serialData 
     * @param out
     * @throws IOException
     */
    private void writeObject(ObjectOutputStream out) throws IOException {
	out.defaultWriteObject();
    }
    
    /**
     * @serial
     * @param in ObjectInputStream
     * @throws ClassNotFoundException if class not found.
     * @throws IOException if a problem occurs during de-serialization.
     */
    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();
    }
}
