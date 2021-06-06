/*
 * Copyright 2018 peter.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.river.outrigger;

import java.io.IOException;
import net.jini.activation.arg.ActivationException;
import net.jini.activation.arg.ActivationID;
import javax.security.auth.login.LoginException;
import net.jini.activation.arg.MarshalledObject;
import net.jini.config.ConfigurationException;
import net.jini.export.DynamicProxyCodebaseAccessor;

/**
 *
 * @author peter
 */
public class ActivatableOutriggerImpl extends PersistentOutriggerImpl  
			      implements DynamicProxyCodebaseAccessor {

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
    public ActivatableOutriggerImpl(ActivationID activationID, 
				    MarshalledObject data) 
	    throws IOException, ConfigurationException, LoginException,
	    ActivationException, ClassNotFoundException 
    {
	super(activationID, data);
    }
    
}
