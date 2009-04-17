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

import java.util.logging.Level;

import com.sun.jini.qa.harness.TestException;
import com.sun.jini.start.SharedActivationGroupDescriptor;
import net.jini.config.ConfigurationException;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Properties;

public class SharedActivationGroupDescriptorTest extends StarterBase {

    private static String p = "policy";
    private static String cp = "classpath/bogus.jar";
    private static String logDir = "/tmp/bogus_dir";
    private static String vm = "/bin/java";
    private static String[] opts = { "-Xdebug", "-Xbogus" };
    private static String[] badProps = { p }; // Odd #
    private static String[] props = { "policy", p };

    public void run() throws Exception {

        Object[][] badArgsList = {
	    //policy, cp,   logDir,  vm,   opts, props
	    { null,   null, null, null, null, null},
	    { null,   null, logDir,  null, null, null},
	    { null,   cp,   null, null, null, null},
	    { null,   cp,   logDir,  null, null, null},
	    { p,      null, null, null, null, null},
	    { p,      null, logDir,  null, null, null},
	    { p,      cp,   null, null, null, null},
	    { p,      cp,   logDir,  null, null, badProps}
        };

        Object[][] goodArgsList = {
	    //policy, cp, logDir, vm,   opts, props
	    { p,      cp, logDir, null, null, null},
	    { p,      cp, logDir, vm,   opts, props}
	};

        Class[] consArgs = new Class[] {
	    String.class, String.class, String.class, 
	    String.class, String[].class, String[].class 
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

    static boolean checkArgs(Object[] args, 
			             SharedActivationGroupDescriptor sd) {
	boolean status = false;
	if ((String)args[0] != sd.getPolicy()) {
	    System.out.println("!Policy: " + args[0] + ":" + sd.getPolicy());
	} else if ((String)args[1] != sd.getClasspath()) {
	    System.out.println("!cp: " + args[1] + ":" + sd.getClasspath());
	} else if ((String)args[2] != sd.getLog()) {
	    System.out.println("!logDir: " + args[2] + ":" + sd.getLog());
	} else if ((String)args[3] != sd.getServerCommand()) {
	    System.out.println("!vm: " + args[3] + ":" + sd.getServerCommand());
	} else if (!checkOptions((String[])args[4], sd.getServerOptions())) {
	    System.out.println("!opts: " + args[4] + ":" + sd.getServerOptions());
	} else if (!checkProperties((String[])args[5], 
		                    sd.getServerProperties())) {
	    System.out.println("!props: " + args[5] + ":" + sd.getServerProperties());
	} else {
	    status = true;
	}
        return status;
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

