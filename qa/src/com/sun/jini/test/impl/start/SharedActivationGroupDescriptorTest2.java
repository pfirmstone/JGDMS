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

import com.sun.jini.action.GetIntegerAction;
import com.sun.jini.qa.harness.Test;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.start.SharedActivationGroupDescriptor;
import java.lang.reflect.Constructor;
import java.rmi.activation.ActivationSystem;
import java.util.Arrays;
import java.util.Properties;
import java.util.logging.Level;
import net.jini.config.ConfigurationException;





public class SharedActivationGroupDescriptorTest2 extends StarterBase implements Test {

    private static String p = "policy";
    private static String cp = "classpath/bogus.jar";
    private static String logDir = "/tmp/bogus_dir";
    private static String vm = "/bin/java";
    private static String[] opts = { "-Xdebug", "-Xbogus" };
    private static String[] badProps = { p }; // Odd #
    private static String[] props = { "policy", p };
    private static String host = "host";
    private static Integer port = new Integer(8080);

    public void run() throws Exception {

        Object[][] badArgsList = {
	    //policy, cp,   logDir,  vm,   opts, props,    host, port
	    { null,   null, null, null, null, null,     null, port},
	    { null,   null, logDir,  null, null, null,     null, port},
	    { null,   cp,   null, null, null, null,     null, port},
	    { null,   cp,   logDir,  null, null, null,     null, port},
	    { p,      null, null, null, null, null,     null, port},
	    { p,      null, logDir,  null, null, null,     null, port},
	    { p,      cp,   null, null, null, null,     null, port},
	    { p,      cp,   logDir,  null, null, badProps, null, port}
        };

        Object[][] goodArgsList = {
	    //policy, cp, logDir, vm,   opts, props, host, port
	    { p,      cp, logDir, null, null, null, null, new Integer(1)},
	    { p,      cp, logDir, vm,   opts, props, null, new Integer(8080)},
	    { p,      cp, logDir, vm,   opts, props, null, new Integer(0)},
	    { p,      cp, logDir, vm,   opts, props, null, new Integer(-1)}
	};

        Class[] consArgs = new Class[] {
	    String.class, String.class, String.class, 
	    String.class, String[].class, String[].class,
	    String.class, int.class
	};

	Constructor cons = null;
	cons = 
		SharedActivationGroupDescriptor.class.getConstructor(
		    consArgs);

        //Test bad args
	for (int i=0; i < badArgsList.length; i++) {
	    try {
		cons.newInstance(badArgsList[i]);
	        throw new TestException(
                    "Failed -- took bad args: " + i);
	    } catch (Exception e) { 
		logger.log(Level.INFO, "Expected exception: " + e);
	    }
	}

        //Test good args
	for (int i=0; i < goodArgsList.length; i++) {
	    try {
		SharedActivationGroupDescriptor sagd = 
		    (SharedActivationGroupDescriptor)
			cons.newInstance(goodArgsList[i]);
		if (!checkArgs(goodArgsList[i], sagd)) {
	            throw new TestException(
                        "Failed -- check args");
		}
	    } catch (Exception e) { 
	        throw new TestException(
                    "Failed -- failed good args: [" 
		    + i + "] ", e);
	    }
	}

	String[] negOpts = (String[])opts.clone();
	negOpts[negOpts.length-1] = negOpts[negOpts.length-1] + "_diff";
	String[] negProps = (String[])props.clone();
	negProps[negProps.length-1] = negProps[negProps.length-1] + "_diff";

	try {
	    SharedActivationGroupDescriptor sagd = 
	        (SharedActivationGroupDescriptor)
	            cons.newInstance(goodArgsList[goodArgsList.length-1]);
            if (checkOptions(negOpts, sagd.getServerOptions())) {
                throw new TestException(
                    "Failed -- check options");
            }
            if (checkProperties(negProps, sagd.getServerProperties())) {
                throw new TestException(
                    "Failed -- check properties");
            }
	} catch (Exception e) { 
	    throw new TestException(
                    "Failed -- failed check args/opts", e);
	}

	return;
    }

    private static boolean checkArgs(Object[] args, 
			             SharedActivationGroupDescriptor sd) {
	boolean status = false;
	if (!SharedActivationGroupDescriptorTest.checkArgs(args, sd)) {
	    System.out.println("Base args don't match");
	} else if (!checkHost((String)args[6], 
			      sd.getActivationSystemHost())) {
	    System.out.println("!host: " + args[6] + ":" 
		+ sd.getActivationSystemHost());
	} else if (!checkPort(((Integer)args[7]).intValue(), 
			      sd.getActivationSystemPort())) {
	    System.out.println("!port: " + args[7] + ":" 
		+ sd.getActivationSystemPort());
	} else {
	    status = true;
	}
        return status;
    }

    static boolean checkPort(int src, int dest) {
       int desired = 
	   (src <= 0) ? 
               (java.security.AccessController.doPrivileged(
                       new GetIntegerAction("java.rmi.activation.port",
                               ActivationSystem.SYSTEM_PORT))).intValue()
               : src;	
        return (desired == dest);
    }
    static boolean checkHost(String src, String dest) {
	String desired = (src == null) ? "" : src;
        return (desired.equals(dest));
    }

    private static boolean checkOptions(String[] src, String[] dest) {
	String[] customOpts = new String[] {"-cp", cp};
	if (src != null) {
	    String[] tmp =  new String[src.length + customOpts.length];
	    System.arraycopy(customOpts, 0, tmp, 0, customOpts.length);
	    System.arraycopy(src, 0, tmp, customOpts.length,
	        src.length);
            return Arrays.equals(tmp, dest);
	} else {
	    return Arrays.equals(customOpts, dest);
	}
    }

    private static boolean checkProperties(String[] args, Properties props) {
	if (args != null) {
	    for (int i=0; i<args.length; i+=2) {
	        if (!props.getProperty(args[i]).equals(args[i+1])) 
		    return false;
	    }
	} 
	if (!props.getProperty("java.security.policy").equals(p)) 
		    return false;
	
	return true;
    }
}

