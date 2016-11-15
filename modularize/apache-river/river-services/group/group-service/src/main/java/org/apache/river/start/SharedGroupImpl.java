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

import org.apache.river.api.util.Startable;
import java.io.IOException;
import java.rmi.MarshalledObject;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.activation.ActivationException;
import java.rmi.activation.ActivationGroup;
import java.rmi.activation.ActivationID;
import java.rmi.activation.ActivationSystem;
import java.rmi.server.ExportException;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import net.jini.activation.ActivationExporter;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.config.ConfigurationProvider;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.export.Exporter;
import net.jini.export.ProxyAccessor;
import net.jini.io.MarshalledInstance;
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.tcp.TcpServerEndpoint;
import net.jini.security.BasicProxyPreparer;
import net.jini.security.ProxyPreparer;
import net.jini.security.TrustVerifier;
import net.jini.security.proxytrust.ServerProxyTrust;

/**
 * The provided implementation
 * of the {@link SharedGroup} service.
 *
 * The following items are discussed below:
 * <ul>
 * <li><a href="#configEntries">Configuring SharedGroupImpl</a>
 * <li><a href="#logging">Logging</a>
 * </ul>
 *
 * <a name="configEntries"></a>
 * <h3>Configuring SharedGroupImpl</h3>
 *
 * This implementation of <code>SharedGroupImpl</code> supports the
 * following configuration entries, with component
 * <code>org.apache.river.start</code>:
 *
 *   <table summary="Describes the activationIdPreparer configuration entry"
 *          border="0" cellpadding="2">
 *     <tr valign="top">
 *       <th scope="col">&#X2022;
 *       <th scope="col" align="left" colspan="2"><code>
 *       activationIdPreparer</code>
 *     <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *       Type: <td> {@link net.jini.security.ProxyPreparer}
 *     <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *       Default: <td> <code>
 *         new {@link net.jini.security.BasicProxyPreparer}()
 *         </code>
 *     <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *       Description: <td> The proxy preparer for the service's activation
 *       ID. The value should not be <code>null</code>. 
 * 
 *       The service starter calls the  
 *       {@link java.rmi.activation.ActivationID#activate
 *       activate} method to activate the service.
 *   </table>
 *
 *   <table summary="Describes the activationSystemPreparer configuration
 *          entry"
 *          border="0" cellpadding="2">
 *     <tr valign="top">
 *       <th scope="col">&#X2022;
 *       <th scope="col" align="left" colspan="2"><code>
 *       activationSystemPreparer</code>
 *     <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *       Type: <td> {@link net.jini.security.ProxyPreparer}
 *     <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *       Default: <td> <code>
 *         new {@link net.jini.security.BasicProxyPreparer}()</code>
 *     <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *       Description: <td> The proxy preparer for the proxy for the
 *       activation system. The value should not be <code>null</code>. This
 *       entry is obtained at service start and restart. 
 * 
 *       The service calls the {@link
 *       java.rmi.activation.ActivationSystem#unregisterGroup
 *       unregisterGroup} method on the {@link
 *       java.rmi.activation.ActivationSystem} upon service destruction.
 *   </table>
 *
 *   <table summary="Describes the exporter configuration entry"
 *          border="0" cellpadding="2">
 *     <tr valign="top">
 *       <th scope="col">&#X2022;
 *       <th scope="col" align="left" colspan="2"><code>
 *       exporter</code>
 *     <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *       Type: <td> {@link net.jini.export.Exporter}
 *     <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *       Default: <td>
 * <pre>
 * new {@link net.jini.activation.ActivationExporter}(
 *     <i>activationID</i>,
 *     new {@link net.jini.jeri.BasicJeriExporter}(
 *         {@link net.jini.jeri.tcp.TcpServerEndpoint#getInstance TcpServerEndpoint.getInstance}(0),
 *         new {@link net.jini.jeri.BasicILFactory}(), false, true))
 * </pre>
 *     <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *       Description: <td> The object to use for exporting the service. The
 *       value should not be <code>null</code>. The call to 
 *       <code>getEntry</code> will supply the activation ID in
 *       the <code>data</code> argument. This entry is obtained at service
 *       start and restart.
 *   </table> <p>
 *  
 *   <table summary="Describes the loginContext configuration entry"
 *     border="0" cellpadding="2">
 *   <tr valign="top">
 *     <th scope="col">&#X2022;
 *     <th scope="col" align="left" colspan="2"><code>
 *   loginContext</code>
 *   <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *     Type: <td> {@link javax.security.auth.login.LoginContext}
 *   <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *     Default: <td> <code>null</code>
 *   <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *     Description: <td> If not <code>null</code>, specifies the JAAS
 *     login context to use for performing a JAAS login and supplying the
 *     {@link javax.security.auth.Subject} to use when running the
 *     service. If <code>null</code>, no JAAS login is performed. This
 *     entry is obtained at service start and restart.
 *   </table>
 *
 *
 *<a name="logging"></a>
 *<h3>Loggers and Logging Levels</h3>
 *
 *The SharedGroupImpl service implementation uses the {@link
 *java.util.logging.Logger}, named <code>org.apache.river.sharedGroup</code>. 
 *The following table describes the
 *type of information logged as well as the levels of information logged.
 *<p>
 *
 *  <table border="1" cellpadding="5"
 *	 summary="Describes logging performed by sharedGroup at different
 *	 logging levels">
 *
 *  <caption><b><code>
 *	   org.apache.river.start.sharedGroup</code></b></caption>
 *
 *  <tr> <th scope="col"> Level <th scope="col"> Description
 *
 *  <tr> <td> {@link java.util.logging.Level#FINE FINE} <td> 
 *    for low level
 *    service operation tracing
 *  <tr> <td> {@link java.util.logging.Level#FINER FINER} <td> 
 *    for lower level
 *    service operation tracing
 *  <tr> <td> {@link java.util.logging.Level#FINEST FINEST} <td> 
 *    for lowest level
 *    service operation tracing
 *
 *  </table> <p>
 * 
 * @author Sun Microsystems, Inc.
 *
 */
public class SharedGroupImpl implements Remote, 
       SharedGroupBackEnd, ServerProxyTrust, ProxyAccessor, Startable {
    
    /** Component name for configuration entries */
    static final String START_PACKAGE = "org.apache.river.start";

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
    SharedGroupImpl(ActivationID activationID, MarshalledObject data)
	throws Exception
    {
	this(getInit(activationID, data));
    }
    
    private static SharedGroupImplInit getInit(ActivationID activationID, 
                                                            MarshalledObject data)
            throws IOException, ClassNotFoundException, ConfigurationException, Exception
    {
        LoginContext loginContext = null;
        try {
            logger.entering(SharedGroupImpl.class.getName(), "SharedGroupImpl", 
                new Object[] { activationID, data}); 
            String[] configArgs = (String[]) new MarshalledInstance(data).get(false);	
            Configuration config = ConfigurationProvider.getInstance(configArgs);
            loginContext = (LoginContext) config.getEntry(
                START_PACKAGE, "loginContext", LoginContext.class, null);
            SharedGroupImplInit init = null;
                if (loginContext != null) {
                    init = doInitWithLogin(config, activationID, loginContext);
                } else {
                    init = doInit(config, activationID, null);
                }
            return init;
        } catch (Exception e) {
            // If we get here an instance of SharedGroupImpl hasn't been
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
            logger.exiting(SharedGroupImpl.class.getName(), "SharedGroupImpl");
        }
    }
    
    private SharedGroupImpl(SharedGroupImplInit init){
            activationSystem = init.activationSystem;
            this.activationID = init.activationID;
            exporter = init.exporter;
            context = init.context;
            loginContext = init.loginContext;
    }       

    private static SharedGroupImplInit doInitWithLogin(final Configuration config, 
                                                    final ActivationID id, 
                                                    final LoginContext loginContext) 
                                                throws Exception
    {
        loginContext.login();
        try {
            return Subject.doAsPrivileged(
                loginContext.getSubject(),
                new PrivilegedExceptionAction<SharedGroupImplInit>() {
                    public SharedGroupImplInit run() throws Exception {
                        doInit(config, id, loginContext);
                        return null;
                    }
                },
                null);
        } catch (PrivilegedActionException e) {
            throw e.getException();
        }
    }
    
    private static SharedGroupImplInit doInit(Configuration config, 
                                                    ActivationID id, 
                                                    LoginContext loginContext) 
                                                            throws Exception 
    {
        return new SharedGroupImplInit(config, id, loginContext);
    }
    
    public final synchronized void start() throws ExportException {
        try {
            // Export service
            
            ourStub = AccessController.doPrivileged(new PrivilegedExceptionAction<Remote>(){

                @Override
                public Remote run() throws ExportException {
                    return exporter.export(SharedGroupImpl.this);	
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
    @Override
    public void destroyVM() throws RemoteException, ActivationException {
	logger.entering(SharedGroupImpl.class.getName(), "destroyVM"); 
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
        (new SharedGroupImpl.DestroyThread()).start();
	logger.exiting(SharedGroupImpl.class.getName(), "destroyVM"); 
    }

    /**
     * Private utility method which attempts to roll back from 
     * from a failed initialization attempt. 
     */
    private void cleanup() {
	logger.entering(SharedGroupImpl.class.getName(), "cleanup"); 
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
	logger.exiting(SharedGroupImpl.class.getName(), "cleanup"); 
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
            logger.entering(SharedGroupImpl.DestroyThread.class.getName(), 
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
        logger.entering(SharedGroupImpl.class.getName(), 
                "getProxy");
        logger.exiting(SharedGroupImpl.class.getName(), 
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
