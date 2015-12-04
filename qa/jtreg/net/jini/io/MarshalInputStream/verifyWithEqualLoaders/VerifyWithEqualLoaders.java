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
 * @summary MarshalInputStream should verify codebase integrity (if
 * constructed to do so) even if the defaultLoader and the context class
 * loader identical, as long as neither of them are the same as or a
 * descendant of the defining class loader of the class being resolved.
 * @author Peter Jones
 *
 * @library ../../../../../testlibrary
 * @build VerifyWithEqualLoaders
 * @build Foo
 * @build HTTPD
 * @run main/othervm/policy=security.policy VerifyWithEqualLoaders
 */

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.rmi.server.RMIClassLoader;
import java.util.Collections;
import net.jini.io.MarshalInputStream;
import net.jini.io.MarshalOutputStream;

public class VerifyWithEqualLoaders {

    public static void main(String[] args) throws Exception {

	TestLibrary.suggestSecurityManager(null);

	String className = "Foo";
	TestLibrary.installClassInCodebase(className, "codebase");
	File codebaseDir =
	    new File(System.getProperty("user.dir", "."), "codebase");
	HTTPD httpd = new HTTPD(HTTPD.getDefaultPort(), codebaseDir.getPath());
	String codebase = "http://localhost:" + HTTPD.getDefaultPort() + "/";
	ClassLoader defaultLoader = ClassLoader.getSystemClassLoader();

	Class fooClass = RMIClassLoader.loadClass(codebase, className);
	Object toWrite = fooClass.newInstance();

	ByteArrayOutputStream bout = new ByteArrayOutputStream();
	MarshalOutputStream mout =
 	    new MarshalOutputStream(bout, Collections.EMPTY_LIST);
	mout.writeObject(toWrite);
	mout.flush();

	ByteArrayInputStream bin =
	    new ByteArrayInputStream(bout.toByteArray());
	MarshalInputStream min =
	    new MarshalInputStream(bin, defaultLoader, true, defaultLoader,
				   Collections.EMPTY_LIST);
	min.useCodebaseAnnotations();

	try {
	    Object read = min.readObject();
	    throw new RuntimeException(
		"TEST FAILED: object read successfully");
	} catch (ClassNotFoundException e) {
	    if (e.getCause() instanceof SecurityException) {
		e.printStackTrace();
		System.err.println("TEST PASSED");
	    } else {
		throw new RuntimeException(
		    "TEST FAILED: ClassNotFoundException, " +
		    "but doesn't contain SecurityException", e);
	    }
	}
    }
}
