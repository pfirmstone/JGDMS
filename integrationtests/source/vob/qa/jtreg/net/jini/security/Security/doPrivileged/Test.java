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
 * @summary Verify basic functionality of Security.doPrivileged() methods.
 * @library ../../../../../testlibrary
 * @build Test Caller CallerImpl CheckPermissionAction StringPrincipal TestLibrary CheckSubjectAction
 * @run main/othervm/policy=policy Test
 */

import java.lang.reflect.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.*;
import java.util.*;
import javax.security.auth.Subject;
import net.jini.security.Security;

/*
 * Permission key:
 * 
 * RuntimePermission("A") -- granted to all code
 * RuntimePermission("B") -- granted to CallerImpl
 * RuntimePermission("C") -- granted to CallerImpl with StringPrincipal("foo")
 * AllPermission          -- granted to CheckPermissionAction
 */

public class Test {
    public static void main(String[] args) throws Throwable {
	if (System.getSecurityManager() == null) {
	    System.setSecurityManager(new SecurityManager());
	}
	ClassLoader ldr1 = new URLClassLoader(new URL[]{
	    TestLibrary.installClassInCodebase("CallerImpl", "cb1")});
	ClassLoader ldr2 = new URLClassLoader(new URL[]{
	    TestLibrary.installClassInCodebase(
		"CheckPermissionAction", "cb2")});

	final Caller caller = (Caller) 
	    Class.forName("CallerImpl", true, ldr1).newInstance();
	final Constructor checkPermActionConstructor = 
	    Class.forName("CheckPermissionAction", true, ldr2).getConstructor(
		new Class[]{Permission.class});
	
	caller.doCall(new Permission[]{new RuntimePermission("A")},
		      new Permission[]{new RuntimePermission("B"),
				       new RuntimePermission("C"),
				       new RuntimePermission("D")},
		      new Permission[]{new RuntimePermission("A"),
				       new RuntimePermission("B")},
		      new Permission[]{new RuntimePermission("C"),
				       new RuntimePermission("D")},
		      checkPermActionConstructor, null);
	
	HashSet hs = new HashSet();
	hs.add(new StringPrincipal("foo"));
	final Subject s = new Subject(
	    true, hs, Collections.EMPTY_SET, Collections.EMPTY_SET);
	Subject.doAs(s, new PrivilegedExceptionAction() {
	    public Object run() throws Exception {
		caller.doCall(new Permission[]{new RuntimePermission("A")},
			      new Permission[]{new RuntimePermission("B"),
					       new RuntimePermission("C"),
					       new RuntimePermission("D")},
			      new Permission[]{new RuntimePermission("A"),
					       new RuntimePermission("B"),
					       new RuntimePermission("C")},
			      new Permission[]{new RuntimePermission("D")},
			      checkPermActionConstructor, s);
		return null;
	    }
	});
	
	final Exception ex = new Exception();
	try {
	    Security.doPrivileged(new PrivilegedExceptionAction() {
		public Object run() throws Exception {
		    throw ex;
		}
	    });
	} catch (PrivilegedActionException pae) {
	    if (pae.getException() != ex) {
		throw new Error();
	    }
	}

	PrivilegedAction checkPermAction =
	    (PrivilegedAction) checkPermActionConstructor.newInstance(
		new Object[]{ new RuntimePermission("D") });
	Method m = Security.class.getMethod("doPrivileged", 
		       new Class[]{ PrivilegedAction.class });
	try {
	    m.invoke(null, new Object[]{ checkPermAction });
	    throw new Error();
	} catch (InvocationTargetException e) {
	    Throwable t = e.getCause();
	    if (!(t instanceof SecurityException)) {
		throw t;
	    }
	}
    }
}
