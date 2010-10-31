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

import com.sun.jini.proxy.ConstrainableProxyUtil;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.lang.reflect.Method;
import java.rmi.MarshalledObject;
import net.jini.admin.Administrable;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceItem;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.core.lookup.ServiceTemplate;
import net.jini.security.proxytrust.ProxyTrustIterator;
import net.jini.security.proxytrust.SingletonProxyTrustIterator;

/**
 * RegistrarProxy subclass that supports constraints.
 *
 * @author Sun Microsystems, Inc.
 *
 */
final class ConstrainableRegistrarProxy
    extends RegistrarProxy implements RemoteMethodControl
{
    private static final long serialVersionUID = 2L;

    /** Mappings between ServiceRegistrar and Registrar methods */
    private static final Method[] methodMappings = {
	Util.getMethod(ServiceRegistrar.class, "getEntryClasses",
		       new Class[]{ ServiceTemplate.class }),
	Util.getMethod(Registrar.class, "getEntryClasses",
		       new Class[]{ Template.class }),

	Util.getMethod(ServiceRegistrar.class, "getFieldValues",
		       new Class[]{ ServiceTemplate.class, int.class,
				    String.class }),
	Util.getMethod(Registrar.class, "getFieldValues",
		       new Class[]{ Template.class, int.class, int.class }),

	Util.getMethod(ServiceRegistrar.class, "getGroups", new Class[0]),
	Util.getMethod(Registrar.class, "getMemberGroups", new Class[0]),

	Util.getMethod(ServiceRegistrar.class, "getLocator", new Class[0]),
	Util.getMethod(Registrar.class, "getLocator", new Class[0]),

	Util.getMethod(ServiceRegistrar.class, "getServiceTypes",
		       new Class[]{ ServiceTemplate.class, String.class }),
	Util.getMethod(Registrar.class, "getServiceTypes",
		       new Class[]{ Template.class, String.class }),

	Util.getMethod(ServiceRegistrar.class, "lookup",
		       new Class[]{ ServiceTemplate.class }),
	Util.getMethod(Registrar.class, "lookup",
		       new Class[]{ Template.class }),

	Util.getMethod(ServiceRegistrar.class, "lookup",
		       new Class[]{ ServiceTemplate.class, int.class }),
	Util.getMethod(Registrar.class, "lookup",
		       new Class[]{ Template.class, int.class }),

	Util.getMethod(ServiceRegistrar.class, "notify",
		       new Class[]{ ServiceTemplate.class, int.class,
				    RemoteEventListener.class,
				    MarshalledObject.class, long.class }),
	Util.getMethod(Registrar.class, "notify",
		       new Class[]{ Template.class, int.class,
				    RemoteEventListener.class,
				    MarshalledObject.class, long.class }),

	Util.getMethod(ServiceRegistrar.class, "register",
		       new Class[]{ ServiceItem.class, long.class }),
	Util.getMethod(Registrar.class, "register",
		       new Class[]{ Item.class, long.class }),

	Util.getMethod(Administrable.class, "getAdmin", new Class[0]),
	Util.getMethod(Administrable.class, "getAdmin", new Class[0])
    };

    /** Client constraints for this proxy, or null */
    private final MethodConstraints constraints;

    /**
     * Creates new ConstrainableRegistrarProxy with given server reference,
     * service ID and client constraints.
     */
    ConstrainableRegistrarProxy(Registrar server,
				ServiceID registrarID,
				MethodConstraints constraints)
    {
	super((Registrar) ((RemoteMethodControl) server).setConstraints(
		  ConstrainableProxyUtil.translateConstraints(
		      constraints, methodMappings)),
	      registrarID);
	this.constraints = constraints;
    }

    // javadoc inherited from RemoteMethodControl.setConstraints
    public RemoteMethodControl setConstraints(MethodConstraints constraints) {
	return new ConstrainableRegistrarProxy(
	    server, registrarID, constraints);
    }

    // javadoc inherited from RemoteMethodControl.getConstraints
    public MethodConstraints getConstraints() {
	return constraints;
    }

    /**
     * Returns iterator used by ProxyTrustVerifier to retrieve a trust verifier
     * for this object.
     */
    private ProxyTrustIterator getProxyTrustIterator() {
	return new SingletonProxyTrustIterator(server);
    }

    /**
     * Verifies that the client constraints for this proxy are consistent with
     * those set on the underlying server ref.
     */
    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();
	ConstrainableProxyUtil.verifyConsistentConstraints(
	    constraints, server, methodMappings);
    }
}
