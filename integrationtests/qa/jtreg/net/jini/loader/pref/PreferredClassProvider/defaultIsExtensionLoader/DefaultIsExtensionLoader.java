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
 * 
 * @summary Test that ensures that classes in the system class loader can be found when
 * the defaultLoader is the system class loader and 
 *
 * @author Laird Dornin
 *
 * @library ../../../../../../testlibrary
 * @build DefaultIsExtensionLoader TestClass
 *
 * @run main/othervm/policy=security.policy -Djava.rmi.server.codebase=file:///dummy/ DefaultIsExtensionLoader
 */

import java.rmi.server.RMIClassLoader;
import java.util.Arrays;

public class DefaultIsExtensionLoader {
    public static void main(String[] args) {
	try {
	    TestLibrary.suggestSecurityManager("java.lang.SecurityManager");

	    ClassLoader extensionLoader =
		ClassLoader.getSystemClassLoader().getParent();

	    Class c =
		RMIClassLoader.loadClass(
		    System.getProperty("java.rmi.server.codebase"),
		    "TestClass", extensionLoader);

	    System.err.println("class name: " +	c.getName() + " location: " +
		c.getProtectionDomain().getCodeSource().getLocation());

	    System.err.println("Classpath class found when default loader " +
			       "was extension loader");
	    
	    System.err.println("TEST PASSED");

	} catch (ClassNotFoundException e) {
	    e.printStackTrace();
	    TestLibrary.bomb("TEST FAILED: unable to load class " +
			     "in system class loader");
	} catch (Exception e) {
	    if (e instanceof RuntimeException) {
		throw (RuntimeException) e;
	    }
	    TestLibrary.bomb("unexpected exception", e);
	}
    }
}
