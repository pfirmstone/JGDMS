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

package org.apache.river.qa.harness;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.rmi.RemoteException;
import java.rmi.activation.ActivationGroup;
import java.rmi.activation.ActivationException;
import java.rmi.activation.ActivationSystem;
import java.rmi.Naming;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.security.AccessController;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.river.action.GetBooleanAction;
import org.apache.river.action.GetPropertyAction;
import au.net.zeus.rmi.tls.TlsRMIClientSocketFactory;

/**
 * A implementation of <code>Admin</code> which manages
 * the activation system. 
 * This admin implementation defines the following well-known tokens:
 * <table>
 * <tr><td> <code>logdir</code>      
 * <td>                 the directory path to use for the activation systems
 *                      log files. If specified, this path must be a valid
 *                      absolute or relative path name, and is used directly
 *                      as obtained from the configuration object. If 
 *                      this parameter is not specified, a temporary directory
 *                      is created. In all cases, this directory and it's
 *                      contents are deleted by the <code>stop</code> method.
 * <tr><td> <code>type</code>
 * <td>                 a mandatory token identifying the type of activation
 *                      system being used. Valid values are one of the strings 
 *                      <code>"rmid"</code> or <code>"phoenix"</code>.
 * <tr><td> <code>classpath</code>
 * <td>                 if <code>type</code> is <code>"phoenix"</code>, then 
 *                      this token must be defined and have the value of the
 *                      classpath for phoenix, which is assumed to be a single
 *                      executable .jar file.
 * <tr><td> <code>policyfile</code>
 * <td>                 if <code>type</code> is <code>"phoenix"</code>, then
 *                      this token must be defined and specify the location
 *                      of the security policy file for the activation system.
 * <tr>td> <code>serviceTemplate</code>
 * <td>                 if <code>type</code> is <code>"phoenix"</code>, then
 *                      this token must be defined and specify the 
 *                      Configuration file relative to the root of the
 *                      configuration tree.
 * </table>
 * Additional supported tokens include <code>serverjvmopts</code> and
 * <code>serverjvmprops</code>
 * The proxy returned by <code>getProxy</code> is the remote reference
 * to the <code>ActivationSystem.</code>
 * <p>
 * The configuration value named by the key 
 * <code>org.apache.river.qa.harness.actdeathdelay</code> specifies a time delay
 * to be injected between the time <code>stop</code> is called and the
 * activation system is actually killed. This is done to give services
 * which may have initiated a shutdown time to complete termination
 * processing. If undefined, a default of 5 seconds is used.
 * <p>
 * The logger named <code>org.apache.river.qa.harness.service</code> is used
 * to log debug information
 *
 * <table border=1 cellpadding=5>
 *
 *<tr> <th> Level <th> Description
 *
 *<tr> <td> FINE <td> the command line used to start the activation system
 *</table>
 */
public class ActivationSystemAdmin 
                           extends AbstractServiceAdmin 
                           implements Admin 
{

    private static Logger logger = 
	Logger.getLogger("org.apache.river.qa.harness");

    /** flag indicated the act system was started by this class */
    private static boolean started = false;

    /** the service proxy */
    private ActivationSystem actSystem;

    /** the activation system process */
    private Process actProcess;

    /** the activation system log directory */
    private String logDirName;

    /** the stdout pipe */
    private Pipe outPipe;

    /** the stderr pipe */
    private Pipe errPipe;

    /**
     * Return an indication of whether the activation has already been
     * started by an instance of this admin in this VM.
     *
     * @return true of the activation system has been started
     */
    public synchronized static boolean wasStarted() {
	return started;
    }

    /**
     * Construct an instance of <code>ActivationSystemAdmin</code>.
     *
     * @param config         the configuration object for this test run
     *
     * @param serviceName  the prefix used to build the property
     *                       names needed to acquire service parameters
     *
     * @param index	     the instance number for this service.
     */
    public ActivationSystemAdmin(QAConfig config, 
			         String serviceName, 
			         int index)
    {
	super(config, serviceName, index);
    }

    /**
     * Starts the activation system. This method first checks whether
     * the activation system is already running. If so, a 
     * <code>TestException</code> is thrown. If the test property
     * <code>org.apache.river.qa.harness.runactivation</code> is defined and
     * has the value <code>false</code> this method returns without
     * starting the activation system. Otherwise, this method
     * check whether a running activation system can be detected (presumably
     * resulting from a cleanup problem from a previous test) and
     * attempt to shut it down if so. Then this method
     * constructs a command line for executing the activation system. 
     * If the activation system is phoenix, any configuration overrides
     * provided by registered <code>OverrideProvider</code>s are included
     * on the command line. Since this admin execs a process, it may be
     * necessary for these overrides to escape special characters 
     * interpreted by the underlying OS. 
     * <p>
     * The value of the system property <code>type</code> controls the content
     * and structure of the generated command line. This method then
     * starts the activation system by executing the generated command line
     * after which the <code>ActivationSystem</code> reference is obtained
     * for use by the <code>stop</code> and <code>getProxy</code> methods.
     * If the reference cannot be obtained within 10 seconds, a 
     * <code>TestException</code> is thrown. 
     *
     * @throws TestException    if the activation system has already been
     *                          started by an instance of this admin, 
     *                          if the <code>actCommand</code> parameter is not
     *                          found, if the activation system is already
     *                          running, if starting the activation system
     *                          fails, if the configuration file for
     *                          the activation system is undefined, or if
     *                          the type property does not have a proper value.
     *
     * @throws RemoteException  never.
     */
    public void start() throws RemoteException, TestException {
        synchronized (ActivationSystemAdmin.class){
            if (started) {
                throw new TestException("ActivationSystemAdmin: an activation "
                                      + "system has "
                                      + "already been started by this class");
            }
        }
        synchronized (this){
            if (!config.getBooleanConfigVal("org.apache.river.qa.harness.runactivation",
                                            true)) 
            {
                logger.log(Level.FINE, "Activation system is disabled");
                return;
            } else {
                cleanupRunningActivationSystem(); // clean up a lingering old one
            }
            String type = getServiceType();
            if (! (type.equals("rmid") || type.equals("phoenix"))) {   
                throw new TestException("'type' for " + serviceName
                                        + " is " + type + " - must be "
                                        + " either 'rmid' or 'phoenix'");
            }

            ArrayList l = new ArrayList(10);
            String actCommand = null;
            if (type.equals("rmid")) {
                l.add(System.getProperty("java.home") + File.separator + "bin"+ File.separator+ "rmid");
            } else {
                l.add(System.getProperty("java.home") + File.separator + "bin"+ File.separator+ "java");
            }
//	    l.add("-Djava.security.manager=org.apache.river.tool.SecurityPolicyWriter"); 
//	    l.add("-Dpolicy.provider=net.jini.security.policy.DynamicPolicyProvider");
//	    l.add("-Dpolicy.provider=org.apache.river.tool.DebugDynamicPolicyProvider"); 
//	    l.add("-Dorg.apache.river.tool.DebugDynamicPolicyProvider.grantAll=true");
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
            // don't use -jar syntax in case of augmented classpath
            if (type.equals("phoenix")) {
                l.add("-Djava.rmi.server.codebase=" + getServiceCodebase());
                l.add("-cp");
                l.add(getServiceClasspath());
                l.add("org.apache.river.phoenix.Activation");
                l.add(getServiceConfigurationFileName());
            }

            logDirName = getServicePersistenceLog();
            if (logDirName != null) {
                if (type.equals("rmid")) {
                    l.add("-log");
                    l.add(logDirName);
                } else { // unicode '"' to work around windows platform issue
                    l.add("org.apache.river.phoenix.persistenceDirectory=" 
                          + "\\u0022" + logDirName + "\\u0022");
                    addRegisteredOverrides(l);
                }
            }
            String[] cmdArray = (String[]) l.toArray(new String[0]);
            StringBuffer cmdBuf = new StringBuffer();
            for (int i = 0; i < cmdArray.length; i++) {
                if (i != 0) {
                    cmdBuf.append(" ");
                }
                cmdBuf.append(cmdArray[i]);
            }
            logger.log(Level.FINE, "command: '" + cmdBuf + "'");
            getServicePreparerName();
            logServiceParameters();
            try {
                actProcess = Runtime.getRuntime().exec(cmdArray);
                outPipe = new Pipe("activation system-out", 
                                   actProcess.getInputStream(),
                                   System.out,
                                   null, 
                                   new ActSysAnnotator("ActSys-out: "));
                outPipe.start();
                errPipe = new Pipe("activation system-err", 
                                   actProcess.getErrorStream(),
                                   System.out,
                                   null,
                                   new ActSysAnnotator("ActSys-err: "));
                errPipe.start();
            } catch (IOException e) {
                throw new TestException("ActivationSystemAdmin: Failed to exec "
                                      + "the activation system", e);
            }
            if (!config.activationUp(60)) {
                throw new TestException("ActivationSystemAdmin: activation system "
                                        + "did not start");
            }
            try {
                actSystem = net.jini.activation.ActivationGroup.getSystem();
            } catch (ActivationException e) {
                try {
                    int exitStatus = actProcess.exitValue();
                    throw new TestException("ActivationSystemAdmin: activation "
                                            + "system exited with status "
                                            + exitStatus, e);
                } catch (IllegalThreadStateException ignore) {
                    throw new TestException("ActivationSystemAdmin: problem "
                                            + "getting activation system",
                                            e);
                }
            }
            actSystem = (ActivationSystem) doProxyPreparation(actSystem);
        }
        synchronized (ActivationSystemAdmin.class){
            ActivationSystemAdmin.started = true;
        }
    }

    /**
     * Annotator for annotating output merged into test log
     */
    private static class ActSysAnnotator implements Pipe.Annotator {

	private final String annotation;

        ActSysAnnotator(String annotation) {
	    this.annotation = annotation;
	}

	public String getAnnotation() {
	    return annotation;
	}
    }

    /**
     * Stop the activation system. This method stops the activation system
     * by calling <code>ActivationSystem.shutdown</code>. A 
     * <code>RemoteException</code> is thrown if the shutdown fails.
     * <p>
     * If the test property
     * <code>org.apache.river.qa.harness.actdeathdelay</code> exists and is
     * greater than 0, the value is interpreted as the number of seconds
     * to sleep before shutting down the activation system. If it exists
     * and is non-positive, no delay is imposed. If the value does not
     * exist, a default value of 5 seconds is assumed.
     *
     * @throws RemoteException if failed to contact/shutdown the activation
     *                         system
     */
    public synchronized void stop() throws RemoteException {
	if (actSystem == null) {
	    return;
	}
	int delay = 
	    config.getIntConfigVal("org.apache.river.qa.harness.actdeathdelay", 5);
	if (delay > 0) {
	    logger.log(Level.FINEST, 
		       "activation death delayed " + delay + " seconds");
	    try {
                // Changed from sleep to wait because of synchronization
                // TODO: call in loop and check condition.
		wait(delay * 1000);
	    } catch (InterruptedException ignore) {
                Thread.currentThread().interrupt();
	    }
	}
	actSystem.shutdown();
	outPipe.waitTillEmpty(5000); //give pipes up to 5 seconds to drain
	errPipe.waitTillEmpty(5000);
	actSystem = null;
	actProcess = null;
	cleanupLogs();
    }

    /**
     * Clean up the activation system log directory. First, all files
     * in <code>logDir</code> are deleted, after which <code>logDir</code>
     * itself is deleted. Each deletion call is attempted up to 10 times, with
     * a 500ms delay between calls. This is done to work around cases where
     * the log files may be held by the activation system process, which may
     * still be active at the time this method is entered.
     */
    private void cleanupLogs() {
	if (logDirName == null) {
	    logger.log(Level.FINE, "Persistence directory is undefined");
	    return;
	}
	File logDir = new File(logDirName);
	File[] files = logDir.listFiles();
	if (files != null) {
	    if (files.length == 0) {
		logger.log(Level.FINEST,
			   "log directory " + logDir + " is empty");
	    }
	    for (int i = 0; i < files.length; i++) {
		boolean success = false;
		for (int counter = 10; counter >= 0; --counter) {
		    success = files[i].delete();
		    if (success) {
			break;
		    } else {
			try {
			    Thread.sleep(500);
			} catch (InterruptedException ignore) {
                            Thread.currentThread().interrupt();
			}
		    }
		}
		if (success) {
		    logger.log(Level.FINEST, 
			       "successfully deleted activation log file " 
			       + files[i]);
		} else {
		    logger.log(Level.FINEST, 
			       "failed to delete activation log file " 
			       + files[i]);
		}
	    }
	} else {
	    logger.log(Level.FINEST, 
		       "could not obtain list for log directory " 
		       + logDir);
	}
	if (logDir.delete()) {
	    logger.log(Level.FINEST, 
		       "successfully deleted activation log directory " 
		       + logDir);
	} else {
	    logger.log(Level.FINEST, 
		       "failed to delete activation log directory " 
		       + logDir);
	}
    }

    /**
     * Return the <code>ActivationSystem</code> reference for the activation
     * system managed by this admin.
     *
     * @return the <code>ActivationSystem</code> proxy
     */
    public synchronized Object getProxy() {
	return actSystem;
    }

    /**
     * Destroy a running activation system. Intended for cleanup when a test
     * expects to start it's own activation system. It is important to call this
     * method before any other use of the activation system. Specifically if
     * <code>ActivationGroup.getSystem</code> has already been called in this
     * VM, then the remote reference to the activation system will have been
     * cached. If this method is called, then that cached reference will no 
     * longer be valid and future attempts to contact the activation system will
     * fail (in this VM).
     */
    private void cleanupRunningActivationSystem() {
	String host;
	int port;
	ActivationSystem currSystem = null;
	String actURL = null;
	try {
	    port = Integer.getInteger("java.rmi.activation.port",
				      ActivationSystem.SYSTEM_PORT).intValue();
	    
	    try{
		Registry registry = LocateRegistry.getRegistry("", port, new TlsRMIClientSocketFactory());
		currSystem = (ActivationSystem) registry.lookup("java.rmi.activation.ActivationSystem");
	    } catch (IOException e){
		if (AccessController.doPrivileged(new GetBooleanAction("net.jini.security.allowInsecureConnections"))){
		    actURL = "//:" + port + "/java.rmi.activation.ActivationSystem";
		    currSystem = (ActivationSystem) Naming.lookup(actURL);
		} else {
		    throw e;
		}
	    }
	    currSystem.shutdown();
	} catch (SecurityException e) {
	    throw e; // configuration problem
	} catch (Exception e) {
	    return; // assume not be running
	}
	logger.log(Level.FINE, "Activation System detected - terminating");
	int delay =
	    config.getIntConfigVal("org.apache.river.qa.harness.actdeathdelay", 5);
	try {
	    for (int i = 0; i < delay; i++) {
		Thread.sleep(1000);
		Naming.lookup(actURL);
	    }
	    logger.log(Level.SEVERE, "Failed to destroy activation system");
	} catch (Exception lookupGone) {
	}
    }
}
