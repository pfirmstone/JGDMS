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
package com.sun.jini.start;

import java.io.IOException;
import net.jini.activation.ActivationExporter;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationProvider;
import net.jini.config.ConfigurationException;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.export.Exporter;
import net.jini.export.ProxyAccessor;
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.tcp.TcpServerEndpoint;
import net.jini.security.BasicProxyPreparer;
import net.jini.security.ProxyPreparer;
import net.jini.security.TrustVerifier;
import net.jini.security.proxytrust.ServerProxyTrust;

import java.rmi.MarshalledObject;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.activation.ActivationGroup;
import java.rmi.activation.ActivationID;
import java.rmi.activation.ActivationSystem;
import java.rmi.activation.ActivationException;
import java.rmi.server.ExportException;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;


abstract class AbstractSharedGroup 
    implements SharedGroupBackEnd, ServerProxyTrust, ProxyAccessor 
{

    /** Component name for configuration entries */
    static final String START_PACKAGE = "com.sun.jini.start";

    /** Configure logger */
    static final Logger logger =
        Logger.getLogger(START_PACKAGE + ".sharedGroup");

    /** Our prepared activation ID reference */
    private final ActivationID activationID;

    /** The prepared activation system reference */
    private volatile ActivationSystem activationSystem;
    
    /** The inner proxy of this server */
    private Remote ourStub;

    /** <code>LoginContext</code> for this service. */
    private final LoginContext loginContext;
    
    /** The exporter for exporting and unexporting */
    protected final Exporter exporter;
    
    private final AccessControlContext context;

    /**
     * Activation constructor. 
     */
    AbstractSharedGroup(ActivationID activationID, MarshalledObject data)
	throws Exception
    {
	this(getInit(activationID, data));
    }
    
    private static AbstractSharedGroupInit getInit(ActivationID activationID, 
                                                            MarshalledObject data)
            throws IOException, ClassNotFoundException, ConfigurationException, Exception
    {
        LoginContext loginContext = null;
        try {
            logger.entering(AbstractSharedGroup.class.getName(), "SharedGroupImpl", 
                new Object[] { activationID, data}); 
            String[] configArgs = (String[])data.get();	
            Configuration config = ConfigurationProvider.getInstance(configArgs);
            loginContext = (LoginContext) config.getEntry(
                START_PACKAGE, "loginContext", LoginContext.class, null);
            AbstractSharedGroupInit init = null;
                if (loginContext != null) {
                    init = doInitWithLogin(config, activationID, loginContext);
                } else {
                    init = doInit(config, activationID, null);
                }
            return init;
        } catch (Exception e) {
            // If we get here an instance of AbstractSharedGroup hasn't been
            // created, it's constructor won't be called and exporting doesn't
            // occur.
            if (loginContext != null) {
                try {
                    loginContext.logout();
                    logger.finest("SharedGroupImpl logged-out.");
                } catch (Exception ex) {
                    logger.log(Level.FINEST, 
                        "Problem logging out for SharedGroupImpl.", ex);
                }
            }
            throw e;
        } finally {
            logger.exiting(AbstractSharedGroup.class.getName(), "SharedGroupImpl");
        }
    }
    
    private AbstractSharedGroup(AbstractSharedGroupInit init){
            activationSystem = init.activationSystem;
            this.activationID = init.activationID;
            exporter = init.exporter;
            context = init.context;
            loginContext = init.loginContext;
    }       

    private static AbstractSharedGroupInit doInitWithLogin(final Configuration config, 
                                                    final ActivationID id, 
                                                    final LoginContext loginContext) 
                                                throws Exception
    {
        loginContext.login();
        try {
            return Subject.doAsPrivileged(
                loginContext.getSubject(),
                new PrivilegedExceptionAction<AbstractSharedGroupInit>() {
                    public AbstractSharedGroupInit run() throws Exception {
                        doInit(config, id, loginContext);
                        return null;
                    }
                },
                null);
        } catch (PrivilegedActionException e) {
            throw e.getException();
        }
    }
    
    private static AbstractSharedGroupInit doInit(Configuration config, 
                                                    ActivationID id, 
                                                    LoginContext loginContext) 
                                                            throws Exception 
    {
        return new AbstractSharedGroupInit(config, id, loginContext);
    }
    
    synchronized void export() throws ExportException {
        try {
            // Export service
            
            ourStub = AccessController.doPrivileged(new PrivilegedExceptionAction<Remote>(){

                @Override
                public Remote run() throws ExportException {
                    return exporter.export(AbstractSharedGroup.this);	
                }
                
            }, context);
        } catch (PrivilegedActionException ex) {
            ExportException e = (ExportException) ex.getException();
            cleanup();
            throw e;
        }
                	
        logger.log(Level.FINEST, "Exported service proxy: {0}",
            ourStub);
    }

    // javadoc inherited from supertype
    public void destroyVM() throws RemoteException, ActivationException {
	logger.entering(AbstractSharedGroup.class.getName(), "destroyVM"); 
	/*
	 * Would like to synch access to activationSystem, but need
	 * to avoid holding locks across remote invocations.
	 */
        if (activationSystem != null) {
	    activationSystem.unregisterGroup(
		ActivationGroup.currentGroupID());
                logger.finest("ActivationGroup unregistered.");
		/* Unregistering the group implicitly unregisters
		 * all the objects associated with that group as well.
		 */
	    activationSystem = null;     
	}
        (new DestroyThread()).start();
	logger.exiting(AbstractSharedGroup.class.getName(), "destroyVM"); 
    }

    /**
     * Private utility method which attempts to roll back from 
     * from a failed initialization attempt. 
     */
    private void cleanup() {
	logger.entering(AbstractSharedGroup.class.getName(), "cleanup"); 
        /* 
	 * Caller decides whether or not to unregister this object.
	 */
        if (exporter != null) {
            try {
	        // Unexport object so that no new calls come in
	        exporter.unexport(true);
                logger.finest("SharedGroupImpl unexported.");
	    } catch (Exception e) {
                logger.log(Level.FINEST, 
		    "Problem unexporting SharedGroupImpl.", e);
	    }
        }
		
	if (loginContext != null) {
	    try {
		loginContext.logout();
                logger.finest("SharedGroupImpl logged-out.");
	    } catch (Exception e) {
                logger.log(Level.FINEST, 
		    "Problem logging out for SharedGroupImpl.", e);
	    }
	}
	logger.exiting(AbstractSharedGroup.class.getName(), "cleanup"); 
    }

    /**
     * Termination thread code.  We do this in a separate thread to
     * allow the calling thread to return normally. This is not guaranteed
     * since it's still possible for the VM to exit before the calling
     * thread returns.
     */
    private class DestroyThread extends Thread {

        /** Create a non-daemon thread */
        public DestroyThread() {
            super("DestroyThread");
            /* override inheritance from RMI daemon thread */
            setDaemon(false);
        }

        public void run() {
            logger.entering(DestroyThread.class.getName(), 
                "run");
 
            logger.finest("Calling System.exit() ...");

	    /*
	     * Forcefully destroy the VM, in case there are any lingering 
	     * threads.
	     */
	    System.exit(0);
        }
    }
    // inherit javadoc
    public synchronized Object getProxy() {
        logger.entering(AbstractSharedGroup.class.getName(), 
                "getProxy");
        logger.exiting(AbstractSharedGroup.class.getName(), 
                "getProxy", ourStub);
	return ourStub;
    }
    
    //////////////////////////////////////////
    // ProxyTrust Method
    //////////////////////////////////////////
    //inherit javadoc
    public synchronized TrustVerifier getProxyVerifier( ) {
        /* No verifier if the server isn't secure */
        if (!(ourStub instanceof RemoteMethodControl)) {
            throw new UnsupportedOperationException();
        } else {
            return new ProxyVerifier((SharedGroupBackEnd)ourStub);
        }
    }
}
