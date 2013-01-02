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

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.logging.Level;

public class ClassLoaderUtilTest extends StarterBase implements Test {

    public void run() throws Exception {

        // Assume ":" for path separator now and replace later
        // Assume "/" for file separator now and replace later
        String[] goodClasspathArgsList = {
	    "a", " a", "a ", " a ",
	    "./a", "/a", "a/", "./a/", "/a/",
	    "a:b", " a:b", "a:b ", " a:b ",
	    "./a:./b", "/a:/b", "a/:b/", "./a/:./b/", "/a/:/b/",

	    "./path with spaces:/another path with spaces",
	    "/tmp",
	    "/tmp/",
	};

        //Test good args
	URL[] urls = null;
	URL[] urls2 = null;
	String localPath = null;
	for (int i=0; i < goodClasspathArgsList.length; i++) {
	    try {
		if (':' != File.pathSeparatorChar) {
		    localPath = 
			goodClasspathArgsList[i].replace(
			    ':', File.pathSeparatorChar);
		    localPath = 
			localPath.replace('/', File.separatorChar);
		} else {
		    localPath = goodClasspathArgsList[i];
		}
   	        logger.log(Level.FINEST, "Trying good args: {0}", 
		    localPath);
		urls = ClassLoaderUtil.getClasspathURLs(localPath);
   	        logger.log(Level.FINEST, "Got: {0}", 
		    Arrays.asList(urls));
		// Verify that same set is returned from getImportCodebaseURLs
		urls2 = ClassLoaderUtil.getImportCodebaseURLs(localPath);
		if (!Arrays.equals(urls, urls2))
		    throw new TestException(
                        "Failed -- inconsistent url arrays for: [" 
		        + localPath + "] ");
		
	    } catch (Exception e) { 
		e.printStackTrace();
                throw new TestException(
                    "Failed -- failed good classpath args: [" 
		    + localPath + "] " + e);
	    }
	}

        // Assume ":" for path separator now and replace later
        // Assume "/" for file separator now and replace later
        String[] badClasspathArgsList = {
	    //What's a bad classpath look like?
	};

        //Test bad args
	for (int i=0; i < badClasspathArgsList.length; i++) {
	    try {
		if (':' != File.pathSeparatorChar) {
		    localPath = 
			badClasspathArgsList[i].replace(
			    ':', File.pathSeparatorChar);
		    localPath = 
			localPath.replace('/', File.separatorChar);
		} else {
		    localPath = badClasspathArgsList[i];
		}
   	        logger.log(Level.FINEST, "Trying bad args: {0}", 
		    localPath);
		try {
		    urls = 
			ClassLoaderUtil.getClasspathURLs(localPath);
   	            logger.log(Level.FINEST, "Got: {0}", 
		        Arrays.asList(urls));
                    throw new TestException(
                        "Failed -- successfully used bad classpath args: [" 
		        + badClasspathArgsList[i] + "] ");
		} catch (IOException ioe) {
   	            logger.log(Level.FINEST, 
			"Caught expected exception: ", ioe);
		}
		
	    } catch (Exception e) { 
		e.printStackTrace();
                throw new TestException(
                    "Failed -- unexpected exception: [" 
		    + badClasspathArgsList[i] + "] " + e);
	    }
	}


        String[] goodCodebaseArgsList = {
	    "http://host/"," http://host:8080/",
	    "http://host:8080/"," http://host:8080/", "http://host:8080/ ",
	    "http://host:8080/dir","http://host:8080/dir/", "http://host:8080/dir.jar",
	    "http://host:8080/a.jar http://host:8080/b.jar http://host:8080/c.jar",
	    "http://host:8080/a.jar file:./b.jar",
	    "http://host:80/a.jar http://host/a.jar",
	    "http://host:80/a%20path%20with%20spaces/a.jar http://host%20with%20spaces/a.jar",
    	};

        //Test good args
	for (int i=0; i < goodCodebaseArgsList.length; i++) {
	    try{
   	        logger.log(Level.FINEST, "Trying good args: {0}", 
		    goodCodebaseArgsList[i]);
		urls = ClassLoaderUtil.getCodebaseURLs(goodCodebaseArgsList[i]);
   	        logger.log(Level.FINEST, "Got: {0}", 
		    Arrays.asList(urls));
		// Verify that same set is returned from getImportCodebaseURLs
		urls2 = ClassLoaderUtil.getImportCodebaseURLs(goodCodebaseArgsList[i]);
		if (!Arrays.equals(urls, urls2))
		    throw new TestException(
                        "Failed -- inconsistent url arrays for: [" 
		        + goodCodebaseArgsList[i] + "] ");
	    } catch (Exception e) { 
		e.printStackTrace();
                throw new TestException(
                    "Failed -- failed good codebase args: [" 
		    + goodCodebaseArgsList[i] + "] " + e);
	    }
	}
	
        String[] badCodebaseArgsList = {
	    "soapxml://host:8080/", //unknown protocol
    	};
        //Test bad args
	for (int i=0; i < badCodebaseArgsList.length; i++) {
	    try {
   	        logger.log(Level.FINEST, "Trying bad codebase args: {0}", 
		    badCodebaseArgsList[i]);
		try {
		    urls = 
			ClassLoaderUtil.getCodebaseURLs(badCodebaseArgsList[i]);
   	            logger.log(Level.FINEST, "Got: {0}", 
		        Arrays.asList(urls));
                    throw new TestException(
                        "Failed -- successfully used bad codebase args: [" 
		        + badCodebaseArgsList[i] + "] ");
		} catch (IOException ioe) {
   	            logger.log(Level.FINEST, 
			"Caught expected exception: ", ioe);
		}
		
	    } catch (Exception e) { 
		e.printStackTrace();
                throw new TestException(
                    "Failed -- unexpected exception: [" 
		    + badCodebaseArgsList[i] + "] " + e);
	    }
	}


        return;
    }

}

