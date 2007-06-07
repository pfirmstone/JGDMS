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

import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.core.lookup.ServiceID;

/**
 * Miscellaneous common utility methods.
 *
 * @author Sun Microsystems, Inc.
 *
 */
class Util {

    /**
     * Returns Method object for specified method, which should always exist.
     */
    static Method getMethod(Class type, String name, Class[] paramTypes) {
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
    static void checkRegistrantServiceID(ServiceID serviceID,
					 Logger logger,
					 Level level)
    {
	if (logger.isLoggable(level)) {
	    int variant =
		(int) (serviceID.getLeastSignificantBits() >> 62) & 0x3;
	    if (variant != 2) {
		logger.log(level, "{0} has invalid variant {1}",
			   new Object[]{ serviceID, new Integer(variant) });
	    }
	    int version =
		(int) (serviceID.getMostSignificantBits() >> 12) & 0xF;
	    if (!(version == 1 || version == 4)) {
		logger.log(level, "{0} has invalid version {1}",
			   new Object[]{ serviceID, new Integer(version) });
	    }
	}
    }
}
