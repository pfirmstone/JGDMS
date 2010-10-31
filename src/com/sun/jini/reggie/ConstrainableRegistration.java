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
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.entry.Entry;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceRegistration;
import net.jini.id.Uuid;
import net.jini.security.proxytrust.ProxyTrustIterator;
import net.jini.security.proxytrust.SingletonProxyTrustIterator;

/**
 * Registration subclass that supports constraints.
 *
 * @author Sun Microsystems, Inc.
 *
 */
final class ConstrainableRegistration
    extends Registration implements RemoteMethodControl
{
    private static final long serialVersionUID = 2L;

    /** Mappings between ServiceRegistration and Registrar methods */
    private static final Method[] methodMappings = {
	Util.getMethod(ServiceRegistration.class, "addAttributes",
		       new Class[]{ Entry[].class }),
	Util.getMethod(Registrar.class, "addAttributes",
		       new Class[]{ ServiceID.class, Uuid.class,
				    EntryRep[].class }),

	Util.getMethod(ServiceRegistration.class, "modifyAttributes",
		       new Class[]{ Entry[].class, Entry[].class }),
	Util.getMethod(Registrar.class, "modifyAttributes",
		       new Class[]{ ServiceID.class, Uuid.class,
				    EntryRep[].class, EntryRep[].class }),

	Util.getMethod(ServiceRegistration.class, "setAttributes",
		       new Class[]{ Entry[].class }),
	Util.getMethod(Registrar.class, "setAttributes",
		       new Class[]{ ServiceID.class, Uuid.class,
				    EntryRep[].class }),
    };

    /** Client constraints for this proxy, or null */
    private final MethodConstraints constraints;

    /**
     * Creates new ConstrainableRegistration with given server reference,
     * service lease and client constraints.
     */
    ConstrainableRegistration(Registrar server,
			      ServiceLease lease,
			      MethodConstraints constraints)
    {
	super((Registrar) ((RemoteMethodControl) server).setConstraints(
		  ConstrainableProxyUtil.translateConstraints(
		      constraints, methodMappings)),
	      lease);
	this.constraints = constraints;
    }

    // javadoc inherited from RemoteMethodControl.setConstraints
    public RemoteMethodControl setConstraints(MethodConstraints constraints) {
	return new ConstrainableRegistration(server, lease, constraints);
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
