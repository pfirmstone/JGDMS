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

package com.sun.jini.example.hello;

import java.rmi.RemoteException;
import java.security.PrivilegedExceptionAction;
import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.config.ConfigurationProvider;
import net.jini.config.NoSuchEntryException;
import net.jini.core.lookup.ServiceID;
import net.jini.discovery.DiscoveryManagement;
import net.jini.discovery.LookupDiscovery;
import net.jini.export.Exporter;
import net.jini.export.ProxyAccessor;
import net.jini.id.Uuid;
import net.jini.id.UuidFactory;
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.tcp.TcpServerEndpoint;
import net.jini.lookup.JoinManager;
import net.jini.security.TrustVerifier;
import net.jini.security.proxytrust.ServerProxyTrust;

/**
 * Defines an application server that provides an implementation of the Hello
 * interface.
 *
 * The application uses the following arguments:
 *
 * [all] - All arguments are used as options when getting the configuration
 *
 * The application uses the following configuration entries, with component
 * com.sun.jini.example.hello.Server:
 *
 * discoveryManager
 *   type: DiscoveryManagement
 *   default: new LookupDiscovery(new String[] { "" }, config)
 *   Object used to discover lookup services to join.
 *
 * exporter
 *   type: Exporter
 *   default: none
 *   The object to use for exporting the server
 *
 * loginContext
 *   type: LoginContext
 *   default: null
 *   If non-null, specifies the JAAS login context to use for performing a JAAS
 *   login and supplying the Subject to use when running the server. If null,
 *   no JAAS login is performed.
 *
 * 
 */
public class Server implements Hello, ServerProxyTrust, ProxyAccessor {

    /**
     * If the impl gets GC'ed, then the server will be unexported.
     * Store the instance here to prevent this.
     */
    private static Server serverImpl;

    /* The configuration to use for configuring the server */
    protected final Configuration config;

    /** The server proxy, for use by getProxyVerifier */
    protected Hello serverProxy;

    /**
     * Starts and registers a server that implements the Hello interface.
     *
     * @param args options to use when getting the Configuration
     * @throws ConfigurationException if a problem occurs with the
     *	       configuration
     * @throws RemoteException if a remote communication problem occurs
     */
    public static void main(String[] args) throws Exception {
	serverImpl = new Server(args);
	serverImpl.init();
	System.out.println("Hello server is ready");
    }

    /**
     * Creates the server.
     *
     * @param configOptions options to use when getting the Configuration
     * @throws ConfigurationException if a problem occurs creating the
     *	       configuration
     */
    protected Server(String[] configOptions) throws ConfigurationException {
	config = ConfigurationProvider.getInstance
                       ( configOptions, (this.getClass()).getClassLoader() );
    }

    /**
     * Initializes the server, including exporting it and storing its proxy in
     * the registry.
     *
     * @throws Exception if a problem occurs
     */
    protected void init() throws Exception {
	LoginContext loginContext = (LoginContext) config.getEntry(
	    "com.sun.jini.example.hello.Server", "loginContext",
	    LoginContext.class, null);
	if (loginContext == null) {
	    initAsSubject();
	} else {
	    loginContext.login();
	    Subject.doAsPrivileged(
		loginContext.getSubject(),
		new PrivilegedExceptionAction() {
		    public Object run() throws Exception {
			initAsSubject();
			return null;
		    }
		},
		null);
	}
    }

    /**
     * Initializes the server, assuming that the appropriate subject is in
     * effect.
     */
    protected void initAsSubject() throws Exception {
	/* Export the server */
	Exporter exporter = getExporter();
	serverProxy = (Hello) exporter.export(this);

	/* Create the smart proxy */
	Proxy smartProxy = Proxy.create(serverProxy);

	/* Get the discovery manager, for discovering lookup services */
	DiscoveryManagement discoveryManager;
	try {
	    discoveryManager = (DiscoveryManagement) config.getEntry(
		"com.sun.jini.example.hello.Server", "discoveryManager",
		DiscoveryManagement.class);
	} catch (NoSuchEntryException e) {
            /* Use the public group */
	    discoveryManager = new LookupDiscovery(
		new String[] { "" }, config);
	}

	/* Get the join manager, for joining lookup services */
	JoinManager joinManager =
	    new JoinManager(smartProxy, null /* attrSets */, getServiceID(),
			    discoveryManager, null /* leaseMgr */, config);
    }

    /**
     * Returns the exporter for exporting the server.
     *
     * @throws ConfigurationException if a problem occurs getting the exporter
     *	       from the configuration
     * @throws RemoteException if a remote communication problem occurs
     */
    protected Exporter getExporter()
	throws ConfigurationException, RemoteException
    {
	return (Exporter) config.getEntry(
	    "com.sun.jini.example.hello.Server", "exporter", Exporter.class,
	    new BasicJeriExporter(TcpServerEndpoint.getInstance(0),
				  new BasicILFactory()));
    }

    /** Returns the service ID for this server. */
    protected ServiceID getServiceID() {
	return createServiceID();
    }

    /** Creates a new service ID. */
    protected static ServiceID createServiceID() {
	Uuid uuid = UuidFactory.generate();
	return new ServiceID(
	    uuid.getMostSignificantBits(), uuid.getLeastSignificantBits());
    }

    /** Implement the Hello interface. */
    public String sayHello() {
	return "Hello, world!";
    }

    /**
     * Implement the ServerProxyTrust interface to provide a verifier for
     * secure smart proxies.
     */
    public TrustVerifier getProxyVerifier() {
	return new Proxy.Verifier(serverProxy);
    }

    /**
     * Returns a proxy object for this remote object.
     *
     * @return our proxy
     */
    public Object getProxy() {
        return serverProxy;
    }

}
