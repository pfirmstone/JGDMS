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
 * @bug 4402154 4302502
 * 
 * @summary test SecureDispatcher.checkClientPermisson
 * @author Bob Scheifler
 * @run main/othervm/policy=security.policy CheckClientPermission
 */
import net.jini.export.ServerContext;
import net.jini.io.context.ClientSubject;
import net.jini.jeri.BasicInvocationDispatcher;
import java.security.AccessControlException;
import java.security.Permission;
import java.security.SecurityPermission;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;

public class CheckClientPermission implements Runnable, ClientSubject {
    /** Client subject */
    private Subject s;
    /** Permission to check */
    private Permission perm;

    private static Subject bob = subject(new String[]{"CN=bob"});
    private static Subject joe = subject(new String[]{"CN=joe"});
    private static Subject jim = subject(new String[]{"CN=jim"});
    private static Subject joebob = subject(new String[]{"CN=joe", "CN=bob"});
    private static Subject jimjoe = subject(new String[]{"CN=jim", "CN=joe"});
    private static Subject jimbob = subject(new String[]{"CN=jim", "CN=bob"});

    /** Create a subject containing X500Principals with the specified names. */
    public static Subject subject(String[] dns) {
	Set prins = new HashSet();
	for (int i = dns.length; --i >= 0; ) {
	    prins.add(new X500Principal(dns[i]));
	}
	return new Subject(true, prins,
			   Collections.EMPTY_SET, Collections.EMPTY_SET);
    }

    /**
     * Create an instance with the specified Subject and permission.
     */
    public CheckClientPermission(Subject s, Permission perm) {
	this.s = s;
	this.perm = perm;
    }

    /** Return the subject */
    public Subject getClientSubject() {
	return s;
    }

    /** Check the permission. */
    public void run() {
	BasicInvocationDispatcher.checkClientPermission(perm);
    }

    /** Check that the subject has the permission. */
    public static void pass(Subject s, Permission perm) {
	CheckClientPermission ccp = new CheckClientPermission(s, perm);
	ArrayList context = new ArrayList(1);
	context.add(ccp);
	ServerContext.doWithServerContext(ccp, context);
    }

    /** Check that the subject doesn't have the permission. */
    public static void fail(Subject s, Permission perm) {
	CheckClientPermission ccp = new CheckClientPermission(s, perm);
	try {
	    ArrayList context = new ArrayList(1);
	    context.add(ccp);
	    ServerContext.doWithServerContext(ccp, context);
	    throw new RuntimeException("check succeeded");
	} catch (AccessControlException e) {
            /* if (e.getPermission() != null) {  - This appears to make
             * assumptions about the permission that caused the 
             * AccessControlException to be thrown.
             * However in our case the permission was the excpecte permission
             * which meant the test failed when it should pass.  It appears
             * the behaviour of the AccessController has changed in the current JVM.
             * Instead I have changed this to fail if we get a different permission
             * than that expected.
             */ 
            Permission expected = e.getPermission();
	    if (expected != null && !perm.equals(expected)) { // In case a different permission caused failure.
		throw e;
	    }
	}
    }

    public static void main(String[] args) {
	System.setSecurityManager(null);
	try {
	    BasicInvocationDispatcher.checkClientPermission(
					       new SecurityPermission("foo"));
	    throw new RuntimeException("check succeeded");
	} catch (RuntimeException e) {
	    if (e.getClass() != IllegalStateException.class ||
		!e.getMessage().equals("server not active"))
	    {
		throw e;
	    }
	}
	pass(null, new SecurityPermission("baz"));
	pass(null, new TestPermission("baz"));
	pass(bob, new SecurityPermission("baz"));
	pass(bob, new TestPermission("baz"));
	pass(joe, new SecurityPermission("baz"));
	pass(joe, new TestPermission("baz"));
	System.setSecurityManager(new SecurityManager());
	fail(null, new SecurityPermission("foo"));
	fail(null, new SecurityPermission("bar"));
	fail(null, new TestPermission("foo"));
	fail(null, new TestPermission("bar"));
	pass(bob, new SecurityPermission("foo"));
	fail(bob, new SecurityPermission("bar"));
	fail(bob, new TestPermission("foo"));
	fail(bob, new TestPermission("bar"));
	fail(joe, new SecurityPermission("foo"));
	fail(joe, new SecurityPermission("bar"));
	pass(joe, new TestPermission("foo"));
	fail(joe, new TestPermission("bar"));
	fail(jim, new SecurityPermission("foo"));
	fail(jim, new SecurityPermission("bar"));
	fail(jim, new TestPermission("foo"));
	fail(jim, new TestPermission("bar"));
	pass(joebob, new SecurityPermission("foo"));
	pass(joebob, new SecurityPermission("bar"));
	pass(joebob, new TestPermission("foo"));
	fail(joebob, new TestPermission("bar"));
	fail(jimjoe, new SecurityPermission("foo"));
	fail(jimjoe, new SecurityPermission("bar"));
	pass(jimjoe, new TestPermission("foo"));
	fail(jimjoe, new TestPermission("bar"));
	pass(jimbob, new SecurityPermission("foo"));
	fail(jimbob, new SecurityPermission("bar"));
	fail(jimbob, new TestPermission("foo"));
	fail(jimbob, new TestPermission("bar"));
    }
}
