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
package com.sun.jini.test.impl.start;

import com.sun.jini.proxy.BasicProxyTrustVerifier;
import com.sun.jini.start.LifeCycle;
import com.sun.jini.start.ServiceProxyAccessor;
import org.apache.river.api.util.Startable;

import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.config.ConfigurationProvider;
import net.jini.export.Exporter;
import net.jini.export.ProxyAccessor;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.tcp.TcpServerEndpoint;
import net.jini.security.TrustVerifier;
import net.jini.security.proxytrust.ServerProxyTrust;

import net.jini.core.event.RemoteEvent;
import net.jini.core.event.UnknownEventException;
import net.jini.core.event.RemoteEventListener;

import java.rmi.activation.ActivationID;
import java.rmi.MarshalledObject;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Collections;
import java.util.Map;
import java.util.HashMap;

import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;



public class BadServiceProxyAccessorImpl 
    implements BadServiceProxyAccessor, ProxyAccessor, Startable
{
    private Object serverStub;
    private AccessControlContext context;

    private static final String BAD_SPA = 
        "com.sun.jini.test.impl.mercury.badSPA";
    private Exporter exporter;
    private boolean started;

    // ProxyAccessor - for activation code
    public synchronized Object getProxy() { return serverStub; }

    // ServiceProxyAccessor - for ServiceStarter code
    public Object getServiceProxy() { return null; }

    // ServerProxyTrust - for proxy preparation
    public synchronized TrustVerifier getProxyVerifier() {
	return new BasicProxyTrustVerifier(serverStub);
    }
    // Activatable constructor
    public BadServiceProxyAccessorImpl (
        ActivationID activationID, MarshalledObject data)
        throws Exception
    {
        this((String[]) data.get(), activationID, null);
    }

    // Transient constructor
    public BadServiceProxyAccessorImpl (
	String[] configArgs, LifeCycle lc) 
	throws Exception 
    {
        this(configArgs, null, lc);
    }

    public BadServiceProxyAccessorImpl(
	String[] configArgs, ActivationID activationID, 
	LifeCycle lifeCycle)
	throws Exception
    {
        final Configuration config =
            ConfigurationProvider.getInstance(configArgs);
        LoginContext loginContext = (LoginContext) config.getEntry(
            BAD_SPA, "loginContext", LoginContext.class, null);
        if (loginContext != null) {
            doInitWithLogin(config, loginContext);
        } else {
            doInit(config);
        }
    }

    /**
     * Method that attempts to login before deledating the
     * rest of the initialization process to <code>doInit</code>
     */
    private void doInitWithLogin(final Configuration config,
        LoginContext loginContext) throws Exception
    {
        loginContext.login();
        try {
            Subject.doAsPrivileged(
                loginContext.getSubject(),
                new PrivilegedExceptionAction() {
                    public Object run() throws Exception {
                        doInit(config);
                        return null;
                    }
                },
                null);
        } catch (PrivilegedActionException e) {
           try {
                loginContext.logout();
            } catch (LoginException le) {
                System.out.println("Trouble logging out" + le);
            }
            throw e.getException();
        }
    }

    /** Initialization common to both activatable and transient instances. */
    private void doInit(Configuration config) throws Exception {
        exporter = (Exporter) getNonNullEntry(
            config, "exporter", Exporter.class,
            new BasicJeriExporter(TcpServerEndpoint.getInstance(0), 
				  new BasicILFactory(), 
				  false, 
				  true));
        context = AccessController.getContext();
    }

    protected Object getNonNullEntry(Configuration config,
                                     String name,
                                     Class type,
                                     Object defaultValue)
        throws ConfigurationException
    {
        Object result = config.getEntry(BAD_SPA, name, type, defaultValue);
        if (result == null) {
            throw new ConfigurationException(
                "Configuration entry for component " + BAD_SPA +
                ", name " + name + " should not be null");
        }
        return result;
    }

    @Override
    public final synchronized void start() throws Exception {
        if (started) return;
        started = true;
        AccessController.doPrivileged(new PrivilegedExceptionAction<Object>(){

            @Override
            public Object run() throws Exception {
                // Export server instance and get its reference
                serverStub =  exporter.export(BadServiceProxyAccessorImpl.this);
                return null;
            }
            
        }, context);
    }
}


