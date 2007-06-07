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
package com.sun.jini.outrigger;

import java.io.IOException;
import java.rmi.Remote;
import java.rmi.MarshalledObject;
import java.rmi.activation.ActivationID;
import java.rmi.activation.ActivationException;
import javax.security.auth.login.LoginException;
import net.jini.config.ConfigurationException;
import com.sun.jini.start.LifeCycle;

/**
 * <code>OutriggerServerWrapper</code> subclass for
 * persistent servers.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
class PersistentOutriggerImpl extends OutriggerServerWrapper {
    /**
     * Create a new incarnation of an activatable
     * <code>OutriggerServerImpl</code> server.
     * @param activationID of the server, may be <code>null</code>.
     * @param data an array of <code>String</code>s (packaged in
     *        a marshalled object) that will be used 
     *        to obtain a <code>Configuration</code>.
     * @throws IOException if there is problem recovering data
     *         from disk, exporting the server, or unpacking
     *         <code>data</code>.
     * @throws ClassCastException if the value of <code>data.get()</code>
     *         is not an array of <code>String</code>s.
     * @throws ConfigurationException if the <code>Configuration</code> is 
     *         malformed.  
     * @throws ActivationException if activatable and there
     *         is a problem getting a reference to the activation system.
     * @throws LoginException if the <code>loginContext</code> specified
     *         in the configuration is non-null and throws 
     *         an exception when login is attempted.
     * @throws ClassNotFoundException if the classes of the objects
     *         encapsulated inside <code>data</code> can not be found.
     */
    PersistentOutriggerImpl(ActivationID activationID, 
			    MarshalledObject data) 
	throws IOException, ConfigurationException, LoginException,
	       ActivationException, ClassNotFoundException
    {
	super(activationID, (String[])data.get());
	allowCalls();
    }

    /**
     * Create a new non-activatable, persistent space.
     * The space will be implemented by a new
     * <code>OutriggerServerImpl()</code> server instance.
     * @param configArgs set of strings to be used to obtain a
     *                   <code>Configuration</code>.
     * @param lifeCycle the object to notify when this
     *                  service is destroyed.
     * @throws IOException if there is problem recovering data
     *         from disk or exporting the server for the space.
     * @throws ConfigurationException if the configuration is 
     *         malformed.  
     * @throws LoginException if the <code>loginContext</code> specified
     *         in the configuration is non-null and throws 
     *         an exception when login is attempted.
     */
    PersistentOutriggerImpl(String[] configArgs, LifeCycle lifeCycle) 
        throws IOException, ConfigurationException, LoginException
    {
	super(configArgs, lifeCycle, true);
	allowCalls();
    }
}

