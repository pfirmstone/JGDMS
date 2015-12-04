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
package org.apache.river.test.services.lookupsimulator;

import org.apache.river.proxy.ConstrainableProxyUtil;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.lang.reflect.Method;
import java.rmi.MarshalledObject;
import java.rmi.RemoteException;
import javax.security.auth.Subject;
import net.jini.admin.Administrable;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceItem;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.core.lookup.ServiceTemplate;
import net.jini.core.discovery.LookupLocator;
import net.jini.security.proxytrust.ProxyTrustIterator;
import net.jini.security.proxytrust.SingletonProxyTrustIterator;

/**
 * RegistrarProxy subclass that supports constraints.
 *
 * @author Sun Microsystems, Inc.
 *
 */
final class LookupSimulatorConstrainableProxy
    extends LookupSimulatorProxy implements RemoteMethodControl
{
    private static final long serialVersionUID = 2L;

    /** Mappings between ServiceRegistrar and Registrar methods */
    private static final Method[] methodMappings = {
	getMethod(LookupSimulatorProxyInterface.class, "getEntryClasses",
		       new Class[]{ ServiceTemplate.class }),
	getMethod(LookupSimulator.class, "getEntryClasses",
		       new Class[]{ ServiceTemplate.class }),

	getMethod(LookupSimulatorProxyInterface.class, "getFieldValues",
		       new Class[]{ ServiceTemplate.class, int.class,
				    String.class }),
	getMethod(LookupSimulator.class, "getFieldValues",
		       new Class[]{ ServiceTemplate.class, int.class, String.class }),

	getMethod(LookupSimulatorProxyInterface.class, "getGroups", new Class[0]),
	getMethod(LookupSimulator.class, "getMemberGroups", new Class[0]),

	getMethod(LookupSimulatorProxyInterface.class, "getLocator", new Class[0]),
	getMethod(LookupSimulator.class, "getLocator", new Class[0]),

	getMethod(LookupSimulatorProxyInterface.class, "getServiceTypes",
		       new Class[]{ ServiceTemplate.class, String.class }),
	getMethod(LookupSimulator.class, "getServiceTypes",
		       new Class[]{ ServiceTemplate.class, String.class }),

	getMethod(LookupSimulatorProxyInterface.class, "lookup",
		       new Class[]{ ServiceTemplate.class }),
	getMethod(LookupSimulator.class, "lookup",
		       new Class[]{ ServiceTemplate.class }),

	getMethod(LookupSimulatorProxyInterface.class, "lookup",
		       new Class[]{ ServiceTemplate.class, int.class }),
	getMethod(LookupSimulator.class, "lookup",
		       new Class[]{ ServiceTemplate.class, int.class }),

	getMethod(LookupSimulatorProxyInterface.class, "notify",
		       new Class[]{ ServiceTemplate.class, int.class,
				    RemoteEventListener.class,
				    MarshalledObject.class, long.class }),
	getMethod(LookupSimulator.class, "notify",
		       new Class[]{ ServiceTemplate.class, int.class,
				    RemoteEventListener.class,
				    MarshalledObject.class, long.class }),

	getMethod(LookupSimulatorProxyInterface.class, "register",
		       new Class[]{ ServiceItem.class, long.class }),
	getMethod(LookupSimulator.class, "register",
		       new Class[]{ ServiceItem.class, long.class }),

	getMethod(LookupSimulatorProxyInterface.class, "setLocator",
		       new Class[]{ LookupLocator.class }),
	getMethod(LookupSimulator.class, "setLocator",
		       new Class[]{ LookupLocator.class }),

    };

    /** Client constraints for this proxy, or null */
    private final MethodConstraints constraints;

    /**
     * Creates new ConstrainableRegistrarProxy with given server reference,
     * service ID and client constraints.
     */
    LookupSimulatorConstrainableProxy(LookupSimulator server,
				      ServiceID serviceID,
				      MethodConstraints constraints)
    {
	super((LookupSimulator) ((RemoteMethodControl) server).setConstraints(
		  ConstrainableProxyUtil.translateConstraints(
		      constraints, methodMappings)),
	      serviceID);
	this.constraints = constraints;
    }

    // javadoc inherited from RemoteMethodControl.setConstraints
    public RemoteMethodControl setConstraints(MethodConstraints constraints) {
	return new LookupSimulatorConstrainableProxy(server, serviceID, constraints);
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

    static Method getMethod(Class type, String name, Class[] paramTypes) {
        try {
            return type.getMethod(name, paramTypes);
        } catch (NoSuchMethodException e) {
            throw new AssertionError(e);
        }
    }
}
