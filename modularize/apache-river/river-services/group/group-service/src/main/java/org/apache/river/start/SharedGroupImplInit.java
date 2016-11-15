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

package org.apache.river.start;

import java.rmi.activation.ActivationGroup;
import java.rmi.activation.ActivationID;
import java.rmi.activation.ActivationSystem;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.util.logging.Level;
import javax.security.auth.login.LoginContext;
import net.jini.activation.ActivationExporter;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.export.Exporter;
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.tcp.TcpServerEndpoint;
import net.jini.security.BasicProxyPreparer;
import net.jini.security.ProxyPreparer;

/**
 *  This is created and discarded during construction of (@link SharedGroupImpl).
 */
class SharedGroupImplInit {
    ActivationSystem activationSystem;
    ActivationID activationID;
    Exporter exporter;
    AccessControlContext context;
    LoginContext loginContext;
    
    SharedGroupImplInit(Configuration config, ActivationID id, LoginContext LoginContext) throws Exception {
        ProxyPreparer activationSystemPreparer =
            (ProxyPreparer) config.getEntry(
                SharedGroupImpl.START_PACKAGE, "activationSystemPreparer",
                ProxyPreparer.class, new BasicProxyPreparer());
	if (activationSystemPreparer == null) {
             throw new ConfigurationException(SharedGroupImpl.START_PACKAGE 
	     + ".activationSystemPreparer entry should not be null");
        }
        SharedGroupImpl.logger.log(Level.FINE, SharedGroupImpl.START_PACKAGE + ".activationSystemPreparer: {0}",
            activationSystemPreparer);
	    
	ProxyPreparer activationIdPreparer = (ProxyPreparer)
	    config.getEntry(SharedGroupImpl.START_PACKAGE, "activationIdPreparer",
	    ProxyPreparer.class, new BasicProxyPreparer());
	if (activationIdPreparer == null) {
             throw new ConfigurationException(SharedGroupImpl.START_PACKAGE 
	     + ".activationIdPreparer entry should not be null");
        }
        SharedGroupImpl.logger.log(Level.FINE, SharedGroupImpl.START_PACKAGE + ".activationIdPreparer: {0}",
            activationIdPreparer);
	    
        // Prepare activation subsystem
	/*
	 * ActivationGroup is trusted and returned ActivationSystem
	 * might already have been prepared by the group itself.
	 */
	activationSystem = (ActivationSystem) 
	    activationSystemPreparer.prepareProxy(
                ActivationGroup.getSystem());
        SharedGroupImpl.logger.log(Level.FINE, "Prepared ActivationSystem: {0}",
            activationSystem);
	activationID = (ActivationID)  
	    activationIdPreparer.prepareProxy(id);
        SharedGroupImpl.logger.log(Level.FINEST, "Prepared ActivationID: {0}",
            activationID);

        /**
	 * Would like to get this entry sooner, but need to use
	 * the prepared activationID.
	 */
	exporter = (Exporter) config.getEntry(
                SharedGroupImpl.START_PACKAGE, "exporter", Exporter.class, 
		new ActivationExporter(
		    activationID,
		    new BasicJeriExporter(
			TcpServerEndpoint.getInstance(0), 
			new BasicILFactory(), false, true)),
		activationID);
	if (exporter == null) {
             throw new ConfigurationException(SharedGroupImpl.START_PACKAGE 
	     + ".exporter entry should not be null");
        }
        SharedGroupImpl.logger.log(Level.FINE, SharedGroupImpl.START_PACKAGE + ".exporter: {0}",
            exporter);
	context = AccessController.getContext();
	
    }
    
}
