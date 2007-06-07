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

import java.rmi.RemoteException;
import net.jini.core.lease.*;
import net.jini.core.entry.Entry;

/**
 * A registered service item is manipulated using an instance of this class.
 * This is not a remote interface; each implementation of the lookup service
 * exports proxy objects that implement this interface local the client.  The
 * proxy methods obey normal RMI remote interface semantics.  Every method
 * invocation (on both ServiceRegistrar and ServiceRegistration) is atomic
 * with respect to other invocations.
 * 
 * @author Sun Microsystems, Inc.
 *
 * @see ServiceRegistrar
 *
 * @since 1.0
 */
public interface ServiceRegistration {

    /**
     * Returns the service ID for this service. Note that this method 
     * does not make a remote call.
     *
     * @return the ServiceID for this service.
     */
    ServiceID getServiceID();

    /**
     * Returns the lease that controls the service registration, allowing
     * the lease to be renewed or cancelled.  Note that this does not make
     * a remote call.
     *
     * @return the lease that controls this service registration.
     */
    Lease getLease();

    /**
     * Adds the specified attribute sets (those that aren't duplicates of
     * existing attribute sets) to the registered service item.  Note that
     * this operation has no effect on existing attribute sets of the
     * service item, and can be repeated in an idempotent fashion.
     *
     * @param attrSets attribute sets to add
     * @throws UnknownLeaseException the registration lease has expired
     *         or been cancelled.
     * @throws java.rmi.RemoteException
     */
    void addAttributes(Entry[] attrSets)
	throws UnknownLeaseException, RemoteException;

    /**
     * Modifies existing attribute sets. The lengths of attrSetTemplates and
     * attrSets must be equal, or IllegalArgumentException is thrown.  The
     * service item's attribute sets are modified as follows.  For each array
     * index i: if attrSets[i] is null, then every entry that matches
     * attrSetTemplates[i] is deleted; otherwise, for every non-null field
     * in attrSets[i], the value of that field is stored into the
     * corresponding field of every entry that matches attrSetTemplates[i].
     * The class of attrSets[i] must be the same as, or a superclass of, the
     * class of attrSetTemplates[i], or IllegalArgumentException is thrown.
     * If the modifications result in duplicate entries within the service
     * item, the duplicates are eliminated.
     * <p>
     * Note that it is possible to use modifyAttributes in ways that are not
     * idempotent.  The attribute schema should be designed in such a way
     * that all intended uses of this method can be performed in an
     * idempotent fashion.  Also note that modifyAttributes does not provide
     * a means for setting a field to null; it is assumed that the attribute
     * schema is designed in such a way that this is not necessary.
     *
     * @param attrSetTemplates attribute set templates to match
     * @param attrSets modifications to make to matching attribute sets
     * @throws UnknownLeaseException the registration lease has expired
     *         or been cancelled
     * @throws IllegalArgumentException lengths of attrSetTemplates and
     *         attrSets are not equal, or class of an attrSets element is 
     *         not the same as, or a superclass of, the class of the
     *         corresponding attrSetTemplates element
     * @throws java.rmi.RemoteException
     */
    void modifyAttributes(Entry[] attrSetTemplates, Entry[] attrSets)
	throws UnknownLeaseException, RemoteException;

    /**
     * Deletes all of the service item's existing attributes, and replaces
     * them with the specified attribute sets.  Any duplicate attribute sets
     * are eliminated in the stored representation of the item.
     *
     * @param attrSets attribute sets to use
     * @throws UnknownLeaseException the registration lease has expired
     *         or been cancelled
     * @throws java.rmi.RemoteException
     */
    void setAttributes(Entry[] attrSets)
	throws UnknownLeaseException, RemoteException;
}
