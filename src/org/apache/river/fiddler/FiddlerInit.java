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

package org.apache.river.fiddler;

import java.io.IOException;
import java.rmi.activation.ActivationException;
import java.rmi.activation.ActivationGroup;
import java.rmi.activation.ActivationID;
import java.rmi.activation.ActivationSystem;
import java.rmi.server.ExportException;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import javax.security.auth.login.LoginContext;
import net.jini.activation.ActivationExporter;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.config.NoSuchEntryException;
import net.jini.core.discovery.LookupLocator;
import net.jini.discovery.DiscoveryGroupManagement;
import net.jini.discovery.DiscoveryLocatorManagement;
import net.jini.discovery.DiscoveryManagement;
import net.jini.discovery.LookupDiscoveryManager;
import net.jini.export.Exporter;
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.InvocationLayerFactory;
import net.jini.jeri.ServerEndpoint;
import net.jini.jeri.tcp.TcpServerEndpoint;
import net.jini.security.BasicProxyPreparer;
import net.jini.security.ProxyPreparer;
import org.apache.river.config.Config;
import org.apache.river.fiddler.FiddlerImpl.LocalLogHandler;
import org.apache.river.reliableLog.ReliableLog;
import org.apache.river.thread.NamedThreadFactory;

/**
 * Initialization common to all modes in which instances of this service
 *  runs: activatable/persistent, non-activatable/persistent, and
 *  transient (non-activatable /non-persistent).
 *
 */
class FiddlerInit {
    LookupDiscoveryManager discoveryMgr;
    ProxyPreparer listenerPreparer;
    ProxyPreparer locatorToJoinPreparer;
    ProxyPreparer locatorToDiscoverPreparer;
    ProxyPreparer recoveredListenerPreparer;
    ProxyPreparer recoveredLocatorToJoinPreparer;
    ProxyPreparer recoveredLocatorToDiscoverPreparer;
    String persistDir;
    ReliableLog log = null;
    DiscoveryManagement joinMgrLDM;
    long leaseMax;
    ExecutorService executorService;
    ActivationSystem activationSystem;
    Exporter serverExporter;
    LocalLogHandler logHandler;
    ActivationID activationID = null;
    // These three fields are used by the Starter.start() implementation.
    boolean persistent;
    Configuration config;
    AccessControlContext context;
    LoginContext loginContext;
    
    FiddlerInit(Configuration config,
                boolean persistent, 
                ActivationID activID, 
                LoginContext loginContext) throws IOException,
                                                        ConfigurationException,
                                                        ActivationException
    {
        try {
            this.config = config;
            this.persistent = persistent;
            this.loginContext = loginContext;
            context = AccessController.getContext();
           /* Note that two discovery managers are retrieved/created in this
             * method. One manager is used internally by this service to provide
             * the lookup discovery capabilities offered to the clients that
             * register to use this service. That manager (discoveryMgr) is
             * considered part of this service's implementation, and thus is
             * created rather than retrieved from the configuration. When
             * created here, this manager is configured to initially discover
             * NO_GROUPS and NO LOCATORS. The sets of groups and locators to
             * discover on behalf of the registered clients will be populated
             * during recovery; and then the discovery mechanism provided by
             * this manager will be started later, after recovery and
             * configuration retrieval has occurred, and after this service
             * has been exported. Thus, it is important that this manager be
             * created prior to recovery; as is done below.
             * 
             * The second discovery manager (joinMgrLDM) is passed to the join
             * manager that is employed by this service to advertise itself to
             * clients through lookup services. This discovery manager is
             * retrieved from the configuration, and must satisfy the following
             * requirements: must be an instance of both DiscoveryGroupManagement
             * and DiscoveryLocatorManagement, and should be initially configured
             * to discover NO_GROUPS and NO LOCATORS. This discovery manager is
             * retrieved from the configuration after recovery of any persistent
             * state, and after retrieval of any initial configuration items.
             */
            discoveryMgr = new LookupDiscoveryManager
                                              (DiscoveryGroupManagement.NO_GROUPS,
                                               new LookupLocator[0],
                                               null,
                                               config);

            /* Get the proxy preparers for the remote event listeners */
            listenerPreparer = (ProxyPreparer)Config.getNonNullEntry
                                                       (config,
                                                        FiddlerImpl.COMPONENT_NAME,
                                                        "listenerPreparer",
                                                        ProxyPreparer.class,
                                                        new BasicProxyPreparer());
            /* Get the proxy preparers for the lookup locators to join */
            locatorToJoinPreparer = (ProxyPreparer)Config.getNonNullEntry
                                                       (config,
                                                        FiddlerImpl.COMPONENT_NAME,
                                                        "locatorToJoinPreparer",
                                                        ProxyPreparer.class,
                                                        new BasicProxyPreparer());
            /* Get the proxy preparers for the lookup locators to discover */
            locatorToDiscoverPreparer = (ProxyPreparer)Config.getNonNullEntry
                                                     (config,
                                                      FiddlerImpl.COMPONENT_NAME,
                                                      "locatorToDiscoverPreparer",
                                                      ProxyPreparer.class,
                                                      new BasicProxyPreparer());
            if(persistent) {
                /* Retrieve the proxy preparers that will only be applied during
                 * the state recovery process below, when any previously stored
                 * listeners or locators are recovered from persistent storage.
                 */
                recoveredListenerPreparer = 
                 (ProxyPreparer)Config.getNonNullEntry(config,
                                                       FiddlerImpl.COMPONENT_NAME,
                                                       "recoveredListenerPreparer",
                                                       ProxyPreparer.class,
                                                       new BasicProxyPreparer());
                recoveredLocatorToJoinPreparer =
                 (ProxyPreparer)Config.getNonNullEntry
                                                 (config,
                                                  FiddlerImpl.COMPONENT_NAME,
                                                  "recoveredLocatorToJoinPreparer",
                                                  ProxyPreparer.class,
                                                  new BasicProxyPreparer());
                recoveredLocatorToDiscoverPreparer = 
                 (ProxyPreparer)Config.getNonNullEntry
                                             (config,
                                              FiddlerImpl.COMPONENT_NAME,
                                              "recoveredLocatorToDiscoverPreparer",
                                              ProxyPreparer.class,
                                              new BasicProxyPreparer());


                /* Get the log directory for persisting this service's state */
                persistDir = (String)Config.getNonNullEntry(config,
                                                            FiddlerImpl.COMPONENT_NAME,
                                                            "persistenceDirectory",
                                                            String.class);
                /* Recover the state that was persisted on prior runs (if any) */
                logHandler = new LocalLogHandler();
                log = new ReliableLog(persistDir, logHandler);
            }//endif(persistent)

            /* Get the various configurable constants */
            leaseMax = Config.getLongEntry(config,
                                           FiddlerImpl.COMPONENT_NAME,
                                           "leaseMax",
                                           FiddlerImpl.MAX_LEASE, 0, Long.MAX_VALUE);

            /* Get a general-purpose task manager for this service 
             * LinkedBlockingQueue is unbounded for that reason*/
            executorService = Config.getNonNullEntry(config,
                                             FiddlerImpl.COMPONENT_NAME,
                                             "executorService",
                                             ExecutorService.class,
                                             new ThreadPoolExecutor(10,10,15,TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(), new NamedThreadFactory("Fiddler Executor", false)) );
            /* Get the discovery manager to pass to this service's join manager. */
            try {
                joinMgrLDM = Config.getNonNullEntry(config,
                                                    FiddlerImpl.COMPONENT_NAME,
                                                    "discoveryManager",
                                                    DiscoveryManagement.class);
                if( joinMgrLDM instanceof DiscoveryGroupManagement ) {
                    String[] groups0 =
                               ((DiscoveryGroupManagement)joinMgrLDM).getGroups();
                    if(    (groups0 == DiscoveryGroupManagement.ALL_GROUPS)
                        || (groups0.length != 0) )
                    {
                        throw new ConfigurationException
                                     ("discoveryManager entry must be configured "
                                      +"to initially discover/join NO_GROUPS");
                    }//endif
                } else {// !(joinMgrLDM instanceof DiscoveryGroupManagement)
                    throw new ConfigurationException
                                           ("discoveryManager entry must "
                                            +"implement DiscoveryGroupManagement");
                }//endif
                if( joinMgrLDM instanceof DiscoveryLocatorManagement ) {
                    LookupLocator[] locs0 =
                            ((DiscoveryLocatorManagement)joinMgrLDM).getLocators();
                    if( (locs0 != null) && (locs0.length != 0) ) {
                        throw new ConfigurationException
                                     ("discoveryManager entry must be configured "
                                      +"to initially discover/join no locators");
                    }//endif
                } else {// !(joinMgrLDM instanceof DiscoveryLocatorManagement)
                    throw new ConfigurationException
                                         ("discoveryManager entry must "
                                          +"implement DiscoveryLocatorManagement");
                }//endif
            } catch (NoSuchEntryException e) {
                joinMgrLDM
                   = new LookupDiscoveryManager(DiscoveryGroupManagement.NO_GROUPS,
                                                new LookupLocator[0], null,config);
            }

            /* Handle items and duties related to exporting this service. */
            ServerEndpoint endpoint = TcpServerEndpoint.getInstance(0);
            InvocationLayerFactory ilFactory = new BasicILFactory();
            Exporter defaultExporter = new BasicJeriExporter(endpoint,
                                                             ilFactory,
                                                             false,
                                                             true);
            /* For the activatable server */
            if(activID != null) {
                ProxyPreparer aidPreparer =
                  (ProxyPreparer)Config.getNonNullEntry(config,
                                                        FiddlerImpl.COMPONENT_NAME,
                                                        "activationIdPreparer",
                                                        ProxyPreparer.class,
                                                        new BasicProxyPreparer());
                ProxyPreparer aSysPreparer = 
                  (ProxyPreparer)Config.getNonNullEntry(config,
                                                        FiddlerImpl.COMPONENT_NAME,
                                                        "activationSystemPreparer",
                                                        ProxyPreparer.class,
                                                        new BasicProxyPreparer());
                activationID = (ActivationID)aidPreparer.prepareProxy
                                                                   (activID);
                activationSystem = (ActivationSystem)aSysPreparer.prepareProxy
                                                                (ActivationGroup.getSystem());
                defaultExporter = new ActivationExporter(activationID,
                                                         defaultExporter);
            }//endif(activationID != null)

            /* Get the exporter that will be used to export this service */
            try {
                serverExporter = (Exporter)Config.getNonNullEntry(config,
                                                                  FiddlerImpl.COMPONENT_NAME,
                                                                  "serverExporter",
                                                                  Exporter.class,
                                                                  defaultExporter,
                                                                  activationID);
            } catch(ConfigurationException e) {// exception, use default
                throw new ExportException("Configuration exception while "
                                          +"retrieving service's exporter",
                                          e);
            }
        } catch(Throwable e) {
            cleanupInitFailure();
            handleActivatableInitThrowable(e);
        }
        
    }
    
    /* Called in the constructor when failure occurs during the initialization
     * process. Un-does any work that may have already been completed; for
     * example, un-exports the service if it has already been exported,
     * terminates any threads that may have been started, etc.
     */
    private void cleanupInitFailure() {
        if(executorService != null)  {
            try {
                executorService.shutdown();
            } catch(Throwable t) { }
        }//endif
        
        if(joinMgrLDM != null)  {
            try {
                joinMgrLDM.terminate();
            } catch(Throwable t) { }
        }//endif

        if(discoveryMgr != null)  {
            try {
                discoveryMgr.terminate();
            } catch(Throwable t) { }
        }//endif
        // No threads started, no need to interrupt.
    }//end cleanupInitFailure
    
    /* Convenience method called in the constructor or the activatable version
     * of this service when failure occurs during the initialization process.
     * Logs and rethrows the given <code>Throwable</code> so the constructor
     * doesn't have to.
     */
    private static void handleActivatableInitThrowable(Throwable t) 
                                            throws IOException,
                                                   ActivationException,
                                                   ConfigurationException
    {
        handleInitThrowable(t);
        if (t instanceof ActivationException) {
            throw (ActivationException)t;
        } else {
            throw new AssertionError(t);
        }//endif
    }//end handleInitThrowable
    
    /* Convenience method called in the constructor or the non-activatable 
     * version of this service when failure occurs during the initialization
     * process. Logs and rethrows the given <code>Throwable</code> so the
     * constructor doesn't have to.
     */
    private static void handleInitThrowable(Throwable t) 
                                            throws IOException,
                                                   ConfigurationException
    {
        FiddlerImpl.problemLogger.log(Level.SEVERE, "cannot initialize the service", t);
        if (t instanceof IOException) {
            throw (IOException)t;
        } else if (t instanceof ConfigurationException) {
            throw (ConfigurationException)t;
        } else if (t instanceof RuntimeException) {
            throw (RuntimeException)t;
        } else if (t instanceof Error) {
            throw (Error)t;
        }//endif
    }//end handleInitThrowable
}
