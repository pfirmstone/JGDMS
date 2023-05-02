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
/* @test
 * @bug 5015331
 * @summary PreferredClassLoader should have a constructor that has a
 * URLStreamHandlerFactory, like URLClassLoader does.  If specified,
 * the URLStreamHandlerFactory should be passed to the superclass
 * constructor, and it should be used to create a URLStreamHandler for
 * "jar:" URLs that get used when the loader creates such URLs for
 * retrieving the preferred list and resources from codebase path
 * elements that do not end with "/".
 *
 * @build TestURLStreamHandlerFactory Foo
 * @run main/othervm/policy=security.policy TestURLStreamHandlerFactory
 */

import java.io.File;
import java.net.URL;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import net.jini.loader.pref.PreferredClassLoader;

public class TestURLStreamHandlerFactory {

    public static void main(String[] args) throws Exception {
	System.err.println("\n\nRegression test for RFE 5015331\n");

	if (System.getSecurityManager() == null) {
	    System.setSecurityManager(new SecurityManager());
	}

	String testsrc;
        String root = System.getProperty("test.root", ".");  
        StringBuilder sb = new StringBuilder();
        sb.append(root).append(File.separator).append("net").append(File.separator);
        sb.append("jini").append(File.separator).append("loader");
        sb.append(File.separator).append("pref").append(File.separator);
        sb.append("PreferredClassLoader").append(File.separator);
        sb.append("urlStreamHandlerFactory").append(File.separator);
        testsrc = sb.toString();
	URL[] codebaseURLs = new URL[] {
	    new URL((new File(testsrc)).toURI().toURL(), "foo.jar"),
	};
	Factory factory = new Factory(null);
	ClassLoader loader = new PreferredClassLoader(
	     codebaseURLs,
	     TestURLStreamHandlerFactory.class.getClassLoader(),
	     null,
	     false,
	     factory);
	System.err.println("created loader with URLStreamHandlerFactory: " +
			   loader);

	Class c = Class.forName("Foo", false, loader);
	System.err.println("loaded through loader: " + c);
	
	int factoryInvocations = factory.getInvocations();
	System.err.println("URLStreamHandlerFactory invoked " +
			   factoryInvocations + " time" +
			   (factoryInvocations == 1 ? "" : "s"));
	if (factoryInvocations < 2) {
	    throw new RuntimeException(
		"TEST FAILED: factory invoked fewer than two times");
	}
	System.err.println("TEST PASSED");
    }

    private static class Factory implements URLStreamHandlerFactory {

	private final URLStreamHandler handler;
	private final Object lock = new Object();
	private int invocations = 0;

	Factory(URLStreamHandler handler) {
	    this.handler = handler;
	}

	public URLStreamHandler createURLStreamHandler(String protocol) {
	    if (!protocol.equals("jar")) {
		throw new RuntimeException(
		    "unexpected protocol passed to factory: " + protocol);
	    }
	    synchronized (lock) {
		invocations++;
	    }
	    return handler;
	}

	int getInvocations() {
	    synchronized (lock) {
		return invocations;
	    }
	}
    }
}
