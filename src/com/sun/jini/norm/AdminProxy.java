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
package com.sun.jini.norm;

import com.sun.jini.admin.DestroyAdmin;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.rmi.RemoteException;
import net.jini.admin.JoinAdmin;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.discovery.LookupLocator;
import net.jini.core.entry.Entry;
import net.jini.id.Uuid;
import net.jini.security.proxytrust.ProxyTrustIterator;
import net.jini.security.proxytrust.SingletonProxyTrustIterator;

/**
 * Defines a proxy for a Norm server's admin object.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
class AdminProxy extends AbstractProxy implements JoinAdmin, DestroyAdmin {
    private static final long serialVersionUID = 1;

    /**
     * Creates an admin proxy, returning an instance that implements
     * RemoteMethodControl if the server does.
     */
    static AdminProxy create(NormServer server, Uuid serverUuid) {
	if (server instanceof RemoteMethodControl) {
	    return new ConstrainableAdminProxy(server, serverUuid);
	} else {
	    return new AdminProxy(server, serverUuid);
	}
    }

    /** Creates an instance of this class. */
    AdminProxy(NormServer server, Uuid serverUuid) {
	super(server, serverUuid);
    }

    /** Require fields to be non-null. */
    private void readObjectNoData() throws InvalidObjectException {
	throw new InvalidObjectException(
	    "server and uuid must be non-null");
    }

    /* -- Implement JoinAdmin -- */

    /* inherit javadoc */
    public Entry[] getLookupAttributes() throws RemoteException {
	return server.getLookupAttributes();
    }

    /* inherit javadoc */
    public void addLookupAttributes(Entry[] attrSets) throws RemoteException {
	server.addLookupAttributes(attrSets);
    }

    /* inherit javadoc */
    public void modifyLookupAttributes(Entry[] attrSetTemplates, 
				       Entry[] attrSets) 
	throws RemoteException
    {
	server.modifyLookupAttributes(attrSetTemplates, attrSets);
    }
  
    /* inherit javadoc */
    public String[] getLookupGroups() throws RemoteException {
	return server.getLookupGroups();
    }

    /* inherit javadoc */
    public void addLookupGroups(String[] groups) throws RemoteException {
	server.addLookupGroups(groups);
    }

    /* inherit javadoc */
    public void removeLookupGroups(String[] groups) throws RemoteException {
	server.removeLookupGroups(groups);
    }

    /* inherit javadoc */
    public void setLookupGroups(String[] groups) throws RemoteException {
	server.setLookupGroups(groups);
    }

    /* inherit javadoc */
    public LookupLocator[] getLookupLocators() throws RemoteException {
	return server.getLookupLocators();
    }

    /* inherit javadoc */
    public void addLookupLocators(LookupLocator[] locators)
	throws RemoteException
    {
	server.addLookupLocators(locators);
    }

    /* inherit javadoc */
    public void removeLookupLocators(LookupLocator[] locators)
	throws RemoteException
    {
	server.removeLookupLocators(locators);
    }

    /* inherit javadoc */
    public void setLookupLocators(LookupLocator[] locators)
	throws RemoteException
    {
	server.setLookupLocators(locators);
    }

    /* -- Implement DestroyAdmin -- */

    /* inherit javadoc */
    public void destroy() throws RemoteException {
	server.destroy();
    }

    /** Defines a subclass that implements RemoteMethodControl. */
    static final class ConstrainableAdminProxy extends AdminProxy
	implements RemoteMethodControl
    {
	private static final long serialVersionUID = 1;

	/** Creates an instance of this class. */
	ConstrainableAdminProxy(NormServer server, Uuid serverUuid) {
	    super(server, serverUuid);
	    if (!(server instanceof RemoteMethodControl)) {
		throw new IllegalArgumentException(
		    "server must implement RemoteMethodControl");
	    }
	}

	/** Require server to implement RemoteMethodControl. */
	private void readObject(ObjectInputStream in)
	    throws IOException, ClassNotFoundException
	{
	    in.defaultReadObject();
	    if (!(server instanceof RemoteMethodControl)) {
		throw new InvalidObjectException(
		    "server must implement RemoteMethodControl");
	    }
	}

	/* inherit javadoc */
	public RemoteMethodControl setConstraints(
	    MethodConstraints constraints)
	{
	    NormServer constrainedServer = (NormServer)
		((RemoteMethodControl) server).setConstraints(constraints);
	    return new ConstrainableAdminProxy(constrainedServer, uuid);
	}

	/* inherit javadoc */
	public MethodConstraints getConstraints() {
	    return ((RemoteMethodControl) server).getConstraints();
	}

	/**
	 * Returns a proxy trust iterator that yields this object's server.
	 */
	private ProxyTrustIterator getProxyTrustIterator() {
	    return new SingletonProxyTrustIterator(server);
	}
    }
}
