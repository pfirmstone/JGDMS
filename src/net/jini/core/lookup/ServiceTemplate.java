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
import java.io.ObjectOutputStream;
import net.jini.core.entry.CloneableEntry;
import net.jini.core.entry.Entry;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;

/**
 * Items in the lookup service are matched using instance of this class.
 * A service item (item) matches a service template (tmpl) if:
 * item.serviceID equals tmpl.serviceID (or if tmpl.serviceID is null);
 * and item.service is an instance of every type in tmpl.serviceTypes; and
 * item.attributeSets contains at least one matching entry for each entry
 * template in tmpl.attributeSetTemplates.
 * <p>
 * An entry matches
 * an entry template if the class of the template is the same as, or a
 * superclass of, the class of the entry, and every non-null field in the
 * template equals the corresponding field of the entry.  Every entry can be
 * used to match more than one template.  Note that in a service template,
 * for serviceTypes and attributeSetTemplates, a null field is equivalent to
 * an empty array; both represent a wildcard.
 *
 * @author Sun Microsystems, Inc.
 *
 * @since 1.0
 */
@AtomicSerial
public class ServiceTemplate implements java.io.Serializable, Cloneable {

    private static final long serialVersionUID = 7854483807886483216L;

    /**
     * Service ID to match, or <tt>null</tt>.
     *
     * @serial
     */
    public ServiceID serviceID;
    /**
     * Service types to match, or <tt>null</tt>.
     *
     * @serial
     */
    public Class[] serviceTypes;
    /**
     * Attribute set templates to match, or <tt>null</tt>.
     *
     * @serial
     */
    public Entry[] attributeSetTemplates;
    
    /**
     * Constructor for @AtomicSerial, note that any instances of this
     * should be cloned in a secure stream to prevent an attacker retaining 
     * a reference to mutable state.
     * 
     * @param arg
     * @throws IOException 
     */
    public ServiceTemplate(GetArg arg) throws IOException {
	/* Any class cast exceptions will be occur before Object's default
	 * constructor is called, in that case an instance of this object
	 * will not be created.
	 * null check for org/apache/river/test/spec/lookupservice/ToStringTest.td
	 */
	this(arg == null ? null: arg.get("serviceID", null, ServiceID.class),
	    arg == null? null: arg.get("serviceTypes", null, Class[].class),
	    arg == null? null: arg.get("attributeSetTemplates", null, Entry[].class)
	);
    }
    

    /**
     * Simple constructor.
     *
     * @param serviceID service ID to match, or null
     * @param serviceTypes service types to match, or null
     * @param attrSetTemplates attribute set templates to match, or null
     */
    public ServiceTemplate(ServiceID serviceID,
			   Class[] serviceTypes,
			   Entry[] attrSetTemplates) 
    {
	this.serviceID = serviceID;
	this.serviceTypes = serviceTypes;
	this.attributeSetTemplates = attrSetTemplates;
    }
    
    /**
     * Clone has been implemented to allow utilities such as
     * {@link net.jini.lookup.ServiceDiscoveryManager} to avoid sharing 
     * internally stored instances with client code.
     * 
     * @return a clone of the original ServiceTemplate
     */
    @Override
    public ServiceTemplate clone() 
    {
        try {
            ServiceTemplate clone = (ServiceTemplate) super.clone();
	    if (clone.serviceTypes != null){
		clone.serviceTypes = clone.serviceTypes.clone();
	    }
	    if (clone.attributeSetTemplates != null){
		clone.attributeSetTemplates = clone.attributeSetTemplates.clone();
		for (int i = 0, l = clone.attributeSetTemplates.length; i < l; i++){
		    Entry e = clone.attributeSetTemplates[i];
		    if (e instanceof CloneableEntry){
			clone.attributeSetTemplates[i] = ((CloneableEntry) e).clone();
		    }
		}
	    }
            return clone;
        } catch (CloneNotSupportedException ex) {
            throw new AssertionError();
        }
    }
    
    /**
     * Returns a <code>String</code> representation of this 
     * <code>ServiceTemplate</code>.
     * @return <code>String</code> representation of this 
     * <code>ServiceTemplate</code>
     */
    public String toString() {
	StringBuilder sBuffer = new StringBuilder();
	sBuffer.append(
	       getClass().getName()).append(
	       "[serviceID=").append(
	       serviceID).append(
	       ", serviceTypes=");
	if (serviceTypes != null) {
            sBuffer.append("[");
            if (serviceTypes.length > 0) {
                for (int i = 0; i < serviceTypes.length - 1; i++)
                    sBuffer.append(serviceTypes[i]).append(" ");
                sBuffer.append(serviceTypes[serviceTypes.length - 1]);
            }
            sBuffer.append("]");
	} else {
	    sBuffer.append((Object)null);
	}
	sBuffer.append(", attributeSetTemplates=");
	if (attributeSetTemplates != null) {
            sBuffer.append("[");
            if (attributeSetTemplates.length > 0) {
                for (int i = 0; i < attributeSetTemplates.length - 1; i++)
                    sBuffer.append(attributeSetTemplates[i]).append(" ");
                sBuffer.append(
                    attributeSetTemplates[attributeSetTemplates.length - 1]);
            }
            sBuffer.append("]");
	} else {
	    sBuffer.append((Object)null);
	}
	return sBuffer.append("]").toString();
    }
            
    private void writeObject(ObjectOutputStream out) throws IOException {
	out.defaultWriteObject();
}
}
