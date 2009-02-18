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
 * @bug 4903755
 * @summary When PreferredClassProvider.loadClass is passed an array
 * class name, it should follow the prescription of the preferred list
 * as if it had been passed the name of the element type of the same
 * array class.
 *
 * @build ArrayClassNames Foo
 * @run main/othervm/policy=security.policy ArrayClassNames
 */

import java.io.File;
import java.rmi.server.RMIClassLoader;

public class ArrayClassNames {
    public static void main(String[] args) throws Exception {
	System.err.println("\nRegresstion test for bug 4903755\n");

	if (System.getSecurityManager() == null) {
	    System.setSecurityManager(new SecurityManager());
	}

	ClassLoader syscl = ClassLoader.getSystemClassLoader();

	String testsrc = System.getProperty("test.src", ".");
	String testclasses = System.getProperty("test.classes", ".");

	String testsrcURL =
	    (new File(testsrc)).toURI().toURL().toExternalForm();
	String testclassesURL =
	    (new File(testclasses)).toURI().toURL().toExternalForm();

	String codebase = testsrcURL + " " + testclassesURL;
	System.err.println("codebase: \"" + codebase + "\"");

	Class localFoo = Foo.class;
	System.err.println("local Foo: " + nameAndLoader(localFoo));

	Class codebaseFoo = RMIClassLoader.loadClass(codebase, "Foo", syscl);
	System.err.println("codebase Foo: " + nameAndLoader(codebaseFoo));

	if (codebaseFoo == localFoo) {
	    throw new RuntimeException("TEST FAILED: localFoo == codebaseFoo");
	}

	Class localArrayFoo = Foo[].class;
	System.err.println("local Foo[]: " + nameAndLoader(localArrayFoo));

	Class codebaseArrayFoo =
	    RMIClassLoader.loadClass(codebase, "[LFoo;", syscl);
	System.err.println("codebase Foo[]: " +
			   nameAndLoader(codebaseArrayFoo));

	if (codebaseArrayFoo == localArrayFoo) {
	    throw new RuntimeException(
		"TEST FAILED: localArrayFoo == codebaseArrayFoo");
	}

	System.err.println("TEST PASSED");
    }

    private static String nameAndLoader(Class c) {
	return "\"" + c.getName() + "\"^" + c.getClassLoader();
    }
}
