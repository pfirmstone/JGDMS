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
import java.lang.reflect.Method;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.lease.Lease;
import net.jini.id.Uuid;

/**
 * RegistrarLeaseMap subclass that supports constraints.
 *
 * @author Sun Microsystems, Inc.
 *
 */
final class ConstrainableRegistrarLeaseMap extends RegistrarLeaseMap {

    /** Mappings between Lease methods and Registrar lease-batching methods */
    static final Method[] methodMappings = {
	Util.getMethod(Lease.class, "cancel", new Class[0]),
	Util.getMethod(Registrar.class, "cancelLeases",
		       new Class[]{ Object[].class, Uuid[].class }),

	Util.getMethod(Lease.class, "renew", new Class[]{ long.class }),
	Util.getMethod(Registrar.class, "renewLeases",
		       new Class[]{ Object[].class, Uuid[].class,
				    long[].class })
    };

    /**
     * Constructs lease map containing a mapping from the given constrainable
     * lease to the specified duration.
     */
    ConstrainableRegistrarLeaseMap(RegistrarLease lease, long duration) {
	super((Registrar)
		  ((RemoteMethodControl) lease.getRegistrar()).setConstraints(
		      ConstrainableProxyUtil.translateConstraints(
			  ((RemoteMethodControl) lease).getConstraints(),
			  methodMappings)),
	      lease,
	      duration);
    }

    /**
     * Only allow leases permitted by RegistrarLeaseMap with compatible
     * constraints.
     */
    public boolean canContainKey(Object key) {
	if (!(super.canContainKey(key) && key instanceof RemoteMethodControl))
	{
	    return false;
	}
	return ConstrainableProxyUtil.equivalentConstraints(
	    ((RemoteMethodControl) key).getConstraints(),
	    ((RemoteMethodControl) server).getConstraints(),
	    methodMappings);
    }
}
