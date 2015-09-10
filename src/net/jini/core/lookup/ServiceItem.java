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
import java.io.ObjectStreamField;
import net.jini.core.entry.CloneableEntry;
import net.jini.core.entry.Entry;

/**
 * Items are stored in and retrieved from the lookup service using
 * instances of this class.
 *
 * @author Sun Microsystems, Inc.
 *
 * @since 1.0
 */
public class ServiceItem implements java.io.Serializable, Cloneable {

    private static final long serialVersionUID = 717395451032330758L;
    private static final ObjectStreamField[] serialPersistentFields = 
    { 
        /** @serialField serviceID ServiceID Universally unique identifier for services */
        new ObjectStreamField("serviceID", ServiceID.class),
        /** @serialField service Object A service proxy */
        new ObjectStreamField("service", Object.class),
        /** @serialField attributeSets Entry[] Attribute sets */
        new ObjectStreamField("attributeSets", Entry[].class)
    };

    /** A service ID, or null if registering for the first time. */
    public ServiceID serviceID;
    /** A service object. */
    public Object service;
    /** Attribute sets. */
    public Entry[] attributeSets;

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
     * {@link net.jini.lookup.ServiceDiscoveryManager} to avoid sharing 
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
     * @param in
     * @throws IOException
     * @throws ClassNotFoundException 
     */
    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();
    }
}
