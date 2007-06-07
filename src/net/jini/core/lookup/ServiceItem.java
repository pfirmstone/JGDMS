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

import net.jini.core.entry.Entry;

/**
 * Items are stored in and retrieved from the lookup service using
 * instances of this class.
 *
 * @author Sun Microsystems, Inc.
 *
 * @since 1.0
 */
public class ServiceItem implements java.io.Serializable {

    private static final long serialVersionUID = 717395451032330758L;

    /**
     * A service ID, or null if registering for the first time.
     *
     * @serial
     */
    public ServiceID serviceID;
    /**
     * A service object.
     *
     * @serial
     */
    public Object service;
    /**
     * Attribute sets.
     *
     * @serial
     */
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
    public String toString() {
	StringBuffer sBuffer = new StringBuffer();
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
}
