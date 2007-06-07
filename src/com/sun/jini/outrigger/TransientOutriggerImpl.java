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
import javax.security.auth.login.LoginException;
import net.jini.config.ConfigurationException;
import com.sun.jini.start.LifeCycle;

/**
 * <code>OutriggerServerWrapper</code> subclass for
 * transient servers.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
class TransientOutriggerImpl extends OutriggerServerWrapper {
    /**
     * Create a new transient outrigger server.
     * @param configArgs set of strings to be used to obtain a
     *                   <code>Configuration</code>.
     * @param lifeCycle the object to notify when this
     *                  service is destroyed.
     * @throws IOException if there is problem exporting the server.
     * @throws ConfigurationException if the <code>Configuration</code> is 
     *         malformed.
     * @throws LoginException if the <code>loginContext</code> specified
     *         in the configuration is non-null and throws 
     *         an exception when login is attempted.
     */
    TransientOutriggerImpl(String[] configArgs, LifeCycle lifeCycle)
	throws IOException, ConfigurationException, LoginException
    {
	super(configArgs, lifeCycle, false);
	allowCalls();
    }
}

