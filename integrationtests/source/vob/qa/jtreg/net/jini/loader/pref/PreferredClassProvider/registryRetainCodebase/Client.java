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

/**
 * 
 */

import java.net.URL;

import java.rmi.Naming;
import java.rmi.ConnectIOException;
import java.rmi.RemoteException;
import java.rmi.RMISecurityManager;
import java.rmi.server.RMIClassLoader;

import java.security.CodeSource;
import java.security.ProtectionDomain;

/**
 * Receives the stub and makes sure its classes were preferred.
 */
public class Client {

    public static void main(String[] args) {
	try {

	    System.setSecurityManager(new SecurityManager());

	    /*
	     * If the class for the stub of the remote object
	     * requested can be loaded, the stub codebase annotation
	     * has survived a 3rd party transfer because of preferred
	     * classes.
	     */
	    BasicRemote basic = (BasicRemote)
	    	    Naming.lookup("rmi://localhost:" +
				  TestLibrary.REGISTRY_PORT +
				  "/registryRetainCodebase");

	    System.err.println("");

	    String basicLocation =
		basic.getClass().getProtectionDomain().
		getCodeSource().getLocation().toString();
	    System.err.println(basic.getClass().getName());
	    if (!basicLocation.startsWith("http")) {
		throw new RuntimeException("Preferred class loaded " +
					   "from a file: " + basicLocation);
	    }
	    
	    System.err.println("basic loaded from: " + basicLocation);

	    /*
	     * The security policy of this VM should only allow code
	     * loaded from the server to open a connection to the
	     * server.  If any preferred classes are replaced by
	     * locally available classes, this call should fail due to
	     * lack of privileges.  This exercises privileges that
	     * class should still have if it is using preferred
	     * classes.
	     */
	    basic.simpleMethod();
	    
	} catch (Exception e) {
	    e.printStackTrace();
	}
    }
}
