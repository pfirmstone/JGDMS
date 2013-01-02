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

import com.sun.jini.qa.harness.Test;
import java.util.logging.Level;

import com.sun.jini.qa.harness.TestException;
import com.sun.jini.start.LifeCycle;
import com.sun.jini.start.NonActivatableServiceDescriptor;
import net.jini.config.ConfigurationException;
import net.jini.security.BasicProxyPreparer;
import net.jini.security.ProxyPreparer;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Properties;
import java.util.logging.Level;

public class NonActivatableServiceDescriptorTest extends StarterBase implements Test {

    private static String cb = "http://host:port/cb";
    private static String p = "policy";
    private static String cp = "classpath/bogus.jar";
    private static String impl = "implClass";
    private static String[] confArgs = { cb, p, cp, impl };
    private static LifeCycle lc = 
        new LifeCycle() { // default, no-op object
	    public boolean unregister(Object impl) { return false; }
	};
    private static ProxyPreparer pp = new BasicProxyPreparer();

    public void run() throws Exception {

        Object[][] badArgsList = {
	    //cb,   policy, cp,   impl, conf, life, prep  
	    { null, null,   null, null, null, null, null},
	    { null, null,   null, impl, null, null, null},
	    { null, null,   cp,   null, null, null, null},
	    { null, null,   cp,   impl, null, null, null},
	    { null, p,      null, null, null, null, null},
	    { null, p,      null, impl, null, null, null},
	    { null, p,      cp,   null, null, null, null},
	    { null, p,      cp,   impl, null, null, null},
	    { cb,   null,   null, null, null, null, null},
	    { cb,   null,   null, impl, null, null, null},
	    { cb,   null,   cp,   null, null, null, null},
	    { cb,   null,   cp,   impl, null, null, null},
	    { cb,   p,      null, null, null, null, null},
	    { cb,   p,      null, impl, null, null, null},
	    { cb,   p,      cp,   null, null, null, null},
        };

        Object[][] goodArgsList = {
	    { cb,   p,    cp,   impl, null, null, null},
	    { cb,   p,    cp,   impl, null, null, pp},
	    { cb,   p,    cp,   impl, null, lc, null},
	    { cb,   p,    cp,   impl, null, lc, pp},
	    { cb,   p,    cp,   impl, confArgs, null, null},
	    { cb,   p,    cp,   impl, confArgs, null, pp},
	    { cb,   p,    cp,   impl, confArgs, lc, null},
	    { cb,   p,    cp,   impl, confArgs, lc, pp},
	};

        // Excercise "main" constructor
        Class[] consArgs = new Class[] {
	    String.class, String.class, String.class, String.class,
	    String[].class, LifeCycle.class, ProxyPreparer.class};
	Constructor cons = 
            NonActivatableServiceDescriptor.class.getConstructor(consArgs);

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
		NonActivatableServiceDescriptor tsd = 
		    (NonActivatableServiceDescriptor)
			cons.newInstance(goodArgsList[i]);
		if (!checkMainArgs(goodArgsList[i], tsd)) {
                    throw new TestException(
                    "Failed -- check args");
		}
		if (!checkLifeCycle((LifeCycle)goodArgsList[i][5], tsd)) {
                    throw new TestException(
                    "Failed -- check LifeCycle");
		}                	    
		if (!checkPreparer((ProxyPreparer)goodArgsList[i][6], tsd)) {
                    throw new TestException(
                    "Failed -- check ProxyPreparer");
		}                	                
            } catch (Exception e) { 
		e.printStackTrace();
                throw new TestException(
                    "Failed -- failed good args: [" 
		    + i + "] " + e);
	    }
	}
        
       // Excercise alternate constructor        
        consArgs = new Class[] {
	    String.class, String.class, String.class, String.class,
	    String[].class, LifeCycle.class};
	cons = 
            NonActivatableServiceDescriptor.class.getConstructor(consArgs);

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
                logger.log(Level.FINEST, "Trying good args: {0}", list);
                Object[] args = list.toArray();
		NonActivatableServiceDescriptor tsd = 
		    (NonActivatableServiceDescriptor)
			cons.newInstance(args);
		if (!checkMainArgs(args, tsd)) {
                    throw new TestException(
                    "Failed -- check args");
		}
		if (!checkLifeCycle((LifeCycle)args[5], tsd)) {
                    throw new TestException(
                    "Failed -- check args");
		}                
	    } catch (Exception e) { 
		e.printStackTrace();
                throw new TestException(
                    "Failed -- failed good args: [" 
		    + i + "] " + e);
	    }
	}        
      // Excercise another alternate constructor        
        consArgs = new Class[] {
	    String.class, String.class, String.class, String.class,
	    String[].class, ProxyPreparer.class};
	cons = 
            NonActivatableServiceDescriptor.class.getConstructor(consArgs);
        
        logger.log(Level.FINEST, 
            "Trying constructor that takes the following args: {0}",
            Arrays.asList(consArgs));
       //Test bad args
	for (int i=0; i < badArgsList.length; i++) {
	    try {
                list.clear();
                list.add(0, badArgsList[i][0]);
                list.add(1, badArgsList[i][1]);
                list.add(2, badArgsList[i][2]);
                list.add(3, badArgsList[i][3]);
                list.add(4, badArgsList[i][4]);
                list.add(5, badArgsList[i][6]);
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
                list.add(5, goodArgsList[i][6]);
                logger.log(Level.FINEST, "Trying good args: {0}", list);
                Object[] args = list.toArray();
		NonActivatableServiceDescriptor tsd = 
		    (NonActivatableServiceDescriptor)
			cons.newInstance(args);
		if (!checkMainArgs(args, tsd)) {
                    throw new TestException(
                    "Failed -- check args");
		}
		if (!checkPreparer((ProxyPreparer)args[5], tsd)) {
                    throw new TestException(
                    "Failed -- check ProxyPreparer");
		}                	                
            } catch (Exception e) { 
		e.printStackTrace();
                throw new TestException(
                    "Failed -- failed good args: [" 
		    + i + "] " + e);
	    }
	}                
        // Excercise another alternate constructor        
        consArgs = new Class[] {
	    String.class, String.class, String.class, String.class,
	    String[].class};
	cons = 
            NonActivatableServiceDescriptor.class.getConstructor(consArgs);
        
        logger.log(Level.FINEST, 
            "Trying constructor that takes the following args: {0}",
            Arrays.asList(consArgs));
       //Test bad args
	for (int i=0; i < badArgsList.length; i++) {
	    try {
                list.clear();
                list.add(0, badArgsList[i][0]);
                list.add(1, badArgsList[i][1]);
                list.add(2, badArgsList[i][2]);
                list.add(3, badArgsList[i][3]);
                list.add(4, badArgsList[i][4]);
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
                logger.log(Level.FINEST, "Trying good args: {0}", list);                
		NonActivatableServiceDescriptor tsd = 
		    (NonActivatableServiceDescriptor)
			cons.newInstance(list.toArray());
		if (!checkMainArgs(list.toArray(), tsd)) {
                    throw new TestException(
                    "Failed -- check args");
		}
	    } catch (Exception e) { 
		e.printStackTrace();
                throw new TestException(
                    "Failed -- failed good args: [" 
		    + i + "] " + e);
	    }
	}                

        return;
    }

    private static boolean checkMainArgs(Object[] args, 
			             NonActivatableServiceDescriptor sd) {
	boolean status = false;
	if ((String)args[0] != sd.getExportCodebase()) {
	    System.out.println("!cb: " + args[0] + ":" + sd.getExportCodebase());
	} else if ((String)args[1] != sd.getPolicy()) {
	    System.out.println("!policy: " + args[1] + ":" + sd.getPolicy());
	} else if ((String)args[2] != sd.getImportCodebase()) {
	    System.out.println("!cp: " + args[2] + ":" + sd.getImportCodebase());
	} else if ((String)args[3] != sd.getImplClassName()) {
	    System.out.println("!impl: " + args[3] + ":" + sd.getImplClassName());
	} else if (!Arrays.equals((String[])args[4], sd.getServerConfigArgs())) {
	    System.out.println("!conf: " + args[4] + ":" + sd.getServerConfigArgs());
	} else {
	    status = true;
	}

        return status;
    }
    
    private static boolean checkLifeCycle(LifeCycle lc, 
	NonActivatableServiceDescriptor sd) 
    {
	boolean status = true;
	if (lc == null && sd.getLifeCycle() == null) { // verify that NASD created a default LifeCycle
	    System.out.println("No default LifeCycle.");
            status = false;
        } else if (lc != null && 
                   (!lc.equals(sd.getLifeCycle()))) {
	    System.out.println("!LifeCycle: " + lc + ":" + sd.getLifeCycle());
            status = false;
	}

        return status;    
    }
    
    private static boolean checkPreparer(ProxyPreparer pp, 
	NonActivatableServiceDescriptor sd) 
    {
	boolean status = true;
	if (pp == null && 
            (sd.getServicePreparer() != null)) {
	    System.out.println("Illegal default ProxyPreparer.");
            status = false;
        } else if (pp != null && 
                   (!pp.equals(sd.getServicePreparer()))) {
	    System.out.println("!ProxyPreparer: " + pp + ":" + sd.getServicePreparer());
            status = false;
	}

        return status;    
    }    
}

