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

import com.sun.jini.start.NonActivatableServiceDescriptor;
import com.sun.jini.start.NonActivatableServiceDescriptor.Created;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.rmi.MarshalledObject;
import java.rmi.RemoteException;
import java.rmi.server.ExportException;
import java.util.ArrayList;
import java.util.logging.Logger;
import java.util.logging.Level;

import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.config.ConfigurationProvider;
import net.jini.export.Exporter;
import net.jini.jrmp.JrmpExporter;

/**
 * A container for nonactivatable services. This class is the
 * main class exec'd in a separate VM. The main method creates
 * an instance of a <code>NonActivatableGroup</code> and returns
 * it's proxy to the parent process by writing the object
 * to <code>System.err</code>. There is no <code>Configuration</code>
 * associated with this service.
 */
class NonActivatableGroupImpl {

    /** the logger */
    private static Logger logger = 
	Logger.getLogger("com.sun.jini.qa.harness");

    /** the <code>System.err</code> stream created for the VM */
    private static PrintStream origErr;

    /** the reference to the group, save to ensure it won't be GC'd */
    private static GroupImpl group;

    /**
     * Set up the VM to act as a NonActivatableGroup and
     * return the group proxy to the parent process. The
     * initial System.err is saved, and then System.err is
     * set System.out. This prevents any error or logging
     * performed from being written to the original System.err.
     * An instance of GroupImpl is created and a reference 
     * saved to ensure it is not GC'd. The proxy is written to
     * the original System.err. 
     *
     * @param args the command line arguments, which are unused
     */
    public static void main(String[] args) {
	origErr = System.err;
	System.setErr(System.out);
	if (System.getSecurityManager() == null) {
	    System.setSecurityManager(new java.rmi.RMISecurityManager());
	}
	group = new GroupImpl();
	try {
	    ObjectOutputStream os = new ObjectOutputStream(origErr);
	    os.writeObject(new MarshalledObject(group.getProxy()));
	    os.flush();
	} catch (IOException e) {
	    throw new RuntimeException("WriteObject failed", e);
	}
    }

    /**
     * The implementation of NonActivatableGroup.
     */
    private static class GroupImpl implements NonActivatableGroup {

	/** the proxy resulting from exporting the group */
	private Object proxy;

	/** the groups exporter */
	private final Exporter exporter;

	/** store service references here to ensure no GC interference */
	private final ArrayList serviceList = new ArrayList();

	/**
	 * Construct a <code>NonActivatableGroup</code>. Instances export themselves
	 * at construction time using a <code>JrmpExporter</code>.
	 */
	public GroupImpl() {
            this (new JrmpExporter());
	    export();
	}
        
        private GroupImpl(Exporter exporter){
           this.exporter = exporter;
        }
        
        private void export(){
            try {
                synchronized (this){
                    proxy = exporter.export(this);
                }
	    } catch (ExportException e) {
		e.printStackTrace();
		try {Thread.sleep(5000);} catch (Exception e2){}
		throw new RuntimeException("Export of group failed", e);
	    }
        }
        
	/**
	 * Return the proxy for the NonActivatableGroup remote object
	 */
	synchronized Object getProxy() {
	     return proxy;
	}

	/**
	 * Stop the group. A thread is started to perform the destroy.
	 *
	 * @throws RemoteException never
	 */
	public void stop() throws RemoteException {
	    new DestroyThread(exporter).start();
	}

	/** 
	 * Start a service. A NonActivatableServiceDescriptor is constructed
	 * using the given codebase, policyFile, classpath, serviceImpl,
	 * and configArgs. The descriptors create method is called passing
	 * a configuration obtained using the given starterConfigName.
	 * The proxy is extracted from the returned Created object and
	 * returned.
	 * 
	 * @param codebase the service codebase
	 * @param policyFile the service policy file
	 * @param classpath the service classpath
	 * @param serviceImpl the service implementation class name
	 * @param configArgs the configuration arguments passed to the service
	 * @param starterConfigName the name of the starter configuration file
	 *
	 * @return the proxy of the started service
	 * @throws RemoteException if the service cannot be started
	 */
	public Object startService(String codebase,
				   String policyFile,
				   String classpath,
				   String serviceImpl,
				   String[] configArgs,
				   String starterConfigName,
				   ServiceDescriptorTransformer transformer) 
	    throws RemoteException
	{
	    
	    NonActivatableServiceDescriptor desc = 
		new NonActivatableServiceDescriptor(codebase,
						    policyFile,
						    classpath,
						    serviceImpl,
						    configArgs);
	    Configuration starterConfig = null;
	    if (starterConfigName != null) {
		try {
		    starterConfig = 
			ConfigurationProvider.getInstance(
			      new String[]{starterConfigName});
		} catch (ConfigurationException e) {
		    throw new RemoteException("Starter configuration problem",
					      e);
		}
	    }
	    if (transformer != null) {
		desc = (NonActivatableServiceDescriptor) 
		       transformer.transform(desc);
	    }
	    try {
		Created created = (Created) desc.create(starterConfig);
                synchronized (this){
                    serviceList.add(created);
                }
		return created.proxy;
	    } catch (Exception e) {
		throw new RemoteException("Create failed", e);
	    }
	}
    }

    /**
     * A thread which unexports and destroys the group after a 2 second delay
     */
    private static class DestroyThread extends Thread {
	
	final Exporter exporter;

        /** Create a non-daemon thread */
        public DestroyThread(Exporter exporter) {
            super("destroy");
	    this.exporter = exporter;
        }

	public void run() {
	    try {
		Thread.sleep(2000);
	    } catch (InterruptedException e) {
	    }
	    exporter.unexport(true);
	    System.exit(0);
	}
    }
}
