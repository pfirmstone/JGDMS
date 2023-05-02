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
 * @bug 4857361
 * @bug 4863274
 * @summary When the context class loader or an ancestor of the
 * context class loader is chosen because of a boomerang match, or
 * when the context class loader is chosen because of a null codebase
 * argument (and the default loader was not used), and this chosen
 * loader is not a URLClassLoader, then there should not be a
 * ClassCastException because of an assumption that the chosen loader
 * is a URLClassLoader while checking permissions.  Also, the
 * permissions checked should always be for the URLs of the codebase
 * argument, not the "import" URLs of the chosen loader.
 *
 * @build NonURLAncestor Foo
 * @run main/othervm/policy=security.policy NonURLAncestor
 */

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.rmi.server.RMIClassLoader;
import net.jini.loader.ClassAnnotation;
import net.jini.loader.pref.PreferredClassLoader;

public class NonURLAncestor {

    private static final String CLASS_NAME = "Foo";
    private static final String[] INTERFACE_NAMES = { "Foo" };

    public static void main(String[] args) throws Exception {
	System.err.println("\nRegression test for bug 4857361\n");

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
        sb.append("nonURLAncestor").append(File.separator);
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

	ClassLoader nonURLLoader =
	    new NonURLLoader(codebase,
			     NonURLAncestor.class.getClassLoader());

	Class c;
	ClassLoader l;

	/*
	 * Make sure that we are preferring the class from the
	 * codebase as expected:
	 */
	c = RMIClassLoader.loadClass(null, CLASS_NAME, null);
	check(c.getClassLoader(), Foo.class.getClassLoader());
	c = RMIClassLoader.loadClass(codebase, CLASS_NAME, null);
	check(Boolean.valueOf(c.getClassLoader() instanceof PreferredClassLoader), Boolean.TRUE);

	/*
	 * Try with the context class loader being a
	 * non-URLClassLoader and null codebase arguments:
	 */
	Thread.currentThread().setContextClassLoader(nonURLLoader);

	c = RMIClassLoader.loadClass(null, CLASS_NAME, null);
	check(c.getClassLoader(), Foo.class.getClassLoader());
	c = RMIClassLoader.loadProxyClass(null, INTERFACE_NAMES, null);
	check(c.getClassLoader(), nonURLLoader);
	l = RMIClassLoader.getClassLoader(null);
	check(l, nonURLLoader);

	/*
	 * Try with the context class loader being a
	 * non-URLClassLoader and matching codebase arguments:
	 */
	c = RMIClassLoader.loadClass(codebase, CLASS_NAME, null);
	check(c.getClassLoader(), Foo.class.getClassLoader());
	c = RMIClassLoader.loadProxyClass(codebase, INTERFACE_NAMES, null);
	check(c.getClassLoader(), nonURLLoader);
	l = RMIClassLoader.getClassLoader(codebase);
	check(l, nonURLLoader);

	/*
	 * Try with the parent of the context class loader being a
	 * non-URLClassLoader and matching codebase arguments:
	 */
	Thread.currentThread().setContextClassLoader(
	    new URLClassLoader(new URL[0], nonURLLoader));

	c = RMIClassLoader.loadClass(codebase, CLASS_NAME, null);
	check(c.getClassLoader(), Foo.class.getClassLoader());
	c = RMIClassLoader.loadProxyClass(codebase, INTERFACE_NAMES, null);
	check(c.getClassLoader(), nonURLLoader);
	l = RMIClassLoader.getClassLoader(codebase);
	check(l, nonURLLoader);

	/*
	 * Try with the parent of the context class loader being a
	 * URLClassLoader with a matching annotation but different
	 * import URLs-- should not prevent (because of lack of
	 * permission for its import URLs) leapfrogging intermediate
	 * preferred loader:
	 */
	ClassLoader matchingURLLoader =
	    new URLLoader(new URL[] { new URL("http://java.sun.com/") },
			  codebase, NonURLAncestor.class.getClassLoader());
	ClassLoader nonmatchingPreferredLoader =
	    new PreferredClassLoader(codebaseURLs, matchingURLLoader,
				     "", false);
	Thread.currentThread().setContextClassLoader(
	    nonmatchingPreferredLoader);

	c = RMIClassLoader.loadClass(codebase, CLASS_NAME, null);
	check(c.getClassLoader(), Foo.class.getClassLoader());
	c = RMIClassLoader.loadProxyClass(codebase, INTERFACE_NAMES, null);
	check(c.getClassLoader(), matchingURLLoader);
	l = RMIClassLoader.getClassLoader(codebase);
	check(l, matchingURLLoader);

	System.err.println("TEST PASSED");
    }

    private static class NonURLLoader
	extends ClassLoader
	implements ClassAnnotation
    {
	private final String annotation;

	NonURLLoader(String annotation, ClassLoader parent) {
	    super(parent);
	    this.annotation = annotation;
	}

	public String getClassAnnotation() {
	    return annotation;
	}
    }

    private static class URLLoader
	extends URLClassLoader
	implements ClassAnnotation
    {
	private final String annotation;

	URLLoader(URL[] urls, String annotation, ClassLoader parent) {
	    super(urls, parent);
	    this.annotation = annotation;
	}

	public String getClassAnnotation() {
	    return annotation;
	}
    }

    private static void check(Object actual, Object expected) {
	if (actual != expected) {
	    throw new AssertionError("\nexpected: " + expected +
				     "\n  actual: " + actual);
	}
    }
}
