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
package com.sun.jini.reggie;

import java.io.Serializable;
import java.rmi.RemoteException;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceTemplate;

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
class Template implements Serializable {

    private static final long serialVersionUID = 2L;

    /**
     * ServiceTemplate.serviceID
     *
     * @serial
     */
    public ServiceID serviceID;
    /**
     * ServiceTemplate.serviceTypes converted to ServiceTypes
     *
     * @serial
     */
    public ServiceType[] serviceTypes;
    /**
     * ServiceTemplate.attributeSetTemplates converted to EntryReps
     *
     * @serial
     */
    public EntryRep[] attributeSetTemplates;

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
}
