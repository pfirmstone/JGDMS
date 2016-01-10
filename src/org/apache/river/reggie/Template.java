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
package org.apache.river.reggie;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.rmi.RemoteException;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceTemplate;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;

/**
 * A Template contains the fields of a ServiceTemplate packaged up for
 * transmission between client-side proxies and the registrar server.
 * Instances are never visible to clients, they are private to the
 * communication between the proxies and the server.
 * <p>
 * This class only has a bare minimum of methods, to minimize
 * the amount of code downloaded into clients.
 *
 * @author Sun Microsystems, Inc.
 *
 */
@AtomicSerial
class Template implements Serializable {

    private static final long serialVersionUID = 2L;

    /**
     * ServiceTemplate.serviceID
     *
     * @serial
     */
    final ServiceID serviceID;
    /**
     * ServiceTemplate.serviceTypes converted to ServiceTypes
     *
     * @serial
     */
    final ServiceType[] serviceTypes;
    /**
     * ServiceTemplate.attributeSetTemplates converted to EntryReps
     *
     * @serial
     */
    final EntryRep[] attributeSetTemplates;

    /**
     * Converts a ServiceTemplate to a Template.  Any exception that results
     * is bundled up into a MarshalException.
     */
    public Template(ServiceTemplate tmpl) throws RemoteException {
	serviceID = tmpl.serviceID;
	serviceTypes = ClassMapper.toServiceType(tmpl.serviceTypes);
	attributeSetTemplates =
	    EntryRep.toEntryRep(tmpl.attributeSetTemplates, false);
    }
    
    private static boolean check(GetArg arg) throws IOException {
	Object serviceID = arg.get("serviceID", null);
	if (serviceID != null && !(serviceID instanceof ServiceID)) 
	    throw new InvalidObjectException("serviceID must be instance of ServiceID");
	Object serviceTypes = arg.get("serviceTypes", null);
	if (serviceTypes != null && !(serviceTypes instanceof ServiceType []))
	    throw new InvalidObjectException("serviceTypes must be instance of ServiceType[]");
	Object attributeSetTemplates = arg.get("attributeSetTemplates", null);
	if (attributeSetTemplates != null && !(attributeSetTemplates instanceof EntryRep[]))
	    throw new InvalidObjectException("attributeSetTemplates must be instance of EntryRep[]");
	return true;
}
    
    Template(GetArg arg) throws IOException {
	this(check(arg), arg);
    }
    
    private Template(boolean check, GetArg arg) throws IOException {
	serviceID = (ServiceID) arg.get("serviceID", null);
	ServiceType [] serviceTypes = (ServiceType[]) arg.get("serviceTypes", null);
	this.serviceTypes = 
		serviceTypes != null? 
		serviceTypes.clone() : 
		null;
	EntryRep [] attributeSetTemplates = (EntryRep[]) arg.get("attributeSetTemplates", null);
	this.attributeSetTemplates = 
		attributeSetTemplates != null ?
		attributeSetTemplates.clone():
		null;
    }
    
    private void writeObject(ObjectOutputStream out) throws IOException {
	out.defaultWriteObject();
    }
    
    /**
     * Serialization evolution support
     * @serialData 
     */
    private void readObject(java.io.ObjectInputStream in)
	throws java.io.IOException, ClassNotFoundException
    {
	in.defaultReadObject();
    }
}
