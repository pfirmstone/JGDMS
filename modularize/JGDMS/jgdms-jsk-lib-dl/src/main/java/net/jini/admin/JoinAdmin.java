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

package net.jini.admin;

import net.jini.core.discovery.LookupLocator;
import net.jini.core.entry.Entry;
import java.rmi.RemoteException;

/**
 * The methods in this interface are used to control a service's
 * participation in the join protocol.  The object returned by the
 * Administrable.getAdmin method should implement this interface, in
 * addition to any other service-specific administration interfaces.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see net.jini.admin.Administrable#getAdmin
 */
public interface JoinAdmin {

    /** 
     * Get the current attribute sets for the service. 
     * 
     * @return the current attribute sets for the service
     * @throws java.rmi.RemoteException when a communication issue occurs.
     */
    Entry[] getLookupAttributes() throws RemoteException;

    /** 
     * Add attribute sets for the service.  The resulting set will be used
     * for all future joins.  The attribute sets are also added to all 
     * currently-joined lookup services.
     *
     * @param attrSets the attribute sets to add
     * @throws java.rmi.RemoteException when a communication issue occurs.
     */
    void addLookupAttributes(Entry[] attrSets) throws RemoteException;

    /**  
     * Modify the current attribute sets, using the same semantics as
     * ServiceRegistration.modifyAttributes.  The resulting set will be used
     * for all future joins.  The same modifications are also made to all 
     * currently-joined lookup services.
     *
     * @param attrSetTemplates the templates for matching attribute sets
     * @param attrSets the modifications to make to matching sets
     * @throws java.rmi.RemoteException when a communication issue occurs.
     *     
     * @see net.jini.core.lookup.ServiceRegistration#modifyAttributes
     */
   void modifyLookupAttributes(Entry[] attrSetTemplates, Entry[] attrSets)
       throws RemoteException;
    
    /**
     * Get the list of groups to join.  An empty array means the service
     * joins no groups (as opposed to "all" groups).
     *
     * @return an array of groups to join. An empty array means the service
     *         joins no groups (as opposed to "all" groups).
     * @throws java.rmi.RemoteException when a communication issue occurs.
     * @see #setLookupGroups
     */
    String[] getLookupGroups() throws RemoteException;

    /**
     * Add new groups to the set to join.  Lookup services in the new
     * groups will be discovered and joined.
     *
     * @param groups groups to join
     * @throws java.rmi.RemoteException when a communication issue occurs.
     * @see #removeLookupGroups
     */
    void addLookupGroups(String[] groups) throws RemoteException;

    /**
     * Remove groups from the set to join.  Leases are cancelled at lookup
     * services that are not members of any of the remaining groups.
     *
     * @param groups groups to leave
     * @throws java.rmi.RemoteException when a communication issue occurs.
     * @see #addLookupGroups
     */
    void removeLookupGroups(String[] groups) throws RemoteException;

    /**
     * Replace the list of groups to join with a new list.  Leases are
     * cancelled at lookup services that are not members of any of the
     * new groups.  Lookup services in the new groups will be discovered
     * and joined.
     *
     * @param groups groups to join
     * @throws java.rmi.RemoteException when a communication issue occurs.
     * @see #getLookupGroups
     */
    void setLookupGroups(String[] groups) throws RemoteException;

    /** 
     *Get the list of locators of specific lookup services to join. 
     *
     * @return the list of locators of specific lookup services to join
     * @throws java.rmi.RemoteException when a communication issue occurs.
     * @see #setLookupLocators
     */
    LookupLocator[] getLookupLocators() throws RemoteException;

    /**
     * Add locators for specific new lookup services to join.  The new
     * lookup services will be discovered and joined.
     *
     * @param locators locators of specific lookup services to join
     * @throws java.rmi.RemoteException when a communication issue occurs.
     * @see #removeLookupLocators
     */
    void addLookupLocators(LookupLocator[] locators) throws RemoteException;

    /**
     * Remove locators for specific lookup services from the set to join.
     * Any leases held at the lookup services are cancelled.
     *
     * @param locators locators of specific lookup services to leave
     * @throws java.rmi.RemoteException when a communication issue occurs.
     * @see #addLookupLocators
     */
    void removeLookupLocators(LookupLocator[] locators) throws RemoteException;

    /**
     * Replace the list of locators of specific lookup services to join
     * with a new list.  Leases are cancelled at lookup services that were
     * in the old list but are not in the new list.  Any new lookup services
     * will be discovered and joined.
     *
     * @param locators locators of specific lookup services to join
     * @throws java.rmi.RemoteException when a communication issue occurs.
     * @see #getLookupLocators
     */
    void setLookupLocators(LookupLocator[] locators) throws RemoteException;
}
