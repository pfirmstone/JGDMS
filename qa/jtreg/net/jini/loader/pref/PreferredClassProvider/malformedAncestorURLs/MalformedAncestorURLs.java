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
 * @bug 4863352
 * @bug 6232020
 * @summary When the default loader, the context class loader, or an
 * ancestor of the context class loader has an annotation string with
 * a malformed URL, that shouldn't cause PreferredClassProvider's
 * loadClass, loadProxyClass, or getClassLoader methods to throw a
 * MalformedURLException; those methods analyze these other loader's
 * annotations for "boomerang" matches, but if one of them contains a
 * malformed URL, then it must not match the codebase argument's URLs
 * (which are checked for malformed URLs earlier), so the method
 * should proceed gracefully as if the match failed rather than
 * throwing a MalformedURLException to the caller.  Of course, if the
 * loadClass, loadProxyClass, or getClassLoader methods are invoked
 * with a codebase argument that has a malformed URL, they should
 * always throw a MalformedURLException, even if other conditions
 * would seem to allow them to return normally, such as if there is no
 * security manager.
 *
 * @build MalformedAncestorURLs Foo
 * @run main/othervm/policy=security.policy MalformedAncestorURLs
 */

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.server.RMIClassLoader;
import net.jini.loader.ClassAnnotation;

public class MalformedAncestorURLs {

    private static final String MALFORMED_URL = "bogus:-+-*-+-";
    private static final String CLASS_NAME = "Foo";
    private static final String[] INTERFACE_NAMES = { "Foo" };

    public static void main(String[] args) throws Exception {
	System.err.println("\nRegression test for bug 4863352\n");

	if (System.getSecurityManager() == null) {
	    System.setSecurityManager(new SecurityManager());
	}

	String testsrc;
        String root = System.getProperty("test.root", ".");  
        StringBuilder sb = new StringBuilder();
        sb.append(root).append(File.separator).append("net").append(File.separator);
        sb.append("jini").append(File.separator).append("loader");
        sb.append(File.separator).append("pref").append(File.separator);
        sb.append("PreferredClassProvider").append(File.separator);
        sb.append("malformedAncestorURLs").append(File.separator);
        testsrc = sb.toString();
	String testclasses = System.getProperty("test.classes", ".");

	URL[] codebaseURLs = new URL[] {
	    (new File(testsrc)).toURI().toURL(),
	    (new File(testclasses)).toURI().toURL()
	};

	String codebase =
	    codebaseURLs[0].toExternalForm() + " " +
	    codebaseURLs[1].toExternalForm();

	System.err.println("codebase: \"" + codebase + "\"");

	ClassLoader malformedLoader =
	    new Loader(MALFORMED_URL,
		       MalformedAncestorURLs.class.getClassLoader());

	/*
	 * Try with the default loader having a malformed URL in its
	 * annotation:
	 */
	RMIClassLoader.loadClass(codebase, CLASS_NAME, malformedLoader);
	RMIClassLoader.loadProxyClass(codebase, INTERFACE_NAMES,
				      malformedLoader);

	/*
	 * Try with the context class loader having a malformed URL in
	 * its annotation:
	 */
	Thread.currentThread().setContextClassLoader(malformedLoader);

	RMIClassLoader.loadClass(codebase, CLASS_NAME, null);
	RMIClassLoader.loadProxyClass(codebase, INTERFACE_NAMES, null);
	RMIClassLoader.getClassLoader(codebase);

	/*
	 * Try with the parent of the context class loader having a
	 * malformed URL in its annotation:
	 */
	Thread.currentThread().setContextClassLoader(
	    new Loader("", malformedLoader));

	RMIClassLoader.loadClass(codebase, CLASS_NAME, null);
	RMIClassLoader.loadProxyClass(codebase, INTERFACE_NAMES, null);
	RMIClassLoader.getClassLoader(codebase);

	/*
	 * Verify that malformed URL in codebase argument still
	 * results in a MalformedURLException:
	 */
	checkMalformedCodebaseURL();

	/*
	 * Even with no security manager:
	 */
	System.setSecurityManager(null);
	checkMalformedCodebaseURL();

	System.err.println("TEST PASSED");
    }

    private static void checkMalformedCodebaseURL() throws Exception {
	try {
	    RMIClassLoader.loadClass(MALFORMED_URL, CLASS_NAME, null);
	    throw new RuntimeException("expected MalformedURLException");
	} catch (MalformedURLException e) {
	}
	try {
	    RMIClassLoader.loadProxyClass(MALFORMED_URL, INTERFACE_NAMES,
					  null);
	    throw new RuntimeException("expected MalformedURLException");
	} catch (MalformedURLException e) {
	}
	try {
	    RMIClassLoader.getClassLoader(MALFORMED_URL);
	    throw new RuntimeException("expected MalformedURLException");
	} catch (MalformedURLException e) {
	}
    }

    private static class Loader
	extends ClassLoader
	implements ClassAnnotation
    {
	private final String annotation;

	Loader(String annotation, ClassLoader parent) {
	    super(parent);
	    this.annotation = annotation;
	}

	public String getClassAnnotation() {
	    return annotation;
	}
    }

    private static String nameAndLoader(Class c) {
	return "\"" + c.getName() + "\"^" + c.getClassLoader();
    }
}
