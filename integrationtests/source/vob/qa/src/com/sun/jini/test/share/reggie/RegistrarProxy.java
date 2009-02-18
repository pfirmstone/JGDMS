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
package com.sun.jini.test.share.reggie;

import net.jini.core.lookup.*;
import net.jini.core.event.*;
import net.jini.admin.Administrable;
import net.jini.core.discovery.LookupLocator;
import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.UnmarshalException;
import java.rmi.MarshalledObject;
import java.lang.reflect.Field;
import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * A RegistrarProxy is a proxy for a registrar.  Clients only see instances
 * via the ServiceRegistrar interface (and the RegistrarAdmin interface
 * if they need it).
 *
 * @author Sun Microsystems, Inc.
 *
 */
class RegistrarProxy implements ServiceRegistrar, Administrable,
                                java.io.Serializable
{
    private static final long serialVersionUID = 2425188657680236255L;

    /**
     * The registrar
     *
     * @serial
     */
    private final Registrar server;
    /**
     * The registrar's service ID
     *
     * @serial
     */
    private final ServiceID serviceID;

    /** Simple constructor. */
    public RegistrarProxy(Registrar server, ServiceID serviceID) {
	this.server = server;
	this.serviceID = serviceID;
    }

    public Object getAdmin() throws RemoteException {
        return server.getAdmin();
    }

    public ServiceRegistration register(ServiceItem item, long leaseDuration)
	throws RemoteException
    {
	return server.register(new Item(item), leaseDuration);
    }

    public Object lookup(ServiceTemplate tmpl) throws RemoteException {
	MarshalledObject obj = server.lookup(new Template(tmpl));
	if (obj == null)
	    return null;
	try {
	    return obj.get();
	} catch (IOException e) {
	    throw new UnmarshalException("error unmarshalling return", e);
	} catch (ClassNotFoundException e) {
	    throw new UnmarshalException("error unmarshalling return", e);
	}
    }

    public ServiceMatches lookup(ServiceTemplate tmpl, int maxMatches)
	throws RemoteException
    {
	return server.lookup(new Template(tmpl), maxMatches).get();
    }

    public EventRegistration notify(ServiceTemplate tmpl,
				    int transitions,
				    RemoteEventListener listener,
				    MarshalledObject handback,
				    long leaseDuration)
	throws RemoteException
    {
	return server.notify(new Template(tmpl), transitions, listener,
			     handback, leaseDuration);
    }

    public Class[] getEntryClasses(ServiceTemplate tmpl)
	throws RemoteException
    {
	return EntryClassBase.toClass(
				  server.getEntryClasses(new Template(tmpl)));
    }

    public Object[] getFieldValues(ServiceTemplate tmpl,
				   int setIndex, String field)
	throws NoSuchFieldException, RemoteException
    {
	/* check that setIndex and field are valid, convert field to index */
	ClassMapper.EntryField[] efields =
	    ClassMapper.getFields(
			     tmpl.attributeSetTemplates[setIndex].getClass());
	int fidx;
	for (fidx = efields.length; --fidx >= 0; ) {
	    if (field.equals(efields[fidx].field.getName()))
		break;
	}
	if (fidx < 0)
	    throw new NoSuchFieldException(field);
	Object[] values = server.getFieldValues(new Template(tmpl),
						setIndex, fidx);
	/* unmarshal each value, replacing with null on exception */
	if (values != null && efields[fidx].marshal) {
	    for (int i = values.length; --i >= 0; ) {
		try {
		    values[i] = ((MarshalledObject)values[i]).get();
		    continue;
		} catch (Throwable e) {
		    handleException(e);
		}
		values[i] = null;
	    }
	}
	return values;
    }

    /**
     * Rethrow the exception if it is an Error, unless it is a LinkageError,
     * OutOfMemoryError, or StackOverflowError.  Otherwise print the
     * exception stack trace if debugging is enabled.
     */
    static void handleException(final Throwable e) {
	if (e instanceof Error &&
	    !(e instanceof LinkageError ||
	      e instanceof OutOfMemoryError ||
	      e instanceof StackOverflowError))
	    throw (Error)e;
	AccessController.doPrivileged(new PrivilegedAction() {
	    public Object run() {
		try {
		    if (System.getProperty("com.sun.jini.reggie.proxy.debug")
			!= null)
			e.printStackTrace();
		} catch (SecurityException ee) {
		}
		return null;
	    }
	});
    }

    public Class[] getServiceTypes(ServiceTemplate tmpl, String prefix)
	throws RemoteException
    {
	return ServiceTypeBase.toClass(
				   server.getServiceTypes(new Template(tmpl),
							  prefix));
    }

    public ServiceID getServiceID() {
	return serviceID;
    }

    public LookupLocator getLocator() throws RemoteException {
	return server.getLocator();
    }

    public String[] getGroups() throws RemoteException {
	return server.getMemberGroups();
    }

    public int hashCode() {
	return serviceID.hashCode();
    }

    /** Proxies for servers with the same serviceID are considered equal. */
    public boolean equals(Object obj) {
	return (obj instanceof RegistrarProxy &&
		serviceID.equals(((RegistrarProxy)obj).serviceID));
    }
}
