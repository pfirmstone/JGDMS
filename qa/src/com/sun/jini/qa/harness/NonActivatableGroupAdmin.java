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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.rmi.MarshalledObject;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * An <code>Admin</code> which manages a <code>NonActivatableGroup</code>.
 * The group is started in a separate VM and should be packaged in its own
 * minimized JAR file to avoid class loader conflicts.
 */
public class NonActivatableGroupAdmin extends AbstractServiceAdmin 
                                      implements Admin 
{
    /** the logger */
    private final static Logger logger = 
	Logger.getLogger("com.sun.jini.qa.harness");

    /** the group proxy */
    private NonActivatableGroup proxy;

    /** the system process */
    private Process process;

    /** the stdout pipe, which mustn't be GC'd */
    private Pipe outPipe;

    /** service options provided by the 5-arg constructor */
    private final String[] options;

    /** service properties provided by the 5-arg constructor */
    private final String[] properties;

    /** merge of group options and service options */
    private String[] combinedOptions;

    /** merge of group properties and service properties */
    private String[] combinedProperties;

    /**
     * Construct an instance of <code>NonActivatableGroupAdmin</code>.
     * This constructor is called to create a group admin for a group
     * which will be private to a single service. The given service
     * options and properties are merged with the options and
     * properties defined for the group.
     *
     * @param config         the configuration object for this test run
     * @param serviceName    the name of the group managed by this admin
     * @param index	     the instance number for this service.
     * @param options        the service options 
     * @param properties     the service properties
     */
    public NonActivatableGroupAdmin(QAConfig config, 
				    String serviceName, 
				    int index,
				    String[] options,
				    String[] properties)
    {
	super(config, serviceName, index);
	this.options = options;
	this.properties = properties;
    }

    /**
     * Construct an instance of <code>NonActivatableGroupAdmin</code>.
     * This constructor is called to create a group which is intended
     * to be shared among multiple nonactivatable services.
     *
     * @param config         the configuration object for this test run
     * @param serviceName    the name of the group managed by this admin
     * @param index	     the instance number for this service.
     */
    public NonActivatableGroupAdmin(QAConfig config, 
				    String serviceName, 
				    int index)
    {
	super(config, serviceName, index);
        options = new String[0];
        properties = options;
    }

    /**
     * Start the group managed by this admin. A command line is constructed
     * based on the service properties associated with this group. A VM is
     * exec'd, and an object is read from the child process
     * <code>System.err</code> stream which is expected to be a
     * <code>MarshalledObject</code> containing the serialized proxy for the
     * <code>NonActivatableGroup</code> instance provided by that VM. No further
     * input is read from the child <code>System.err.</code> It is assumed that
     * the child will write any output originally destined to
     * <code>System.err</code> to its <code>System.out</code> stream.
     *
     * @throws TestException if the child process could not be exec'd
     *                       or if an I/O error occurs reading the
     *                       childs proxy or if the childs proxy class
     *                       could not be found
     * @throws RemoteException never
     */
    public void start() throws RemoteException, TestException {

	// construct the command line and convert to a string array
	ArrayList l = new ArrayList(10);
	String actCommand = null;
	String vm = getServiceJVM();
	if (vm == null) {
	    vm = System.getProperty("java.home");
	}
	l.add(vm + "/bin/java");
	l.add("-Djava.rmi.server.codebase=" + getServiceCodebase());
	l.add("-Djava.security.policy=" + getServicePolicyFile());
	String[] opts = getServiceOptions();
	if (opts != null) {
	    for (int i = 0; i < opts.length; i++) {
		l.add(opts[i]);
	    }
	}
	String[] props = getServiceProperties();
	if (props != null) {
	    for (int i = 0; i < props.length; i += 2) {
		l.add("-D" + props[i] + "=" + props[i+1]);
	    }
	}
	l.add("-cp");
	l.add(getServiceClasspath());
	l.add(getServiceImpl());
	String[] cmdArray = (String[]) l.toArray(new String[l.size()]);

	// stringify the command line for log display
	StringBuffer cmdBuf = new StringBuffer(cmdArray[0]);
	for (int i = 1; i < cmdArray.length; i++) {
	    cmdBuf.append(" ").append(cmdArray[i]);
	}
	logger.log(Level.FINER, 
		   "NonActivatableGroup exec command line: '" + cmdBuf + "'");
	logServiceParameters();

        ObjectInputStream proxyStream = null;
	// exec the process, setup the pipe, and get the proxy
        synchronized (this){
            try {
                process = Runtime.getRuntime().exec(cmdArray);
                outPipe = new Pipe("NonActivatableGroup_system-out", 
                                   process.getInputStream(),
                                   System.out,
                                   null, //filter
                                   new NonActGrpAnnotator("NonActGrp-out: "));
                outPipe.start();
                proxyStream = new ObjectInputStream(process.getErrorStream());
                proxy = (NonActivatableGroup)
                        ((MarshalledObject) proxyStream.readObject()).get();
            } catch (IOException e) {
                // Clean up.
                process.destroy();
                try {
                    outPipe.stop();
                } catch (IOException ex){ }//Ignore
                try {
                    if (proxyStream != null) proxyStream.close();
                } catch (IOException ex){ } // Ignore.
                throw new TestException("NonActivatableGroupAdmin: Failed to exec "
                                      + "the group", e);
            } catch (ClassNotFoundException e) {
                // Clean up.
                process.destroy();
                try {
                    outPipe.stop();
                } catch (IOException ex){ }//Ignore
                try {
                    if (proxyStream != null) proxyStream.close();
                } catch (IOException ex){ } // Ignore.
                throw new TestException("NonActivatableGroupAdmin: Failed to exec "
                                      + "the group", e);
            }
        }
    }

    /**
     * Annotator for annotating output merged into test log
     */
    private static class NonActGrpAnnotator implements Pipe.Annotator {

	private final String annotation;

        NonActGrpAnnotator(String annotation) {
	    this.annotation = annotation;
	}

	public String getAnnotation() {
	    return annotation;
	}
    }

    /**
     * Stop the group. Start a destroy thread which has a two-second delay
     * before calling <code>System.exit</code> to allow the call to return.
     *
     * @throws RemoteException if a communication error occurs when the
     *                         groups <code>stop</code> method is called
     */
    public synchronized void stop() throws RemoteException {
	proxy.stop();
	Timeout.TimeoutHandler handler =
	    new Timeout.ThreadTimeoutHandler(Thread.currentThread());
	Timeout timeout = new Timeout(handler, 10000);  // ten seconds
	timeout.start();
	try {
	    process.waitFor();
	    timeout.cancel();
	} catch (InterruptedException e) {
	    logger.log(Level.INFO, "Nonactivatable group process did not exit");
	}
    }

    /**
     * Return the proxy for the <code>NonActivatableGroup</code>
     * managed by this admin.
     *
     * @return the <code>NonActivatableGroup</code> proxy
     */
    public synchronized Object getProxy() {
	return proxy;
    }

    /**
     * Override the base class method to merge options which may have
     * been supplied through the constructor.
     *
     * @return the merged property array
     */
    public synchronized String[] getServiceOptions() {
	combinedOptions = config.mergeOptions(super.getServiceOptions(),
					      options);
	return combinedOptions.clone();
    }

    /** 
     * Override the base class method to return the merged options.
     * The <code>getServiceOptions</code> method must be called
     * prior to calling this method.
     *
     * @return the merged options array
     */
    public synchronized String[] getOptions() {
	return combinedOptions.clone();
    }

    /**
     * Override the base class method to merge properties which may have
     * been supplied through the constructor.
     *
     * @return the merged property array
     */
    public synchronized String[] getServiceProperties() throws TestException {
	combinedProperties = config.mergeProperties(super.getServiceProperties(),
						properties);
	return combinedProperties.clone();
    }

    /** 
     * Override the base class method to return the merged properties.
     * The <code>getServiceProperties</code> method must be called
     * prior to calling this method.
     *
     * @return the merged property array
     */
    public synchronized String[] getProperties() {
	return combinedProperties.clone();
    }

    /**
     * Attempt to force a thread dump.
     *
     * @return true if the dump is successfully requested
     */
    public synchronized boolean forceThreadDump() {
	logger.log(Level.INFO, "Attempting to force thread dump on "
		   + "NonActivatableGroup " + process);
	boolean ret = true;
	try {
	    Class procClass = process.getClass();
	    Field field = procClass.getDeclaredField("pid");
	    field.setAccessible(true);
	    int pid = field.getInt(process);
	    Process p = Runtime.getRuntime().exec("/usr/bin/kill -QUIT " + pid);
	    p.waitFor();
	} catch (Exception e) {
	    logger.log(Level.INFO, "Unable to force thread dump");
	    ret = false;
	}
	return ret;
    }
}
