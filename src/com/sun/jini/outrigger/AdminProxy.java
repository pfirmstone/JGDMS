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
package com.sun.jini.outrigger;

import java.io.Serializable;
import java.io.ObjectInputStream;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.rmi.RemoteException;

import net.jini.core.discovery.LookupLocator;
import net.jini.core.entry.Entry;
import net.jini.core.transaction.Transaction;
import net.jini.core.transaction.TransactionException;

import net.jini.space.JavaSpace;

import net.jini.id.Uuid;
import net.jini.id.ReferentUuid;
import net.jini.id.ReferentUuids;

/**
 * <code>AdminProxy</code> objects are connected to particular
 * <code>OutriggerServerImpl</code>s to implement the
 * <code>JavaSpaceAdmin</code> interface for the server.
 *
 * @see JavaSpaceAdmin 
 */
class AdminProxy implements JavaSpaceAdmin, ReferentUuid, Serializable {
    private static final long serialVersionUID = 1L;
    
    /** Reference to the actual remote admin object. */
    final OutriggerAdmin          admin;

    /** The <code>Uuid</code> that identifies the space this proxy is for */
    final Uuid spaceUuid;
 
    private static final boolean DEBUG = false;

    /**
     * Create an <code>AdminProxy</code> for the given remote admin
     * objects.
     * @param admin reference to remote server for the space.
     * @param spaceUuid universal unique ID for the space.
     * @throws NullPointerException if <code>admin</code> or
     *         <code>spaceUuid</code> is <code>null</code>.     
     */
    AdminProxy(OutriggerAdmin admin, Uuid spaceUuid) {
	if (admin == null)
	    throw new NullPointerException("admin must be non-null");
	if (spaceUuid == null) 
	    throw new NullPointerException("spaceUuid must be non-null");
	this.admin = admin;
	this.spaceUuid = spaceUuid;
    }

    /**
     * Read this object back and validate state.
     */
    private void readObject(ObjectInputStream in) 
	throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();

	if (admin == null) 
	    throw new InvalidObjectException("null server reference");
	    
	if (spaceUuid == null)
	    throw new InvalidObjectException("null Uuid");
    }

    /** 
     * We should always have data in the stream, if this method
     * gets called there is something wrong.
     */
    private void readObjectNoData() throws InvalidObjectException {
	throw new 
	    InvalidObjectException("SpaceProxy should always have data");
    }

    // inherit doc comment
    public JavaSpace space() throws RemoteException {
	return admin.space();
    }

    // inherit doc comment
    public AdminIterator contents(Entry tmpl, Transaction tr)
	throws TransactionException, RemoteException
    {
	return contents(tmpl, tr, USE_DEFAULT);
    }

    // inherit doc comment
    public AdminIterator contents(Entry tmpl, Transaction tr, int fetchSize)
	throws TransactionException, RemoteException
    {
	return new IteratorProxy(
            admin.contents(SpaceProxy2.repFor(tmpl), tr), admin, fetchSize);
    }

    // inherit doc comment
    public void destroy() throws RemoteException {
	admin.destroy();
    }

    //              JoinAdmin
    // --------------------------------------------------
    // inherit doc comment
    public Entry[] getLookupAttributes() throws RemoteException {
	return admin.getLookupAttributes();
    }

    // inherit doc comment
    public void addLookupAttributes(Entry[] attrSets) 
	throws RemoteException 
    {
	admin.addLookupAttributes(attrSets);
    }

    // inherit doc comment
    public void modifyLookupAttributes(Entry[] attrSetTemplates, 
				       Entry[] attrSets)
	throws RemoteException
    {
	admin.modifyLookupAttributes(attrSetTemplates, attrSets);
    }

    // inherit doc comment
    public String[] getLookupGroups() throws RemoteException {
	return admin.getLookupGroups();
    }

    // inherit doc comment
    public void addLookupGroups(String[] groups) throws RemoteException {
	admin.addLookupGroups(groups);
    }
    
    // inherit doc comment
    public void removeLookupGroups(String[] groups) 
	throws RemoteException 
    {
	admin.removeLookupGroups(groups);
    }

    // inherit doc comment
    public void setLookupGroups(String[] groups) throws RemoteException {
	admin.setLookupGroups(groups);
    }

    // inherit doc comment
    public LookupLocator[] getLookupLocators() throws RemoteException {
	return admin.getLookupLocators();
    }

    // inherit doc comment
    public void addLookupLocators(LookupLocator[] locators) 
	throws RemoteException
    {
	admin.addLookupLocators(locators);
    }

    // inherit doc comment
    public void removeLookupLocators(LookupLocator[] locators) 
	throws RemoteException
    {
	admin.removeLookupLocators(locators);
    }

    // inherit doc comment
    public void setLookupLocators(LookupLocator[] locators) 
	throws RemoteException
    {
	admin.setLookupLocators(locators);
    }

    public String toString() {
	return getClass().getName() + " for " + spaceUuid + 
	    " (through " + admin + ")";
    }

    public boolean equals(Object other) {
	return ReferentUuids.compare(this, other);
    }

    public int hashCode() {
	return spaceUuid.hashCode();
    }

    public Uuid getReferentUuid() {
	return spaceUuid;
    }
}
