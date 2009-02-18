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
import com.sun.jini.start.SharedActivatableServiceDescriptor;
import net.jini.config.ConfigurationException;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Properties;

public class SharedActivatableServiceDescriptorTest2 extends StarterBase {

    private static String cb = "http://host:port/cb";
    private static String pol = "policy";
    private static String cp = "file:/classpath/bogus.jar";
    private static String impl = "implClass";
    private static String logDir = "/tmp/logDir";
    private static String[] confArgs = { cb, pol, cp, impl };
    private static Boolean restart = new Boolean(true);
    private static String host = "host";
    private static Integer port = new Integer(8080);

    public void run() throws Exception {

        Object[][] badArgsList = {
	    //cb,   pol,  cp,   impl, logDir,  conf, restart, host, port
	    { null, null, null, null, null, null, restart, host, port },
	    { null, null, null, null, logDir,  null, restart, host, port },
	    { null, null, null, impl, null, null, restart, host, port },
	    { null, null, null, impl, logDir,  null, restart, host, port },
	    { null, null, cp,   null, null, null, restart, host, port },
	    { null, null, cp,   null, logDir,  null, restart, host, port },
	    { null, null, cp,   impl, null, null, restart, host, port },
	    { null, null, cp,   impl, logDir,  null, restart, host, port },
	    { null, pol,  null, null, null, null, restart, host, port },
	    { null, pol,  null, null, logDir,  null, restart, host, port },
	    { null, pol,  null, impl, null, null, restart, host, port },
	    { null, pol,  null, impl, logDir,  null, restart, host, port },
	    { null, pol,  cp,   null, null, null, restart, host, port },
	    { null, pol,  cp,   null, logDir,  null, restart, host, port },
	    { null, pol,  cp,   impl, null, null, restart, host, port },
	    { null, pol,  cp,   impl, logDir,  null, restart, host, port },
	    {   cb, null, null, null, null, null, restart, host, port },
	    {   cb, null, null, null, logDir,  null, restart, host, port },
	    {   cb, null, null, impl, null, null, restart, host, port },
	    {   cb, null, null, impl, logDir,  null, restart, host, port },
	    {   cb, null, cp,   null, null, null, restart, host, port },
	    {   cb, null, cp,   null, logDir,  null, restart, host, port },
	    {   cb, null, cp,   impl, null, null, restart, host, port },
	    {   cb, null, cp,   impl, logDir,  null, restart, host, port },
	    {   cb, pol,  null, null, null, null, restart, host, port },
	    {   cb, pol,  null, null, logDir,  null, restart, host, port },
	    {   cb, pol,  null, impl, null, null, restart, host, port },
	    {   cb, pol,  null, impl, logDir,  null, restart, host, port },
	    {   cb, pol,  cp,   null, null, null, restart, host, port },
	    {   cb, pol,  cp,   null, logDir,  null, restart, host, port },
	    {   cb, pol,  cp,   impl, null, null, restart, host, port },
        };

        Object[][] goodArgsList = {
	    { cb, pol,  cp,   impl, logDir,  null, restart, host, port },
	    { cb, pol,  cp,   impl, logDir,  confArgs, restart, host, port },
	};

        Class[] consArgs = new Class[] {
	    String.class, String.class, String.class, String.class,
	    String.class, String[].class, boolean.class, 
	    String.class, int.class};
	Constructor cons = null;
	cons = 
		SharedActivatableServiceDescriptor.class.getConstructor(
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
		SharedActivatableServiceDescriptor tsd = 
		    (SharedActivatableServiceDescriptor)
			cons.newInstance(goodArgsList[i]);
		if (!checkArgs(goodArgsList[i], tsd)) {
	            throw new TestException(
                        "Failed -- check args");
		}
	    } catch (Exception e) { 
	        throw new TestException(
                    "Failed -- failed good args: [" 
		    + i + "] ", e);
	    }
	}

	return;
    }

    private static boolean checkArgs(Object[] args, 
			             SharedActivatableServiceDescriptor sd) {
	boolean status = false;
	if (!SharedActivatableServiceDescriptorTest.checkMainArgs(args, sd)) {
	    System.out.println("Base args don't match");
        } else if (!SharedActivationGroupDescriptorTest2.checkHost(
	    (String)args[7], sd.getActivationSystemHost())) {
	    System.out.println("!host: " + args[7] + ":" 
		+ sd.getActivationSystemHost());
        } else if (!SharedActivationGroupDescriptorTest2.checkPort(
	    ((Integer)args[8]).intValue(), sd.getActivationSystemPort())) {
	    System.out.println("!port: " + args[8] + ":" 
		+ sd.getActivationSystemPort());
	} else {
	    status = true;
	}
        return status;
    }
}

