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

import com.sun.jini.start.SharedActivatableServiceDescriptor.Created;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationProvider;
import net.jini.config.ConfigurationException;

import java.io.File;
import java.rmi.activation.ActivationSystem;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import javax.security.auth.Subject;

import com.sun.jini.system.FileSystem;

/**
 * This class contains the command-line interface for
 * destroying an instance of a shared activation group.
 *
 * The following items are discussed below:
 * <ul>
 * <li><a href="#configEntries">Configuring DestroySharedGroup</a>
 * <li><a href="#logging">Logging</a>
 * </ul>
 *
 * <a name="configEntries">
 * <h3>Configuring DestroySharedGroup</h3>
 * </a>
 *
 * This implementation of <code>DestroySharedGroup</code> supports the
 * following configuration entries, with component
 * <code>com.sun.jini.start</code>:
 *
 *   <table summary="Describes the activationSystemPreparer configuration
 *          entry"
 *          border="0" cellpadding="2">
 *     <tr valign="top">
 *       <th scope="col" summary="layout"> <font size="+1">&#X2022;</font>
 *       <th scope="col" align="left" colspan="2"> <font size="+1"><code>
 *       activationSystemPreparer</code></font>
 *     <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *       Type: <td> {@link net.jini.security.ProxyPreparer}
 *     <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *       Default: <td> <code>
 *         new {@link net.jini.security.BasicProxyPreparer}()</code>
 *     <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *       Description: <td> The proxy preparer for the proxy for the
 *       activation system. The value should not be <code>null</code>. This
 *       entry is obtained at service start and restart. This entry is only
 *       used by the activatable implementation. <p>
 * 
 *       The service calls the {@link
 *       java.rmi.activation.ActivationSystem#unregisterObject
 *       unregisterObject} method on the {@link
 *       java.rmi.activation.ActivationSystem} when there is a problem
 *       creating a service.
 *   </table>
 *
 *   <table summary="Describes the loginContext configuration entry"
 *     border="0" cellpadding="2">
 *   <tr valign="top">
 *     <th scope="col" summary="layout"> <font size="+1">&#X2022;</font>
 *     <th scope="col" align="left" colspan="2"> <font size="+1"><code>
 *   loginContext</code></font>
 *   <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *     Type: <td> {@link javax.security.auth.login.LoginContext}
 *   <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *     Default: <td> <code>null</code>
 *   <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *     Description: <td> If not <code>null</code>, specifies the JAAS
 *     login context to use for performing a JAAS login and supplying the
 *     {@link javax.security.auth.Subject} to use when running the
 *     services starter. If <code>null</code>, no JAAS login is performed. 
 *   </table>
 *
 * <table summary="Describes the serviceDestructors configuration entry"
 *	  border="0" cellpadding="2">
 *   <tr valign="top">
 *     <th scope="col" summary="layout"> <font size="+1">&#X2022;</font>
 *     <th scope="col" align="left" colspan="2"> <font size="+1"><code>
 *	 serviceDestructors</code></font>
 *   <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *     Type: <td> {@link ServiceDescriptor}[]
 *   <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *     Default: no default
 *   <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *     Description: <td> Array of service descriptors to start.
 * </table>
 *
 *
 *<a name="logging">
 *<h3>Loggers and Logging Levels</h3>
 *</a>
 *
 *The DestroySharedGroup service implementation uses the {@link
 *java.util.logging.Logger}, named 
 * <code>com.sun.jini.start.service.starter</code>. 
 *The following table describes the
 *type of information logged as well as the levels of information logged.
 *<p>
 *
 *  <table border="1" cellpadding="5"
 *	 summary="Describes logging performed by service.starter at different
 *	 logging levels">
 *
 *  <caption halign="center" valign="top"><b><code>
 *	   com.sun.jini.start.service.starter</code></b></caption>
 *
 *  <tr> <th scope="col"> Level <th scope="col"> Description
 *
 *  <tr> <td> {@link java.util.logging.Level#SEVERE SEVERE} <td> 
 *    for problems that prevent service destruction from proceeding
 *  <tr> <td> {@link java.util.logging.Level#WARNING WARNING} <td> 
 *    for problems with service destruction that don't prevent further
 *    processing
 *  <tr> <td> {@link java.util.logging.Level#FINER FINER} <td> 
 *    for high level
 *    service destruction operation tracing
 *  <tr> <td> {@link java.util.logging.Level#FINEST FINEST} <td> 
 *    for low level
 *    service destruction operation tracing
 *
 *  </table> <p>
 * 
 * @author Sun Microsystems, Inc.
 *
 * @see com.sun.jini.start.ServiceStarter
 *
 * @since 1.2 
 */
public class DestroySharedGroup {

    /** Configure logger. */
    private static final Logger logger = ServiceStarter.logger;

    // Private constructor to prevent instantiation
    private DestroySharedGroup() { }

    /**
     * The main method for the <code>DestroySharedGroup</code> application.
     * The <code>args</code> parameter is passed directly to 
     * <code>ConfigurationProvider.getInstance()</code> in order to 
     * obtain a <code>Configuration</code> object. This configuration 
     * object is then queried for a 
     * <code>com.sun.jini.start.serviceDestructors</code> entry, which
     * is assumed to be a <code>SharedActivatableServiceDescriptor[]</code>
     * configured to run {@link com.sun.jini.start.SharedGroup} implementations.
     * The {@link com.sun.jini.start.SharedGroup#destroyVM() destroyVM()} 
     * method is then called on each of the array elements. An attempt is
     * made to also delete shared group <code>log</code> directory associated
     * with each array element.
     * @param args <code>String[]</code> passed to 
     *             <code>ConfigurationProvider.getInstance()</code> in order
     *             to obtain a <code>Configuration</code> object.
     *
     * @see ServiceDescriptor
     * @see SharedActivatableServiceDescriptor
     * @see net.jini.config.Configuration
     * @see net.jini.config.ConfigurationProvider
     *
     */
    public static void main(String[] args) { 
	ServiceStarter.ensureSecurityManager();
        logger.entering(DestroySharedGroup.class.getName(), "main", 
	    ((Object[])args));
	try {
            Configuration config = ConfigurationProvider.getInstance(args);
            ServiceDescriptor[] srvArray =
                (ServiceDescriptor[])config.getEntry(
                    ServiceStarter.START_PACKAGE, "serviceDestructors",
                    ServiceDescriptor[].class, null);
            if (srvArray == null || srvArray.length == 0) {
                logger.log(Level.WARNING, "service.config.empty");
                return;
            }
            LoginContext loginContext =  (LoginContext)
                config.getEntry(ServiceStarter.START_PACKAGE, "loginContext",
                    LoginContext.class, null);
            if (loginContext != null)
                destroyWithLogin(srvArray, config, loginContext);
            else
                destroy(srvArray, config);
	} catch (ConfigurationException ce) {
	    logger.log(Level.SEVERE, "destroy.config.exception", ce);
	} catch (Exception e) {
	    logger.log(Level.SEVERE, "destroy.unexpected.exception", e);
	}
        logger.exiting(DestroySharedGroup.class.getName(), "main");
    }
    
    /**
     * Method that attempts to destroy any available <code>SharedGroup</code>
     * objects in the provided <code>ServiceDescriptor[]</code>. 
     */
    private static void destroy(ServiceDescriptor[] srvArray, 
        Configuration config) throws Exception
    {
        logger.entering(DestroySharedGroup.class.getName(), "destroy", 
	    new Object[] {Arrays.asList(srvArray), config} );
	Created created = null;
        SharedActivatableServiceDescriptor desc = null;
	ActivationSystem activationSystem = null;
	    
        for (int i=0; i < srvArray.length; i++) {
	    if (srvArray[i] instanceof SharedActivatableServiceDescriptor) {
                desc = (SharedActivatableServiceDescriptor)srvArray[i];
		activationSystem = 
		    ServiceStarter.getActivationSystem(
		        desc.getActivationSystemHost(), 
			desc.getActivationSystemPort(), 
			config);
		try {
                    created = (Created)desc.create(config);
	            if (created != null &&
		        created.proxy instanceof SharedGroup) {
			// service proxy from create() is already prepared
		        SharedGroup sg = (SharedGroup)created.proxy;
			try {
			    sg.destroyVM();
		            try { 
		                File log = new File(desc.getSharedGroupLog());
                                FileSystem.destroy(log, true); 
		            } catch (Exception e) {
                                logger.log(Level.WARNING, 
				    "destroy.group.deletion", e);
		            }
		        } catch (Exception e ) {
                            logger.log(Level.SEVERE, 
			        "destroy.group.exception", e);
//TODO - Add configurable retry logic or just unregister				
		        }
		    } else {
                        logger.log(Level.WARNING, "destroy.unexpected.proxy",
			   (created==null)?null:created.proxy);
			if (created != null && created.aid != null) {
			    try { 
			        activationSystem.unregisterObject(created.aid);
			    } catch (Exception e) {
                                logger.log(Level.WARNING, 
				    "destroy.unregister.exception", e) ;
			    }
			}
		    }
	        } catch (Exception ee) {
                    logger.log(Level.SEVERE, "destroy.creation.exception", ee);
		    if (created != null && created.aid != null) {
			try { 
			    activationSystem.unregisterObject(created.aid);
			} catch (Exception e) {
                            logger.log(Level.WARNING, 
				"destroy.unregister.exception", ee) ;
			}
		    }
                }
	    } else {
                logger.log(Level.WARNING, "destroy.unexpected.type", srvArray[i]);
	    }
        }
        logger.exiting(DestroySharedGroup.class.getName(), "destroy");
    }
    
    /**
     * Method that attempts to login via the provided 
     * <code>LoginContext</code> and then calls <code>destroy</code>.
     */
    private static void destroyWithLogin(
        final ServiceDescriptor[] descs, final Configuration config,
	final LoginContext loginContext) throws Exception
    {
        logger.entering(DestroySharedGroup.class.getName(),
            "destroyWithLogin", new Object[] {descs, config, loginContext});
	loginContext.login();
	try {
	    Subject.doAsPrivileged(
	        loginContext.getSubject(),
                new PrivilegedExceptionAction() {
                    public Object run() throws Exception
                    {
                        destroy(descs, config);
			return null;
                    }
                 },
                 null);
        } catch (PrivilegedActionException e) {
	    throw e.getException();
	} finally {
            try {
                loginContext.logout();
	    } catch (LoginException le) {
	        logger.log(Level.FINE, "service.logout.exception", le);
	    }
        }
        logger.exiting(DestroySharedGroup.class.getName(),
            "destroyWithLogin");
        return;
    }   
}


