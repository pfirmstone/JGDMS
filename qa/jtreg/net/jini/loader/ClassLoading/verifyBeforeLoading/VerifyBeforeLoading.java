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
 * @bug 5090030
 * @summary When ClassLoading.loadClass or ClassLoading.loadProxyClass
 * is invoked with verifyCodebaseIntegrity true, it should not pass a
 * codebase that contains URLs that have not been verified to provide
 * integrity to the RMIClassLoader provider.  For example, if
 * integrity is being enforced, the contents of a potentially
 * corrupted preferred list should not be able to influence the
 * behavior of the class loading operation.
 *
 * @library ../../../../../testlibrary
 * @build VerifyBeforeLoading Foo Bar
 * @build TestLibrary HTTPD
 * @run main/othervm/policy=security.policy VerifyBeforeLoading
 */

import java.io.File;
import java.net.MalformedURLException;
import java.rmi.server.RMIClassLoader;
import net.jini.loader.ClassLoading;
import net.jini.loader.pref.PreferredClassProvider;

public class VerifyBeforeLoading {

    private static final ClassLoader verifierLoader =
	VerifyBeforeLoading.class.getClassLoader();

    public static void main(String[] args) throws Exception {
	System.err.println("\nRegression test for bug 5090030\n");

	if (System.getSecurityManager() == null) {
	    System.setSecurityManager(new SecurityManager());
	}

	/*
	 * Set this test's custom RMIClassLoader provider.
	 */
	System.setProperty("java.rmi.server.RMIClassLoaderSpi",
			   TestProvider.class.getName());

	/*
	 * Install Bar in separate codebase; keep Foo in class path.
	 */
	TestLibrary.installClassInCodebase("Bar", "codebase");
	File codebaseDir =
	    new File(System.getProperty("user.dir", "."), "codebase");
	HTTPD httpd = new HTTPD(HTTPD.getDefaultPort(), codebaseDir.getPath());
	String codebase = "http://localhost:" + HTTPD.getDefaultPort() + "/";

	ClassLoader defaultLoader =
	    Thread.currentThread().getContextClassLoader();

	/*
	 * Verify that our test provider is being used.
	 */
	try {
	    RMIClassLoader.loadClass(codebase, "Foo", defaultLoader);
	    throw new RuntimeException(
		"TEST FAILED: test provider not being used");
	} catch (TestProviderException e) {
	    // OK, our provider is definitely being used
	}

	/*
	 * Foo is in the test's class path, so we expect to be able to
	 * load it from the non-integrity-providing codebase, as was
	 * the case with the bug-- but our test provider makes sure
	 * that it does not get passed the non-integrity-providing
	 * codebase.
	 */
	ClassLoading.loadClass(codebase, "Foo", defaultLoader,
			       true, verifierLoader);
	ClassLoading.loadProxyClass(codebase, new String[] { "Foo" },
				    defaultLoader,
				    true, verifierLoader);

	/*
	 * Bar is only available from the non-integrity-providing
	 * codebase, so we expect a ClassNotFoundException attempting
	 * to load it from there, as was the case with the bug-- but
	 * again, our test provider makes sure that it does not get
	 * passed the non-integrity-providing codebase.
	 */
	try {
	    ClassLoading.loadClass(codebase, "Bar", defaultLoader,
				   true, verifierLoader);
	    throw new RuntimeException("TEST FAILED: Bar loaded successfully");
	} catch (ClassNotFoundException e) {
	    if (e.getCause() instanceof SecurityException) {
		e.printStackTrace();
	    } else {
		throw new RuntimeException(
		    "TEST FAILED: ClassNotFoundException, " +
		    "but doesn't contain SecurityException", e);
	    }
	}
	try {
	    ClassLoading.loadProxyClass(codebase, new String[] { "Bar" },
					defaultLoader,
					true, verifierLoader);
	    throw new RuntimeException("TEST FAILED: Bar loaded successfully");
	} catch (ClassNotFoundException e) {
	    if (e.getCause() instanceof SecurityException) {
		e.printStackTrace();
	    } else {
		throw new RuntimeException(
		    "TEST FAILED: ClassNotFoundException, " +
		    "but doesn't contain SecurityException", e);
	    }
	}

	System.err.println("TEST PASSED");
    }

    /**
     * An extension of PreferredClassProvider that throws a
     * TestProviderException if a non-null codebase is passeed to its
     * loadClass or loadProxyClass methods.
     **/
    public static class TestProvider extends PreferredClassProvider {

	public TestProvider() { }

	public Class loadClass(String codebase, String name,
			       ClassLoader defaultLoader)
	    throws MalformedURLException, ClassNotFoundException
	{
	    if (codebase != null) {
		throw new TestProviderException(
		    "TEST FAILED: non-null codebase passed to provider: " +
		    codebase);
	    }
	    return super.loadClass(codebase, name, defaultLoader);
	}

	public Class loadProxyClass(String codebase, String[] interfaces,
				    ClassLoader defaultLoader)
	    throws MalformedURLException, ClassNotFoundException
	{
	    if (codebase != null) {
		throw new TestProviderException(
		    "TEST FAILED: non-null codebase passed to provider: " +
		    codebase);
	    }
	    return super.loadProxyClass(codebase, interfaces, defaultLoader);
	}
    }

    private static class TestProviderException extends RuntimeException {
	TestProviderException(String message) {
	    super(message);
	}
    }
}
