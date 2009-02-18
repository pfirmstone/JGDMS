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

package com.sun.jini.qa.harness;

import java.lang.reflect.Constructor;
import java.rmi.RemoteException;
import java.rmi.activation.ActivationGroup;
import java.rmi.activation.ActivationGroupID;
import java.rmi.activation.ActivationException;
import java.rmi.activation.ActivationSystem;

import com.sun.jini.tool.ClassServer;

/**
 * A specialization of <code>AbstractServiceAdmin</code> which manages
 * an HTTP class server. The class server runs in the same VM as
 * its admin.
 * <p>
 * This admin implementation supports the following well-known service 
 * properties:
 * <table>
 * <tr><td> <code>type</code>
 * <td>                 the mandatory service type, "classServer"
 * <tr><td> <code>impl</code>       
 * <td>                 the mandatory name of the class to instantiate.
 *                      Assumptions built into this admin require that the
 *                      value of the parameter must be 
 *                      "com.sun.jini.tool.ClassServer"
 * <tr><td> <code>port</code>
 * <td>                 the TCP port the server is to use, default is 8080
 * <tr><td> <code>dir</code>
 * <td>                 the document directory the server is to use. The default
 *                      is the value of the configuration property
 *                      <code>com.sun.jini.jsk.home</code> appended with  "/lib"
 * <tr><td> <code>serverjvmopts</code>
 * <td>                 options which may be passed to the server. 
 *                      Supported values include <code>-trees</code> 
 *                      and <code>-verbose</code>
 * </table>
 * The proxy returned by <code>getProxy</code> is a local reference to
 * an instance of the class server. This admin cannot start a class server
 * in a separate VM.
 */
public class ClassServerAdmin extends AbstractServiceAdmin implements Admin {

    /** the class server */
    private ClassServer classServer;

    /**
     * Construct a <code>ClassServerAdmin</code>.
     *
     * @param config         the configuration object for this test run
     * @param serviceName    the prefix used to build the property
     *                       names needed to acquire service parameters
     * @param index	     the instance number for this service.
     */
    public ClassServerAdmin(QAConfig config, 
			    String serviceName, 
			    int index)
    {
        super(config, serviceName, index);
    }

    /**
     * Starts the class server.
     *
     * @throws TestException    if the <code>classServer</code> property
     *                          is not defined, if the path to the JSK
     *                          cannot be resolved, if the class server
     *                          cannot be loaded, if the class server
     *                          does not have the correct constructor, or
     *                          if instantiation fails.
     *
     * @throws RemoteException  never.
     */
    public void start() throws RemoteException, TestException {
	String serverClassName = getServiceImpl();
	Class serverClass = null;
	try {
	    serverClass = Class.forName(serverClassName);
	} catch (ClassNotFoundException e) {
	    throw new TestException("ClassServerAdmin: unable to load class "
				  + serverClassName);
	}
	int port = getServicePort();
	if (port == 0) { 
	    port = 8080;
	}
	String dir = getServiceDir();
	String[] opts = getServiceOptions();
	boolean trees = false;
	boolean verbose = false;
	if (opts != null) {
	    for (int i = 0; i < opts.length; i++) {
		if (opts[i].equals("-trees")) {
		    trees = true;
		} else if (opts[i].equals("-verbose")) {
		    verbose = true;
		}
	    }
	}
	logServiceParameters(); // log debug output
	Constructor cons = null;
	try {
	    cons = serverClass.getConstructor(new Class[]{int.class,
							  String.class,
							  boolean.class,
							  boolean.class});
	} catch (NoSuchMethodException e) {
	    throw new TestException("ClassServerAdmin: no constructor for "
				   + serverClassName);
	}
	try {
	    classServer = (ClassServer) 
		          cons.newInstance(new Object[]{new Integer(port),
							dir,
							new Boolean(trees),
							new Boolean(verbose)});
	} catch (Exception e) {
	    throw new TestException("Exception instantiating class server "
				    + serverClassName, e);
	}
	classServer.start();
    }

    /**
     * Stop the class server. This method stops the class server
     * by calling its <code>terminate</code> method.
     *
     * @throws RemoteException never.
     */
    public void stop() throws RemoteException {
	classServer.terminate();
    }

    /**
     * Return the <code>ClassServer</code> reference for the class
     * server managed by this admin.
     *
     * @return a local reference to the <code>ClassServer</code>
     */
    public Object getProxy() {
	return classServer;
    }
}
