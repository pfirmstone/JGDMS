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

import com.sun.jini.action.GetIntegerAction;
import com.sun.jini.qa.harness.Test;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.start.ServiceStarter;
import com.sun.jini.start.SharedActivatableServiceDescriptor;
import net.jini.config.ConfigurationException;
import net.jini.security.BasicProxyPreparer;
import net.jini.security.ProxyPreparer;

import java.lang.reflect.Constructor;
import java.rmi.activation.ActivationSystem;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;

public class SharedActivatableServiceDescriptorTest extends StarterBase implements Test {

    private static String cb = "http://host:port/cb";
    private static String pol = "policy";
    private static String cp = "file:/classpath/bogus.jar";
    private static String impl = "implClass";
    private static String logDir = "/tmp/logDir";
    private static String[] confArgs = { cb, pol, cp, impl };
    private static Boolean restart = new Boolean(true);
    private static String host = "host";
    private static Integer port = new Integer(1234);
    
    private static ProxyPreparer pp = new BasicProxyPreparer();

    public void run() throws Exception {

        Object[][] badArgsList = {
	    //cb,   pol,  cp,   impl, logDir,  conf, ipxy, opxy, restart, host, port
	    { null, null, null, null, null, null, null, null, restart, host, port },
	    { null, null, null, null, logDir,  null, null, null, restart, host, port },
	    { null, null, null, impl, null, null, null, null, restart, host, port },
	    { null, null, null, impl, logDir,  null, null, null, restart, host, port },
	    { null, null, cp,   null, null, null, null, null, restart, host, port },
	    { null, null, cp,   null, logDir,  null, null, null, restart, host, port },
	    { null, null, cp,   impl, null, null, null, null, restart, host, port },
	    { null, null, cp,   impl, logDir,  null, null, null, restart, host, port },
	    { null, pol,  null, null, null, null, null, null, restart, host, port },
	    { null, pol,  null, null, logDir,  null, null, null, restart, host, port },
	    { null, pol,  null, impl, null, null, null, null, restart, host, port },
	    { null, pol,  null, impl, logDir,  null, null, null, restart, host, port },
	    { null, pol,  cp,   null, null, null, null, null, restart, host, port },
	    { null, pol,  cp,   null, logDir,  null, null, null, restart, host, port },
	    { null, pol,  cp,   impl, null, null, null, null, restart, host, port },
	    { null, pol,  cp,   impl, logDir,  null, null, null, restart, host, port },
	    {   cb, null, null, null, null, null, null, null, restart, host, port },
	    {   cb, null, null, null, logDir,  null, null, null, restart, host, port },
	    {   cb, null, null, impl, null, null, null, null, restart, host, port },
	    {   cb, null, null, impl, logDir,  null, null, null, restart, host, port },
	    {   cb, null, cp,   null, null, null, null, null, restart, host, port },
	    {   cb, null, cp,   null, logDir,  null, null, null, restart, host, port },
	    {   cb, null, cp,   impl, null, null, null, null, restart, host, port },
	    {   cb, null, cp,   impl, logDir,  null, null, null, restart, host, port },
	    {   cb, pol,  null, null, null, null, null, null, restart, host, port },
	    {   cb, pol,  null, null, logDir,  null, null, null, restart, host, port },
	    {   cb, pol,  null, impl, null, null, null, null, restart, host, port },
	    {   cb, pol,  null, impl, logDir,  null, null, null, restart, host, port },
	    {   cb, pol,  cp,   null, null, null, null, null, restart, host, port },
	    {   cb, pol,  cp,   null, logDir,  null, null, null, restart, host, port },
	    {   cb, pol,  cp,   impl, null, null, null, null, restart, host, port },
        };

        Object[][] goodArgsList = {
            { cb, pol,  cp,   impl, logDir,  confArgs, pp, pp, restart, null, port },
            { cb, pol,  cp,   impl, logDir,  confArgs, pp, null, restart, host, port },
            { cb, pol,  cp,   impl, logDir,  confArgs, pp, null, restart, null, port },
	    { cb, pol,  cp,   impl, logDir,  confArgs, null, pp, restart, host, port },
            { cb, pol,  cp,   impl, logDir,  confArgs, null, pp, restart, null, port },
            { cb, pol,  cp,   impl, logDir,  confArgs, null, null, restart, host, port },
            { cb, pol,  cp,   impl, logDir,  confArgs, null, null, restart, null, port },
            { cb, pol,  cp,   impl, logDir,  confArgs, pp, pp, restart, host, port },
	};

        Class[] consArgs = new Class[] {
	    String.class, String.class, String.class, String.class,
	    String.class, String[].class, ProxyPreparer.class,
            ProxyPreparer.class, boolean.class, String.class, int.class};
	Constructor cons = null;
	cons = SharedActivatableServiceDescriptor.class.getConstructor(
		    consArgs);

        logger.log(Level.FINEST, 
            "Trying constructor that takes the following args: {0}",
            Arrays.asList(consArgs));
        //Test bad args
	for (int i=0; i < badArgsList.length; i++) {
	    try {
   	        logger.log(Level.FINEST, "Trying bad args: {0}", 
		    Arrays.asList(badArgsList[i]));
                cons.newInstance(badArgsList[i]);
	        throw new TestException(
		    "Failed -- took bad args: " + i);
	    } catch (java.lang.reflect.InvocationTargetException e) { 
		Throwable t = e.getCause();
		if (t instanceof NullPointerException) {
		    logger.log(Level.FINEST,
		        "Got expected NullPointerException", t);
		} else {
		    logger.log(Level.FINEST,
			"Got unexpected Exception", t);
		    throw e;
		}
	    }
	}

	for (int i=0; i < goodArgsList.length; i++) {
	    try {
   	        logger.log(Level.FINEST, "Trying good args: {0}", 
		    Arrays.asList(goodArgsList[i]));
                SharedActivatableServiceDescriptor tsd = 
		    (SharedActivatableServiceDescriptor)
			cons.newInstance(goodArgsList[i]);
		if (!checkMainArgs(goodArgsList[i], tsd)) {
	            throw new TestException(
			"Failed -- check main args");
		}
                if (!checkRestart((Boolean)goodArgsList[i][8], tsd)) {
	            throw new TestException(
			"Failed -- check restart flag");
		}
                if (!checkInnerProxyPreparer((ProxyPreparer)goodArgsList[i][6], tsd)) {
	            throw new TestException(
			"Failed -- check inner proxy preparer");
		}
                if (!checkServicePreparer((ProxyPreparer)goodArgsList[i][7], tsd)) {
	            throw new TestException(
			"Failed -- check service proxy preparer");
		}
                if (!checkHost((String)goodArgsList[i][9], tsd)) {
	            throw new TestException(
			"Failed -- check activation host");
		}
                if (!checkPort((Integer)goodArgsList[i][10], tsd)) {
	            throw new TestException(
			"Failed -- check activation port");
		}
 	    } catch (Exception e) { 
	        throw new TestException(
		    "Failed -- failed good args: [" 
		    + i + "] ", e);
	    }
	}

       // Excercise alternate constructor        
        consArgs = new Class[] {
	    String.class, String.class, String.class, String.class,
	    String.class, String[].class, boolean.class, String.class, 
            int.class};
	cons = null;
	cons = SharedActivatableServiceDescriptor.class.getConstructor(
		    consArgs);
        
        logger.log(Level.FINEST, 
            "Trying constructor that takes the following args: {0}",
            Arrays.asList(consArgs));
        //Test bad args
        ArrayList list = new ArrayList();
	for (int i=0; i < badArgsList.length; i++) {
	    try {
                list.clear();
                list.add(0, badArgsList[i][0]);
                list.add(1, badArgsList[i][1]);
                list.add(2, badArgsList[i][2]);
                list.add(3, badArgsList[i][3]);
                list.add(4, badArgsList[i][4]);
                list.add(5, badArgsList[i][5]);
                list.add(6, badArgsList[i][8]);
                list.add(7, badArgsList[i][9]);
                list.add(8, badArgsList[i][10]);
   	        logger.log(Level.FINEST, "Trying bad args: {0}", list);
                cons.newInstance(list.toArray());
	        throw new TestException(
		    "Failed -- took bad args: " + i);
	    } catch (java.lang.reflect.InvocationTargetException e) { 
		Throwable t = e.getCause();
		if (t instanceof NullPointerException) {
		    logger.log(Level.FINEST,
		        "Got expected NullPointerException", t);
		} else {
		    logger.log(Level.FINEST,
			"Got unexpected Exception", t);
		    throw e;
		}
	    }
	}

	for (int i=0; i < goodArgsList.length; i++) {
	    try {
                list.clear();
                list.add(0, goodArgsList[i][0]);
                list.add(1, goodArgsList[i][1]);
                list.add(2, goodArgsList[i][2]);
                list.add(3, goodArgsList[i][3]);
                list.add(4, goodArgsList[i][4]);
                list.add(5, goodArgsList[i][5]);
                list.add(6, goodArgsList[i][8]);
                list.add(7, goodArgsList[i][9]);
                list.add(8, goodArgsList[i][10]);
   	        logger.log(Level.FINEST, "Trying good args: {0}", list);
                Object[] args = list.toArray();
                SharedActivatableServiceDescriptor tsd = 
		    (SharedActivatableServiceDescriptor)
			cons.newInstance(args);
		if (!checkMainArgs(args, tsd)) {
	            throw new TestException(
			"Failed -- check main args");
		}
                if (!checkRestart((Boolean)args[6], tsd)) {
	            throw new TestException(
			"Failed -- check restart flag");
		}
                if (!checkHost((String)args[7], tsd)) {
	            throw new TestException(
			"Failed -- check activation host");
		}
                if (!checkPort((Integer)args[8], tsd)) {
	            throw new TestException(
			"Failed -- check activation port");
		}
 	    } catch (Exception e) { 
	        throw new TestException(
		    "Failed -- failed good args: [" 
		    + i + "] ", e);
	    }
	}
       // Excercise alternate constructor        
        consArgs = new Class[] {
	    String.class, String.class, String.class, String.class,
	    String.class, String[].class, ProxyPreparer.class,
            ProxyPreparer.class, boolean.class};
	cons = null;
	cons = SharedActivatableServiceDescriptor.class.getConstructor(
		    consArgs);
        
        logger.log(Level.FINEST, 
            "Trying constructor that takes the following args: {0}",
            Arrays.asList(consArgs));
        //Test bad args
        list = new ArrayList();
	for (int i=0; i < badArgsList.length; i++) {
	    try {
                list.clear();
                list.add(0, badArgsList[i][0]);
                list.add(1, badArgsList[i][1]);
                list.add(2, badArgsList[i][2]);
                list.add(3, badArgsList[i][3]);
                list.add(4, badArgsList[i][4]);
                list.add(5, badArgsList[i][5]);
                list.add(6, badArgsList[i][6]);
                list.add(7, badArgsList[i][7]);
                list.add(8, badArgsList[i][8]);
   	        logger.log(Level.FINEST, "Trying bad args: {0}", list);
                cons.newInstance(list.toArray());
	        throw new TestException(
		    "Failed -- took bad args: " + i);
	    } catch (java.lang.reflect.InvocationTargetException e) { 
		Throwable t = e.getCause();
		if (t instanceof NullPointerException) {
		    logger.log(Level.FINEST,
		        "Got expected NullPointerException", t);
		} else {
		    logger.log(Level.FINEST,
			"Got unexpected Exception", t);
		    throw e;
		}
	    }
	}

	for (int i=0; i < goodArgsList.length; i++) {
	    try {
                list.clear();
                list.add(0, goodArgsList[i][0]);
                list.add(1, goodArgsList[i][1]);
                list.add(2, goodArgsList[i][2]);
                list.add(3, goodArgsList[i][3]);
                list.add(4, goodArgsList[i][4]);
                list.add(5, goodArgsList[i][5]);
                list.add(6, goodArgsList[i][6]);
                list.add(7, goodArgsList[i][7]);
                list.add(8, goodArgsList[i][8]);
   	        logger.log(Level.FINEST, "Trying good args: {0}", list);
                Object[] args = list.toArray();
                SharedActivatableServiceDescriptor tsd = 
		    (SharedActivatableServiceDescriptor)
			cons.newInstance(args);
		if (!checkMainArgs(args, tsd)) {
	            throw new TestException(
			"Failed -- check main args");
		}
                if (!checkRestart((Boolean)args[8], tsd)) {
	            throw new TestException(
			"Failed -- check restart flag");
		}
                if (!checkInnerProxyPreparer((ProxyPreparer)args[6], tsd)) {
	            throw new TestException(
			"Failed -- check inner proxy preparer");
		}
                if (!checkServicePreparer((ProxyPreparer)args[7], tsd)) {
	            throw new TestException(
			"Failed -- check service proxy preparer");
		} 	    } catch (Exception e) { 
	        throw new TestException(
		    "Failed -- failed good args: [" 
		    + i + "] ", e);
	    }
	}
       // Excercise alternate constructor        
        consArgs = new Class[] {
	    String.class, String.class, String.class, String.class,
	    String.class, String[].class, boolean.class};
	cons = null;
	cons = SharedActivatableServiceDescriptor.class.getConstructor(
		    consArgs);
        
        logger.log(Level.FINEST, 
            "Trying constructor that takes the following args: {0}",
            Arrays.asList(consArgs));
        //Test bad args
        list = new ArrayList();
	for (int i=0; i < badArgsList.length; i++) {
	    try {
                list.clear();
                list.add(0, badArgsList[i][0]);
                list.add(1, badArgsList[i][1]);
                list.add(2, badArgsList[i][2]);
                list.add(3, badArgsList[i][3]);
                list.add(4, badArgsList[i][4]);
                list.add(5, badArgsList[i][5]);
                list.add(6, badArgsList[i][8]);
   	        logger.log(Level.FINEST, "Trying bad args: {0}", list);
                cons.newInstance(list.toArray());
	        throw new TestException(
		    "Failed -- took bad args: " + i);
	    } catch (java.lang.reflect.InvocationTargetException e) { 
		Throwable t = e.getCause();
		if (t instanceof NullPointerException) {
		    logger.log(Level.FINEST,
		        "Got expected NullPointerException", t);
		} else {
		    logger.log(Level.FINEST,
			"Got unexpected Exception", t);
		    throw e;
		}
	    }
	}

	for (int i=0; i < goodArgsList.length; i++) {
	    try {
                list.clear();
                list.add(0, goodArgsList[i][0]);
                list.add(1, goodArgsList[i][1]);
                list.add(2, goodArgsList[i][2]);
                list.add(3, goodArgsList[i][3]);
                list.add(4, goodArgsList[i][4]);
                list.add(5, goodArgsList[i][5]);
                list.add(6, goodArgsList[i][8]);
   	        logger.log(Level.FINEST, "Trying good args: {0}", list);
                Object[] args = list.toArray();
                SharedActivatableServiceDescriptor tsd = 
		    (SharedActivatableServiceDescriptor)
			cons.newInstance(args);
		if (!checkMainArgs(args, tsd)) {
	            throw new TestException(
			"Failed -- check main args");
		}
                if (!checkRestart((Boolean)args[6], tsd)) {
	            throw new TestException(
			"Failed -- check restart flag");
		}
            } catch (Exception e) { 
	        throw new TestException(
		    "Failed -- failed good args: [" 
		    + i + "] ", e);
	    }
	}
       
        return;
    }

    static boolean checkMainArgs(Object[] args, 
			     SharedActivatableServiceDescriptor sd) {
	boolean status = false;
	if ((String)args[0] != sd.getExportCodebase()) {
	    System.out.println("!cb: " + args[0] + ":" + sd.getExportCodebase());
	} else if ((String)args[1] != sd.getPolicy()) {
	    System.out.println("!policy: " + args[1] + ":" + sd.getPolicy());
	} else if ((String)args[2] != sd.getImportCodebase()) {
	    System.out.println("!cp: " + args[2] + ":" + sd.getImportCodebase());
	} else if ((String)args[3] != sd.getImplClassName()) {
	    System.out.println("!impl: " + args[3] + ":" + sd.getImplClassName());
	} else if ((String)args[4] != sd.getSharedGroupLog()) {
	    System.out.println("!logDir: " + args[4] + ":" + sd.getSharedGroupLog());
	} else if (!Arrays.equals((String[])args[5], sd.getServerConfigArgs())) {
	    System.out.println("!conf: " + args[5] + ":" + sd.getServerConfigArgs());
	} else {
	    status = true;
	}
        return status;
    }
    
    static boolean checkRestart(Boolean flag, 
			     SharedActivatableServiceDescriptor sd) {
	boolean status = true;
	if (flag.booleanValue() != sd.getRestart()) {
	    System.out.println("Restart flags don't match: flag[" + flag 
                + "], sd[" + sd.getRestart() + "]");
            status = false;            
	}
        return status;
    }
    private static boolean checkServicePreparer(ProxyPreparer pp,
        SharedActivatableServiceDescriptor sd) 
    {
        return checkPreparer(pp, sd.getServicePreparer()); 
    }
    
    private static boolean checkInnerProxyPreparer(ProxyPreparer pp,
        SharedActivatableServiceDescriptor sd) 
    {
        return checkPreparer(pp, sd.getInnerProxyPreparer()); 
    }
    
    private static boolean checkPreparer(ProxyPreparer p1, 
	ProxyPreparer p2) 
    {
	boolean status = true;
	if ((p1 == null) && (p2 != null)) {
	    System.out.println("Illegal default ProxyPreparer.");
            status = false;
        } else if ((p1 != null) && (!p1.equals(p2))) {
	    System.out.println("ProxyPreparer: " + p1 + " != " + p2);
            status = false;
	}
        return status;    
    }
    static boolean checkHost(String host, 
			     SharedActivatableServiceDescriptor sd) {
	boolean status = true;
        String desiredHost = (host == null)? "" : host;
        if (!desiredHost.equals(sd.getActivationSystemHost())) {
	    System.out.println("Hosts don't match: host[" + desiredHost 
                + "], sd[" + sd.getActivationSystemHost() + "]");
            status = false;            
	} 
        return status;
   }    
    static boolean checkPort(Integer port, 
			     SharedActivatableServiceDescriptor sd) {
	boolean status = true;
        int desiredPort = (port.intValue() <= 0)?
            getActivationSystemPort() : port.intValue();
        if (desiredPort != sd.getActivationSystemPort()) {
	    System.out.println("Ports don't match: host[" + desiredPort 
                + "], sd[" + sd.getActivationSystemPort() + "]");
            status = false;            
	} 
        return status;
   }
    static int getActivationSystemPort() {
	return ((Integer)java.security.AccessController.doPrivileged(
                    new GetIntegerAction("java.rmi.activation.port",
			ActivationSystem.SYSTEM_PORT))).intValue();
    }

}

