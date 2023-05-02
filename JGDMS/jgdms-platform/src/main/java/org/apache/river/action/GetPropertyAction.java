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

package org.apache.river.action;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * A convenience class for retrieving the string value of a system
 * property as a privileged action.
 *
 * <p>An instance of this class can be used as the argument of {@link
 * AccessController#doPrivileged(PrivilegedAction)
 * AccessController.doPrivileged} or <code>
 * net.jini.security.Security#doPrivileged(PrivilegedAction) Security.doPrivileged</code>.
 *
 * <p>The following code retrieves the value of the system property
 * named <code>"prop"</code> as a privileged action:
 *
 * <pre>
 * String s = (String) String.doPrivileged(
 *                 new GetPropertyAction("prop"));
 * </pre>
 *
 * <p>If the protection domain of the immediate caller of
 * <code>doPrivileged</code> or the protection domain of this class
 * does not imply the permissions necessary for the operation, the
 * behavior is as if the system property is not defined.
 *
 * @author	Sun Microsystems, Inc.
 * 
 * @see		PrivilegedAction
 * @see		AccessController
 * <code>see		net.jini.security.Security</code>
 * @since 2.0
 **/
public class GetPropertyAction implements PrivilegedAction<String> {

    private static final Logger logger =
	Logger.getLogger("org.apache.river.action.GetPropertyAction");
    
    private final String theProp;
    private final String defaultVal;

    /**
     * Constructor that takes the name of the system property whose
     * string value needs to be determined.
     *
     * @param	theProp the name of the system property
     **/
    public GetPropertyAction(String theProp) {
	this.theProp = theProp;
	defaultVal = null;
    }

    /**
     * Constructor that takes the name of the system property and the
     * default value of that property.
     *
     * @param	theProp the name of the system property
     * @param	defaultVal the default value
     **/
    public GetPropertyAction(String theProp, String defaultVal) {
	this.theProp = theProp;
	this.defaultVal = defaultVal;
    }

    /**
     * Determines the string value of the system property whose name
     * was specified in the constructor.
     *
     * <p>If the system property is defined, then this method returns
     * its value.  Otherwise, if a default value was supplied to this
     * object's constructor, then this method returns that default
     * value, or else <code>null</code> is returned.
     *
     * @return	the string value of the system property or the default
     * value, or <code>null</code>
     **/
    @Override
    public String run() {
	try {
	    String value = System.getProperty(theProp);
	    if (value != null) {
		return value;
	    }
	} catch (SecurityException e) {
	    if (logger.isLoggable(Level.FINE)) {
		logger.logp( Level.FINE,
		    GetPropertyAction.class.toString(), "run()",
		    "security exception reading \"{0}\", returning {1}"
                    , new Object[] {theProp, defaultVal});
                throw e;
	    }             
	}
	return defaultVal;
    }
}
