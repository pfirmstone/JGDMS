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
package org.apache.river.reggie.proxy;

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.MarshallingFormat;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.lookup.ServiceID;

/**
 * Miscellaneous common utility methods.
 *
 * @author Sun Microsystems, Inc.
 *
 */
public class Util {

    /**
     * Returns {@code true} if {@code obj} implements {@link RemoteMethodControl}
     * and any of its method constraints require {@link MarshallingFormat#ATOMIC_DER}.
     * <p>
     * Used to derive, per client&lt;-&gt;server relationship, whether {@link
     * org.apache.river.reggie.proxy.EntryRep}/{@link org.apache.river.reggie.proxy.Item}
     * should marshal entry field / service payloads via DER
     * ({@code net.jini.io.MarshalledInstance} +
     * {@code InvocationConstraints(MarshallingFormat.ATOMIC_DER, null)}) instead of the
     * legacy JOSS {@code AtomicMarshalledInstance} form.
     * <p>
     * This must be derived per-instance from the object's own constraints -- never
     * cached as a JVM-global flag -- because {@code MarshalledWrapper.equals()} (and
     * transitively {@code EntryRep.equals()}/{@code matchEntry()}) perform a raw byte
     * comparison of the wrapped {@code MarshalledInstance}: two logically-identical
     * entries encoded with different inner formats will silently fail to match rather
     * than throwing, so the format choice must track which Reggie relationship (proxy,
     * registration) the payload is destined for.
     *
     * @param obj a proxy or registration object whose format requirement is being
     *            queried (typically {@code this} at a client-side construction site)
     * @return {@code true} if DER-tagged marshalling should be used
     */
    static boolean requiresDerFormat(Object obj) {
	if (!(obj instanceof RemoteMethodControl)) return false;
	MethodConstraints mc = ((RemoteMethodControl) obj).getConstraints();
	if (mc == null) return false;
	Iterator<InvocationConstraints> iter = mc.possibleConstraints();
	while (iter.hasNext()) {
	    InvocationConstraints ic = iter.next();
	    if (ic.requirements().contains(MarshallingFormat.ATOMIC_DER)) return true;
	}
	return false;
    }

    /**
     * Returns Method object for specified method, which should always exist.
     */
    public static Method getMethod(Class type, String name, Class[] paramTypes) {
	try {
	    return type.getMethod(name, paramTypes);
	} catch (NoSuchMethodException e) {
	    throw new AssertionError(e);
	}
    }

    /**
     * Checks if the value of the given service ID to register conforms to the
     * ServiceID specification, logging a message to the provided logger at the
     * specified logging level if it doesn't.
     */
    public static void checkRegistrantServiceID(ServiceID serviceID,
					 Logger logger,
					 Level level)
    {
	if (logger.isLoggable(level)) {
	    int variant =
		(int) (serviceID.getLeastSignificantBits() >> 62) & 0x3;
	    if (variant != 2) {
		logger.log(level, "{0} has invalid variant {1}",
			   new Object[]{ serviceID, Integer.valueOf(variant) });
	    }
	    int version =
		(int) (serviceID.getMostSignificantBits() >> 12) & 0xF;
	    if (!(version == 1 || version == 4)) {
		logger.log(level, "{0} has invalid version {1}",
			   new Object[]{ serviceID, Integer.valueOf(version) });
	    }
	}
    }
}
