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
import com.sun.jini.start.ClassLoaderUtil;
import com.sun.jini.start.ActivateWrapper;

import java.lang.reflect.Constructor;
import java.net.URL;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Properties;
import java.util.logging.Level;

public class ExportClassLoaderTest extends StarterBase implements Test {

    private static String importURLString = 
        "http://host:8080/service.jar http://host:8080/jsk.jar";
    private static ClassLoader parent = 
        ActivateWrapper.class.getClassLoader();
    private static String exportURLString = 
        "http://host:8081/service-dl.jar http://host:8081/jsk-dl.jar";

    public void run() throws Exception {
        
        URL[] importURLs = ClassLoaderUtil.getCodebaseURLs(importURLString);        
        URL[] exportURLs = ClassLoaderUtil.getCodebaseURLs(exportURLString);
        Object[][] badArgsList = {
	    //importURLs,        exportURLs,        parent,  
	    { null,              null,              null},
	    { null,              null,              parent},
	    { null,              exportURLs,        null},
	    { null,              exportURLs,        parent},
        };

        Object[][] goodArgsList = {
	    //importURLs,        exportURLs,        parent,  
	    { importURLs,        null,              null},
	    { importURLs,        null,              parent},
	    { importURLs,        exportURLs,        null},
	    { importURLs,        exportURLs,        parent},
	    { importURLs,        new URL[] {},      parent},
	    { new URL[] {},      exportURLs,        parent},
 	    { new URL[] {},      new URL[] {},      parent},
        };

        // Excercise "main" constructor
        Class[] consArgs = new Class[] {
	    URL[].class, URL[].class, ClassLoader.class};
        Class ecl = parent.loadClass(
            "com.sun.jini.start.ActivateWrapper$ExportClassLoader");
	Constructor cons = 
            ecl.getDeclaredConstructor(consArgs);
        cons.setAccessible(true);

        logger.log(Level.FINEST, 
            "Trying constructor: {0}", cons);
        //Test bad args
	for (int i=0; i < badArgsList.length; i++) {
	    try {
   	        logger.log(Level.FINEST, "Trying bad args: [{0}, {1}, {2}]",
                    new Object[] {
                        badArgsList[i][0]==null?null:Arrays.asList((URL[])badArgsList[i][0]), 
                        badArgsList[i][1]==null?null:Arrays.asList((URL[])badArgsList[i][1]),
                        badArgsList[i][2]
                    });
		cons.newInstance(badArgsList[i]);
                throw new TestException(
                    "Failed -- took bad args: " + i);
	    } catch (java.lang.reflect.InvocationTargetException ite) { 
                Throwable t = ite.getCause();
	        if (t instanceof NullPointerException) {
                    logger.log(Level.FINEST, 
                        "Got expected NullPointerException", t);
                } else {
                    logger.log(Level.FINEST, 
                        "Got unexpected Exception", t);
                    throw ite;
                }
	    }
	}
        Object o = null;
 	for (int i=0; i < goodArgsList.length; i++) {
	    try {
   	        logger.log(Level.FINEST, "Trying good args: [{0}, {1}, {2}]",
                    new Object[] {
                        goodArgsList[i][0]==null
                            ?null:Arrays.asList((URL[])goodArgsList[i][0]), 
                        goodArgsList[i][1]==null
                            ?null:Arrays.asList((URL[])goodArgsList[i][1]),
                        goodArgsList[i][2]
                    });
		o = cons.newInstance(goodArgsList[i]);
                logger.log(Level.FINEST, "Created ExportClassLoader: {0}", new Object[] {o});
//TODO - would like to check passed-in args to retreived args, but can't access 
// ExportLoader at compile time.                
            } catch (Exception e) { 
		e.printStackTrace();
                throw new TestException(
                    "Failed -- failed good args: [" 
		    + i + "] " + e);
	    }
	}        

	// Verify that two indetically configured export class loaders
	// still produce unique toString() values.
	Object[] args = new Object[] {importURLs, exportURLs, parent};
        Object o1 = cons.newInstance(args);
        logger.log(Level.FINEST, 
	    "Created ExportClassLoader1: {0}", new Object[] {o1});
        Object o2 = cons.newInstance(args);
        logger.log(Level.FINEST, 
	    "Created ExportClassLoader2: {0}", new Object[] {o2});
	if (o1.equals(o2)) {
            throw new TestException("Did not get unique strings");
	}
        return;
    }
}

