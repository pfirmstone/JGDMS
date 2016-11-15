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
package org.apache.river.norm;

import java.rmi.MarshalledObject;
import java.rmi.RemoteException;
import java.rmi.activation.ActivationException;
import java.rmi.activation.ActivationGroup;
import java.rmi.activation.ActivationID;
import java.rmi.activation.ActivationSystem;
import java.util.logging.Level;

import org.apache.river.config.Config;
import org.apache.river.logging.Levels;
import org.apache.river.start.ServiceStarter;
import net.jini.activation.ActivationExporter;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.export.Exporter;
import net.jini.io.MarshalledInstance;
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.tcp.TcpServerEndpoint;
import net.jini.security.BasicProxyPreparer;
import net.jini.security.ProxyPreparer;

/**
 * Provides an activatable implementation of NormServer.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
class ActivatableNormServerImpl extends NormServerBaseImpl {
    /** Our activation ID */
    private final ActivationID activationID;

    /** The activation system, prepared */
    private final ActivationSystem activationSystem;

    // Inherit java doc from super type
    public void destroy() throws RemoteException {
	try {
	    activationSystem.unregisterObject(activationID);
	    logger.log(Level.FINEST,
		       "Unregistered object with activation system");
	} catch (ActivationException e) {
	    /*
	     * Activation system is shutting down or this
	     * object has already been unregistered --
	     * ignore in either case.
	     */
	    logger.log(Levels.HANDLED,
		       "Unable to unregister object with activation system",
		       e);
	}
	super.destroy();
    }

    // inherit doc comment
    void postDestroy() {
	/* inactive will set current group ID to null */
	try {
	    net.jini.activation.ActivationGroup.inactive(
		activationID, exporter);
	    logger.log(Level.FINEST,
		       "Inactivated object with activation system");
	} catch (RemoteException e) {
	    logger.log(Levels.HANDLED,
		       "Unable to inactivate object with activation system",
		       e);
	} catch (ActivationException e) {
	    logger.log(Levels.HANDLED,
		       "Unable to inactivate object with activation system",
		       e);
	}
    }

    /**
     * Provides a constructor for an activatable implementation of NormServer
     * suitable for use with {@link ServiceStarter}.
     *
     * @param activationID activation ID passed in by the activation daemon
     * @param data state data needed to re-activate a Norm server
     * @throws Exception if there is a problem creating the server
     */
    ActivatableNormServerImpl(ActivationID activationID, MarshalledObject data)
	throws Exception
    {
        /* Any Exception thrown happens prior to super being called, so this
         * isn't susceptible to finalizer attacks.
         */
        this(init(getConfigOptions(activationID, data), new Init(true /* persistent */)));
    }
    
    private ActivatableNormServerImpl(NormServerInitializer init){
        super(init);
        this.activationSystem = ((Init) init).activationSystem;
        this.activationID = ((Init) init).activationID;
    }
    
    private static String[] getConfigOptions(ActivationID activationID, MarshalledObject data) throws Exception{
        try {
	    if (activationID == null) {
		throw new NullPointerException("activationID is null");
	    }
	    return (String[]) new MarshalledInstance(data).get(false);
	} catch (Throwable e) {
	    initFailed(e);
	}
        return new String[0]; // Unreachable, keeps compiler happy.
    }
    
    private static class Init extends NormServerInitializer {
        private ActivationSystem activationSystem;
        private ActivationID activationID;
        
        Init(boolean persistent){
            super(persistent, null);
        }
        void initAsSubject(Configuration config) throws Exception {
            ProxyPreparer activationSystemPreparer =
                (ProxyPreparer) Config.getNonNullEntry(
                    config, NORM, "activationSystemPreparer", ProxyPreparer.class,
                    new BasicProxyPreparer());
            activationSystem =
                (ActivationSystem) activationSystemPreparer.prepareProxy(
                    ActivationGroup.getSystem());
            ProxyPreparer activationIdPreparer = (ProxyPreparer)
                Config.getNonNullEntry(
                    config, NORM, "activationIdPreparer", ProxyPreparer.class,
                    new BasicProxyPreparer());
            activationID = (ActivationID) activationIdPreparer.prepareProxy(
                activationID);
            super.initAsSubject(config);
        }

        Exporter getExporter(Configuration config)
            throws ConfigurationException
        {
            Exporter result = (Exporter) Config.getNonNullEntry(
                config, NORM, "serverExporter", Exporter.class,
                new ActivationExporter(
                    activationID,
                    new BasicJeriExporter(
                        TcpServerEndpoint.getInstance(0), new BasicILFactory())),
                activationID);
            return result;
        }
    }
}
