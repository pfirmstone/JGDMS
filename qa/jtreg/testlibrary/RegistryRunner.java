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
/*  */

import java.rmi.*;
import java.rmi.registry.*;
import java.rmi.server.*;

/**
 * Class to run a registry whos VM can be told to exit remotely; using
 * the rmiregistry in this fashion makes tests more robust under
 * windows where Process.destroy() seems not to be 100% reliable.  
 */
public class RegistryRunner extends UnicastRemoteObject 
    implements RemoteExiter 
{
    private static Registry registry = null;
    private static RemoteExiter exiter = null;

    public RegistryRunner() throws RemoteException {
    }

    /**
     * Ask the registry to exit instead of forcing it do so; this
     * works better on windows...  
     */
    public void exit() throws RemoteException {
	// REMIND: create a thread to do this to avoid
	// a remote exception?
	System.err.println("received call to exit");
	System.exit(0);
    }

    /** 
     * Request that the registry process exit and handle
     * related exceptions.
     */
    public static void requestExit() {
	try {
	    RemoteExiter exiter = 
		(RemoteExiter) 
		Naming.lookup("rmi://localhost:" + 
			      TestLibrary.REGISTRY_PORT +
			      "/RemoteExiter");
	    try {
		exiter.exit();
	    } catch (RemoteException re) {
	    }
	    exiter = null;
	} catch (java.net.MalformedURLException mfue) {
	    // will not happen
	} catch (NotBoundException nbe) {
	    TestLibrary.bomb("exiter not bound?", nbe);
	} catch (RemoteException re) {
	    TestLibrary.bomb("remote exception trying to exit", 
			     re);
	}
    }

    public static void main(String[] args) {
	try {
	    if (args.length == 0) {
		System.err.println("Usage: <port>");
		System.exit(0);
	    }
	    int port = TestLibrary.REGISTRY_PORT;
	    try {
		port = Integer.parseInt(args[0]);
	    } catch (NumberFormatException nfe) { 
	    }

	    // create a registry
	    registry = LocateRegistry.createRegistry(port);

	    // create a remote object to tell this VM to exit
	    exiter = new RegistryRunner();
	    Naming.rebind("rmi://localhost:" + port + 
			  "/RemoteExiter", exiter);

	} catch (Exception e) {
	    System.err.println(e.getMessage());
	    e.printStackTrace();
	    System.exit(-1);
	}
    }
}
