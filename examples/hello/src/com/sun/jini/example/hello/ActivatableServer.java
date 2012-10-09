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

import java.io.Serializable;
import java.rmi.MarshalledObject;
import java.rmi.RemoteException;
import java.rmi.activation.ActivationDesc;
import java.rmi.activation.ActivationGroup;
import java.rmi.activation.ActivationGroupDesc.CommandEnvironment;
import java.rmi.activation.ActivationGroupDesc;
import java.rmi.activation.ActivationGroupID;
import java.rmi.activation.ActivationID;
import java.rmi.activation.ActivationSystem;
import java.security.PrivilegedExceptionAction;
import java.util.Properties;
import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.config.ConfigurationProvider;
import net.jini.core.lookup.ServiceID;
import net.jini.export.Exporter;
import net.jini.security.BasicProxyPreparer;
import net.jini.security.ProxyPreparer;

/**
 * Defines an activatable application server that provides an implementation of
 * the Hello interface.
 *
 * The application uses the following arguments:
 *
 * [all] - All arguments are used as options when getting the configuration
 *
 * The application uses the following configuration entries, with component
 * com.sun.jini.example.hello.Server:
 *
 * activationIdPreparer
 *   type: ProxyPreparer
 *   default: new BasicProxyPreparer()
 *   Proxy preparer for the service proxy's activation ID
 *
 * activationSystemPreparer
 *   type: ProxyPreparer
 *   default: new BasicProxyPreparer()
 *   Proxy preparer for the activation system proxy
 *
 * configOptions
 *   type: String[]
 *   default: none
 *   The options for the configuration used to configure the server
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
 *   login and supplying the Subject to use when starting the service. If null,
 *   no JAAS login is performed.
 *
 * javaOptions
 *   type: String[]
 *   default: new String[0]
 *   Command line options for the activation group executable
 *
 * javaProgram
 *   type: String
 *   default: null
 *   The executable for the activation group, or the default executable if null
 *
 * javaProperties
 *   type: String[]
 *   default: new String[0]
 *   System properties for the activation group executable, specified as pairs
 *   of keys and values
 *
 * 
 */
public class ActivatableServer extends Server {

    /** The activation ID for this server */
    private ActivationID aid;

    /** The service ID for this server */
    private final ServiceID serviceID;

    /**
     * Starts and registers an activatable server that implements the Hello
     * interface.
     */
    public static void main(String[] args) throws Exception {
	final Configuration config = ConfigurationProvider.getInstance(args);
	LoginContext loginContext = (LoginContext) config.getEntry(
	    "com.sun.jini.example.hello.Server", "loginContext",
	    LoginContext.class, null);
	if (loginContext == null) {
	    mainAsSubject(config);
	} else {
	    loginContext.login();
	    Subject.doAsPrivileged(
		loginContext.getSubject(),
		new PrivilegedExceptionAction() {
		    public Object run() throws Exception {
			mainAsSubject(config);
			return null;
		    }
		},
		null);
	}
    }

    /** Starts the server with the subject already set. */
    static void mainAsSubject(Configuration config) throws Exception {
	/* Get Java program */
	String program = (String) config.getEntry(
	    "com.sun.jini.example.hello.Server", "javaProgram",
	    String.class, null);

	/* Get options for Java program */
	String[] options = (String[]) config.getEntry(
	    "com.sun.jini.example.hello.Server", "javaOptions", String[].class,
	    new String[0]);

	/* Create command environment */
	CommandEnvironment cmd = new CommandEnvironment(program, options);

	/* Get system properties for Java program */
	String[] propValues = (String[]) config.getEntry(
	    "com.sun.jini.example.hello.Server", "javaProperties",
	    String[].class, new String[0]);
	Properties props = new Properties();
	for (int i = 0; i < propValues.length; i += 2) {
	    props.setProperty(propValues[i], propValues[i + 1]);
	}

	/* Create group description */
	ActivationGroupDesc groupDesc = new ActivationGroupDesc(props, cmd);

	/* Create the activation data -- configuration source and service ID */
	MarshalledObject data = new MarshalledObject(
	    new ActivationData(
		(String[]) config.getEntry(
		    "com.sun.jini.example.hello.Server", "configOptions",
		    String[].class),
		createServiceID()));

	/* Get the activation system */
	ProxyPreparer actSysPreparer = (ProxyPreparer) config.getEntry(
	    "com.sun.jini.example.hello.Server", "activationSystemPreparer",
	    ProxyPreparer.class, new BasicProxyPreparer());
	ActivationSystem actSys = 
	    (ActivationSystem) actSysPreparer.prepareProxy(
		ActivationGroup.getSystem());

	/* Create the activation group */
	ActivationGroupID gid = actSys.registerGroup(groupDesc);

	/* Create the activation descriptor */
	ActivationDesc actDesc =
	    new ActivationDesc(
		gid, ActivatableServer.class.getName(),
		null /* location */, data, true /* restart */);

	/* Register the activation descriptor */
	ProxyPreparer actIdPreparer = (ProxyPreparer) config.getEntry(
	    "com.sun.jini.example.hello.Server", "activationIdPreparer",
	    ProxyPreparer.class, new BasicProxyPreparer());
	ActivationID aid = (ActivationID) actIdPreparer.prepareProxy(
	    actSys.registerObject(actDesc));

	/* Activate the server */
	aid.activate(true);

	System.out.println("Activated server");
    }

    /**
     * Creates the server
     *
     * @param aid the activation ID for the server
     * @param data the data for the activatable server, which should be the
     *        options to use when getting the Configuration
     * @throws Exception if a problem occurs
     */
    public ActivatableServer(ActivationID aid, MarshalledObject data) 
        throws Exception
    {
        super(((ActivationData) data.get()).configOptions);
        this.aid = aid;
	serviceID = ((ActivationData) data.get()).serviceID;
        init();
    }

    /**
     * Stores the configuration options and service ID for the activatable
     * server.
     */
    static class ActivationData implements Serializable {
        private static final long serialVersionUID = 2L;
	final String[] configOptions;
	final ServiceID serviceID;

	ActivationData(String[] configOptions, ServiceID serviceID) {
	    this.configOptions = configOptions;
	    this.serviceID = serviceID;
	}
    }

    /**
     * Returns the exporter for exporting the server. Prepares the activation
     * ID and makes it available to the exporter through the getActivationID
     * method.
     *
     * @throws ConfigurationException if a problem occurs getting the exporter
     *	       from the configuration
     * @throws RemoteException if a remote communication problem occurs
     */
    protected Exporter getExporter()
	throws ConfigurationException, RemoteException
    {
	/* Prepare the activation ID */
	ProxyPreparer actIdPreparer = (ProxyPreparer) config.getEntry(
	    "com.sun.jini.example.hello.Server", "activationIdPreparer",
	    ProxyPreparer.class, new BasicProxyPreparer());
	aid = (ActivationID) actIdPreparer.prepareProxy(aid);

	/* Provide the activation ID to the exporter */
	return (Exporter) config.getEntry(
	    "com.sun.jini.example.hello.Server", "exporter", Exporter.class,
	    Configuration.NO_DEFAULT, aid);
    }

    /** Returns the service ID for this server. */
    protected ServiceID getServiceID() {
	return serviceID;
    }
}
