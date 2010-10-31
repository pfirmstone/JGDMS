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

import com.sun.jini.admin.DestroyAdmin;
import com.sun.jini.proxy.ConstrainableProxyUtil;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.lang.reflect.Method;
import net.jini.admin.JoinAdmin;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.discovery.LookupLocator;
import net.jini.core.entry.Entry;
import net.jini.core.lookup.ServiceID;
import net.jini.lookup.DiscoveryAdmin;
import net.jini.security.proxytrust.ProxyTrustIterator;
import net.jini.security.proxytrust.SingletonProxyTrustIterator;

/**
 * AdminProxy subclass that supports constraints.
 *
 * @author Sun Microsystems, Inc.
 *
 */
final class ConstrainableAdminProxy
    extends AdminProxy implements RemoteMethodControl
{
    private static final long serialVersionUID = 2L;

    /** Mappings between public admin methods and Registrar methods */
    private static final Method[] methodMappings = {
	Util.getMethod(DiscoveryAdmin.class, "addMemberGroups",
		       new Class[]{ String[].class }),
	Util.getMethod(DiscoveryAdmin.class, "addMemberGroups",
		       new Class[]{ String[].class }),

	Util.getMethod(DiscoveryAdmin.class, "getMemberGroups", new Class[0]),
	Util.getMethod(DiscoveryAdmin.class, "getMemberGroups", new Class[0]),

	Util.getMethod(DiscoveryAdmin.class, "getUnicastPort", new Class[0]),
	Util.getMethod(DiscoveryAdmin.class, "getUnicastPort", new Class[0]),

	Util.getMethod(DiscoveryAdmin.class, "removeMemberGroups",
		       new Class[]{ String[].class }),
	Util.getMethod(DiscoveryAdmin.class, "removeMemberGroups",
		       new Class[]{ String[].class }),

	Util.getMethod(DiscoveryAdmin.class, "setMemberGroups",
		       new Class[]{ String[].class }),
	Util.getMethod(DiscoveryAdmin.class, "setMemberGroups",
		       new Class[]{ String[].class }),

	Util.getMethod(DiscoveryAdmin.class, "setUnicastPort",
		       new Class[]{ int.class }),
	Util.getMethod(DiscoveryAdmin.class, "setUnicastPort",
		       new Class[]{ int.class }),

	Util.getMethod(JoinAdmin.class, "addLookupAttributes",
		       new Class[]{ Entry[].class }),
	Util.getMethod(JoinAdmin.class, "addLookupAttributes",
		       new Class[]{ Entry[].class }),

	Util.getMethod(JoinAdmin.class, "addLookupGroups",
		       new Class[]{ String[].class }),
	Util.getMethod(JoinAdmin.class, "addLookupGroups",
		       new Class[]{ String[].class }),

	Util.getMethod(JoinAdmin.class, "addLookupLocators",
		       new Class[]{ LookupLocator[].class }),
	Util.getMethod(JoinAdmin.class, "addLookupLocators",
		       new Class[]{ LookupLocator[].class }),

	Util.getMethod(JoinAdmin.class, "getLookupAttributes", new Class[0]),
	Util.getMethod(JoinAdmin.class, "getLookupAttributes", new Class[0]),

	Util.getMethod(JoinAdmin.class, "getLookupGroups", new Class[0]),
	Util.getMethod(JoinAdmin.class, "getLookupGroups", new Class[0]),

	Util.getMethod(JoinAdmin.class, "getLookupLocators", new Class[0]),
	Util.getMethod(JoinAdmin.class, "getLookupLocators", new Class[0]),

	Util.getMethod(JoinAdmin.class, "modifyLookupAttributes",
		       new Class[]{ Entry[].class, Entry[].class }),
	Util.getMethod(JoinAdmin.class, "modifyLookupAttributes",
		       new Class[]{ Entry[].class, Entry[].class }),

	Util.getMethod(JoinAdmin.class, "removeLookupGroups",
		       new Class[]{ String[].class }),
	Util.getMethod(JoinAdmin.class, "removeLookupGroups",
		       new Class[]{ String[].class }),

	Util.getMethod(JoinAdmin.class, "removeLookupLocators",
		       new Class[]{ LookupLocator[].class }),
	Util.getMethod(JoinAdmin.class, "removeLookupLocators",
		       new Class[]{ LookupLocator[].class }),

	Util.getMethod(JoinAdmin.class, "setLookupGroups",
		       new Class[]{ String[].class }),
	Util.getMethod(JoinAdmin.class, "setLookupGroups",
		       new Class[]{ String[].class }),

	Util.getMethod(JoinAdmin.class, "setLookupLocators",
		       new Class[]{ LookupLocator[].class }),
	Util.getMethod(JoinAdmin.class, "setLookupLocators",
		       new Class[]{ LookupLocator[].class }),

	Util.getMethod(DestroyAdmin.class, "destroy", new Class[0]),
	Util.getMethod(DestroyAdmin.class, "destroy", new Class[0])
    };

    /** Client constraints for this proxy, or null */
    private final MethodConstraints constraints;

    /**
     * Creates new ConstrainableAdminProxy with given server reference, service
     * ID and client constraints.
     */
    ConstrainableAdminProxy(Registrar server,
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
	return new ConstrainableAdminProxy(server, registrarID, constraints);
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
