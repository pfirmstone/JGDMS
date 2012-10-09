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
 * @bug 4302502
 * 
 * @summary test basic operations of AuthenticationPermission
 * 
 * @run main/othervm Operations
 */
import java.io.*;
import java.security.Permission;
import java.security.PermissionCollection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import net.jini.security.AuthenticationPermission;

public class Operations {

    /**
     * Throw an exception if b is false.
     */
    public static void v(boolean b) {
	if (!b) {
	    throw new RuntimeException(
				 "test failed; see stack trace for details");
	}
    }

    /**
     * Return an instance with the given target and connect action.
     */
    public static AuthenticationPermission cp(String name) {
	return new AuthenticationPermission(name, "connect");
    }

    /**
     * Return an instance with the given target and delegate action.
     */
    public static AuthenticationPermission dp(String name) {
	return new AuthenticationPermission(name, "delegate");
    }

    /**
     * Return an instance with the given target and accept action.
     */
    public static AuthenticationPermission ap(String name) {
	return new AuthenticationPermission(name, "accept");
    }

    /**
     * Return an instance with the given target and connect+accept actions.
     */
    public static AuthenticationPermission cap(String name) {
	return new AuthenticationPermission(name, "connect,accept");
    }

    /**
     * Return an instance with the given target and delegate+accept actions.
     */
    public static AuthenticationPermission dap(String name) {
	return new AuthenticationPermission(name, "delegate,accept");
    }

    /**
     * Return an instance with the given target and listen action.
     */
    public static AuthenticationPermission lp(String name) {
	return new AuthenticationPermission(name, "listen");
    }

    /**
     * Return an instance with the given target and connect+listen actions.
     */
    public static AuthenticationPermission clp(String name) {
	return new AuthenticationPermission(name, "connect,listen");
    }

    /**
     * Return an instance with the given target and delegate+listen actions.
     */
    public static AuthenticationPermission dlp(String name) {
	return new AuthenticationPermission(name, "delegate,listen");
    }

    /**
     * Return a PermissionCollection containing the permission.
     */
    public static PermissionCollection pc(Permission p) {
	PermissionCollection c = p.newPermissionCollection();
	c.add(p);
	return c;
    }

    /**
     * Return a PermissionCollection containing the permissions.
     */
    public static PermissionCollection pc(Permission p1, Permission p2) {
	PermissionCollection c = p1.newPermissionCollection();
	c.add(p1);
	c.add(p2);
	return c;
    }

    /**
     * Check that the two permissions are functionally equivalent.
     */
    public static void equals(Permission p1, Permission p2) {
	v(p1.equals(p2));
	v(p2.equals(p1));
	v(p1.hashCode() == p2.hashCode());
	v(p1.implies(p2));
	v(p2.implies(p1));
	v(pc(p1).implies(p2));
	v(pc(p2).implies(p1));
    }

    /**
     * Check that the two targets are functionally equivalent.
     */
    public static void equals(String name1, String name2) {
	equals(cp(name1), cp(name2));
	equals(dp(name1), dp(name2));
	equals(ap(name1), ap(name2));
	equals(lp(name1), lp(name2));
	equals(cap(name1), cap(name2));
	equals(dap(name1), dap(name2));
	equals(clp(name1), clp(name2));
	equals(dlp(name1), dlp(name2));
	diff(cp(name1), ap(name2));
	diff(dp(name1), ap(name2));
	diff(cp(name1), lp(name2));
	diff(dp(name1), lp(name2));
	implies(dp(name1), cp(name2));
	implies(ap(name1), lp(name2));
	implies(cap(name1), cp(name2));
	implies(dap(name1), cp(name2));
	implies(dap(name1), clp(name2));
	implies(dap(name1), dp(name2));
	implies(dap(name1), dlp(name2));
	diff(ap(name1), clp(name2));
	diff(ap(name1), dlp(name2));
	diff(dp(name1), cap(name2));
	diff(dp(name1), clp(name2));
	implies(cap(name1), ap(name2));
	implies(cap(name1), lp(name2));
	implies(cap(name1), clp(name2));
	diff(cap(name1), dlp(name2));
	implies(dap(name1), ap(name2));
	implies(dap(name1), lp(name2));
	implies(dap(name1), cap(name2));
	implies(clp(name1), cp(name2));
	implies(clp(name1), lp(name2));
	implies(dlp(name1), cp(name2));
	implies(dlp(name1), dp(name2));
	implies(dlp(name1), lp(name2));
	implies(dlp(name1), clp(name2));
    }

    /**
     * Check that the two permissions are not equal and that p1 implies p2.
     */
    public static void implies(Permission p1, Permission p2) {
	v(p1.implies(p2));
	v(!p2.implies(p1));
	v(!p1.equals(p2));
	v(!p2.equals(p1));
	v(pc(p1).implies(p2));
	v(!pc(p2).implies(p1));
    }

    /**
     * Check that the two targets are not equal and that name1 implies name2.
     */
    public static void implies(String name1, String name2) {
	implies(cp(name1), cp(name2));
	implies(dp(name1), dp(name2));
	implies(ap(name1), ap(name2));
	implies(ap(name1), lp(name2));
	implies(lp(name1), lp(name2));
	implies(cap(name1), cap(name2));
	implies(cap(name1), lp(name2));
	implies(cap(name1), clp(name2));
	implies(dap(name1), dap(name2));
	implies(dap(name1), lp(name2));
	implies(dap(name1), dlp(name2));
	diff(cp(name1), ap(name2));
	diff(cp(name1), lp(name2));
	diff(dp(name1), ap(name2));
	diff(dp(name1), lp(name2));
	implies(dp(name1), cp(name2));
	implies(cap(name1), cp(name2));
	implies(clp(name1), cp(name2));
	implies(clp(name1), lp(name2));
	implies(dap(name1), cp(name2));
	implies(dlp(name1), cp(name2));
	implies(dap(name1), dp(name2));
	implies(dlp(name1), dp(name2));
	implies(dlp(name1), lp(name2));
	diff(ap(name1), clp(name2));
	diff(ap(name1), dlp(name2));
	diff(dp(name1), cap(name2));
	diff(dp(name1), clp(name2));
	implies(cap(name1), ap(name2));
	implies(dap(name1), ap(name2));
	implies(dap(name1), cap(name2));
	implies(dap(name1), clp(name2));
	implies(clp(name1), clp(name2));
	implies(dlp(name1), clp(name2));
	implies(dlp(name1), dlp(name2));
	diff(cap(name1), dlp(name2));
    }

    /**
     * Check that the two targets are not equal and that name1 implies name2,
     * but are equal with respect to the listen action.
     */
    public static void limplies(String name1, String name2) {
	implies(cp(name1), cp(name2));
	implies(dp(name1), dp(name2));
	implies(ap(name1), ap(name2));
	implies(ap(name1), lp(name2));
	equals(lp(name1), lp(name2));
	implies(cap(name1), cap(name2));
	implies(cap(name1), lp(name2));
	implies(cap(name1), clp(name2));
	implies(dap(name1), dap(name2));
	implies(dap(name1), lp(name2));
	implies(dap(name1), dlp(name2));
	diff(cp(name1), ap(name2));
	diff(cp(name1), lp(name2));
	diff(dp(name1), ap(name2));
	diff(dp(name1), lp(name2));
	implies(dp(name1), cp(name2));
	implies(cap(name1), cp(name2));
	implies(clp(name1), cp(name2));
	implies(clp(name1), lp(name2));
	implies(dap(name1), cp(name2));
	implies(dlp(name1), cp(name2));
	implies(dap(name1), dp(name2));
	implies(dlp(name1), dp(name2));
	implies(dlp(name1), lp(name2));
	diff(ap(name1), clp(name2));
	diff(ap(name1), dlp(name2));
	diff(dp(name1), cap(name2));
	diff(dp(name1), clp(name2));
	implies(cap(name1), ap(name2));
	implies(dap(name1), ap(name2));
	implies(dap(name1), cap(name2));
	implies(dap(name1), clp(name2));
	implies(clp(name1), clp(name2));
	implies(dlp(name1), clp(name2));
	implies(dlp(name1), dlp(name2));
	diff(cap(name1), dlp(name2));
    }

    /**
     * Check that the two permissions are not equal and neither implies the
     * other.
     */
    public static void diff(Permission p1, Permission p2) {
	v(!p1.equals(p2));
	v(!p2.equals(p1));
	v(!p1.implies(p2));
	v(!p2.implies(p1));
	v(!pc(p1).implies(p2));
	v(!pc(p2).implies(p1));
    }

    /**
     * Check that the two targets are not equal and neither implies the other.
     */
    public static void diff(String name1, String name2) {
	diff(cp(name1), cp(name2));
	diff(dp(name1), dp(name2));
	diff(ap(name1), ap(name2));
	diff(cap(name1), cap(name2));
	diff(dap(name1), dap(name2));
	diff(cp(name1), ap(name2));
	diff(dp(name1), ap(name2));
	diff(dp(name1), cp(name2));
	diff(ap(name1), lp(name2));
	diff(cp(name1), lp(name2));
	diff(dp(name1), lp(name2));
	diff(lp(name1), lp(name2));
	diff(cap(name1), cp(name2));
	diff(dap(name1), cp(name2));
	diff(dap(name1), dp(name2));
	diff(dp(name1), cap(name2));
	diff(cap(name1), ap(name2));
	diff(dap(name1), ap(name2));
	diff(dap(name1), cap(name2));
	diff(cap(name1), lp(name2));
	diff(dap(name1), lp(name2));
	diff(clp(name1), ap(name2));
	diff(clp(name1), cp(name2));
	diff(clp(name1), dp(name2));
	diff(clp(name1), lp(name2));
	diff(clp(name1), cap(name2));
	diff(clp(name1), dap(name2));
	diff(dlp(name1), ap(name2));
	diff(dlp(name1), cp(name2));
	diff(dlp(name1), dp(name2));
	diff(dlp(name1), lp(name2));
	diff(dlp(name1), cap(name2));
	diff(dlp(name1), clp(name2));
	diff(dlp(name1), cap(name2));
	diff(dlp(name1), dap(name2));
	diff(dlp(name1), dlp(name2));
    }

    /**
     * Check that the two targets are not equal and neither implies the other
     * except they are equal with respect to the listen action.
     */
    public static void ldiff(String name1, String name2) {
	diff(cp(name1), cp(name2));
	diff(dp(name1), dp(name2));
	diff(ap(name1), ap(name2));
	diff(cap(name1), cap(name2));
	diff(dap(name1), dap(name2));
	diff(cp(name1), ap(name2));
	diff(dp(name1), ap(name2));
	diff(dp(name1), cp(name2));
	implies(ap(name1), lp(name2));
	diff(cp(name1), lp(name2));
	diff(dp(name1), lp(name2));
	equals(lp(name1), lp(name2));
	diff(cap(name1), cp(name2));
	diff(dap(name1), cp(name2));
	diff(dap(name1), dp(name2));
	diff(dp(name1), cap(name2));
	diff(cap(name1), ap(name2));
	diff(dap(name1), ap(name2));
	diff(dap(name1), cap(name2));
	implies(cap(name1), lp(name2));
	implies(dap(name1), lp(name2));
	diff(clp(name1), ap(name2));
	diff(clp(name1), cp(name2));
	diff(clp(name1), dp(name2));
	implies(clp(name1), lp(name2));
	diff(clp(name1), cap(name2));
	diff(clp(name1), dap(name2));
	diff(dlp(name1), ap(name2));
	diff(dlp(name1), cp(name2));
	diff(dlp(name1), dp(name2));
	implies(dlp(name1), lp(name2));
	diff(dlp(name1), cap(name2));
	diff(dlp(name1), clp(name2));
	diff(dlp(name1), cap(name2));
	diff(dlp(name1), dap(name2));
	diff(dlp(name1), dlp(name2));
    }

    /**
     * Check that the string is not a valid target.
     */
    public static void bad(String name, String why) throws Exception {
	check(new AP(name, "connect"), why);
	try {
	    new AuthenticationPermission(name, "connect");
	} catch (IllegalArgumentException e) {
	    if (why.equals(e.getMessage())) {
		return;
	    }
	    throw e;
	}
	throw new RuntimeException();
    }

    /**
     * Return an instance with the given action.
     */
    public static AuthenticationPermission act(String action) {
	return new AuthenticationPermission("c \"n\"", action);
    }

    /**
     * Check that the two actions are equivalent.
     */
    public static void aequals(String action1, String action2) {
	v(act(action1).equals(act(action2)));
    }

    /**
     * Check that action3 is implied by the combination of action1 and action2.
     */
    public static void aimplies(String action1, String action2, String action3)
    {
	v(pc(act(action1), act(action2)).implies(act(action3)));
    }

    /**
     * Check that the string is not a valid set of actions.
     */
    public static void abad(String action) throws Exception {
	check(new AP("c \"n\"", action), "invalid actions");
	try {
	    act(action);
	} catch (IllegalArgumentException e) {
	    if ("invalid actions".equals(e.getMessage())) {
		return;
	    }
	    throw e;
	}
	throw new RuntimeException();
    }

    /**
     * Serialization-equivalent of AuthenticationPermission.
     */
    public static class AP extends Permission {
	private String actions;

	public AP(String name, String actions) {
	    super(name);
	    this.actions = actions;
	}

	public boolean implies(Permission permission) {
	    return false;
	}

	public boolean equals(Object obj) {
	    return false;
	}

	public int hashCode() {
	    return 0;
	}

	public String getActions() {
	    return null;
	}
    }

    /**
     * Serialization-equivalent of AuthenticationPermissionCollection.
     */
    public static class APC extends PermissionCollection {
	private List permissions;

	public APC(List permissions) {
	    this.permissions = permissions;
	}

	public void add(Permission perm) {
	}

	public boolean implies(Permission perm) {
	    return false;
	}

	public Enumeration elements() {
	    return null;
	}
    }

    /**
     * Stream to serialize AP as AuthenticationPermission and
     * APC as AuthenticationPermissionCollection.
     */
    public static class Output extends ObjectOutputStream {

	public Output(OutputStream out) throws IOException {
	    super(out);
	}

	protected void writeClassDescriptor(ObjectStreamClass desc)
	    throws IOException
	{
	    if (desc.forClass() == AP.class) {
		desc = ObjectStreamClass.lookup(
					      AuthenticationPermission.class);
	    } else if (desc.forClass() == APC.class) {
		try {
		    desc = ObjectStreamClass.lookup(
			Class.forName("net.jini.security.AuthenticationPermission$AuthenticationPermissionCollection"));
		} catch (ClassNotFoundException e) {
		    e.printStackTrace();
		    throw new IOException(e.getMessage());
		}
	    }
	    super.writeClassDescriptor(desc);
	}
    }

    /**
     * Check that object deserializes with InvalidObjectException.
     */
    public static void check(Object obj, String why) throws Exception {
	ByteArrayOutputStream bos = new ByteArrayOutputStream();
	ObjectOutputStream out = new Output(bos);
	out.writeObject(obj);
	out.close();
	ObjectInputStream in =
	    new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()));
	try {
	    in.readObject();
	    throw new RuntimeException("object deserialized");
	} catch (InvalidObjectException e) {
	    if (why == null ?
		e.getMessage() != null : !why.equals(e.getMessage()))
	    {
		throw e;
	    }
	}
    }

    public static void main(String[] args) throws Exception {
	// basic equality
	equals("c \"n\"", "c \"n\"");
	equals("c \"*\"", "c \"*\"");
	equals("* \"*\"", "* \"*\"");
	// redundancy
	equals("c \"*\"", "c \"*\" c \"n\"");
	equals("c \"*\"", "c \"n1\" c \"*\" c \"n2\"");
	// ordering
	equals("c1 \"n1\" c2 \"n2\"", "c2 \"n2\" c1 \"n1\"");
	// spacing
	equals("c \"foo bar\"", "c   \"foo bar\"");

	// basic equality
	equals("c \"n\" peer c \"n\"", "c \"n\" peer c \"n\"");
	equals("c \"*\" peer c \"n\"", "c \"*\" peer c \"n\"");
	equals("* \"*\" peer c \"n\"", "* \"*\" peer c \"n\"");
	// redundancy
	equals("c \"*\" peer c \"n\"", "c \"*\" c \"n\" peer c \"n\"");
	equals("c \"*\" peer c \"n\"",
	       "c \"n1\" c \"*\" c \"n2\" peer c \"n\"");
	// ordering
	equals("c1 \"n1\" c2 \"n2\" peer c \"n\"",
	       "c2 \"n2\" c1 \"n1\" peer c \"n\"");
	// spacing
	equals("c \"foo bar\" peer c \"n\"", "c   \"foo bar\" peer c \"n\"");
	// redundancy
	equals("c \"*\" peer c \"n\"", "c \"*\" peer c \"n\" c \"n\"");
	// ordering
	equals("c \"n\" peer c1 \"n1\" c2 \"n2\"",
	       "c \"n\" peer c2 \"n2\" c1 \"n1\"");
	// spacing
	equals("c \"n\" peer c \"foo bar\"", "c \"n\" peer c   \"foo bar\"");

	implies("c1 \"n1\" c2 \"n2\"", "c1 \"n1\"");
	implies("c2 \"n2\" c1 \"n1\"", "c1 \"n1\"");
	implies("c \"n\" c1 \"*\"", "c \"n\"");
	implies("c \"*\"", "c \"n\"");
	implies("c \"*\"", "c \"n1\" c \"n2\"");
	implies("* \"*\"", "c \"n\"");
	implies("* \"*\"", "c1 \"n1\" c2 \"n2\"");
	implies("* \"*\"", "c \"*\"");
	implies("c1 \"n1\" * \"*\" c2 \"n2\"", "c2 \"n2\" c1 \"n1\"");
	implies("c1 \"*\" c2 \"*\"", "c2 \"n2\" c1 \"n1\"");
	implies("* \"*\"", "c2 \"n2\" c1 \"n1\"");

	implies("c1 \"n1\" c2 \"n2\" peer c \"n\"", "c1 \"n1\" peer c \"n\"");
	implies("c2 \"n2\" c1 \"n1\" peer c \"n\"", "c1 \"n1\" peer c \"n\"");
	implies("c \"n\" c1 \"*\" peer c \"n\"", "c \"n\" peer c \"n\"");
	implies("c \"*\" peer c \"n\"", "c \"n\" peer c \"n\"");
	implies("c \"*\" peer c \"n\"", "c \"n1\" c \"n2\" peer c \"n\"");
	implies("* \"*\" peer c \"n\"", "c \"n\" peer c \"n\"");
	implies("* \"*\" peer c \"n\"", "c1 \"n1\" c2 \"n2\" peer c \"n\"");
	implies("* \"*\" peer c \"n\"", "c \"*\" peer c \"n\"");
	implies("c1 \"n1\" * \"*\" c2 \"n2\" peer c \"n\"",
		"c2 \"n2\" c1 \"n1\" peer c \"n\"");
	implies("c1 \"*\" c2 \"*\" peer c \"n\"",
		"c2 \"n2\" c1 \"n1\" peer c \"n\"");
	implies("* \"*\" peer c \"n\"", "c2 \"n2\" c1 \"n1\" peer c \"n\"");
	limplies("c \"n\"", "c \"n\" peer c \"n\"");
	limplies("c \"n\"", "c \"n\" peer c1 \"n1\" c2 \"n2\"");
	limplies("c \"n\" peer c2 \"n2\"", "c \"n\" peer c1 \"n1\" c2 \"n2\"");
	implies("c1 \"n1\" c2 \"n2\" peer c1 \"n2\" c2 \"n2\"",
		"c2 \"n2\" peer c1 \"n1\" c2 \"n2\" c1 \"n2\"");

	diff("c \"n\"", "c \"n1\"");
	diff("c \"n\"", "C \"n\"");
	diff("c \"n\"", "c \"N\"");
	diff("c \"*\"", "c1 \"n\"");
	diff("c1 \"n1\" c2 \"n2\"", "c2 \"n1\" c1 \"n2\"");
	diff("c1 \"*\" c2 \"*\"", "c3 \"n2\" c1 \"n1\"");
	diff("c \"*\"", "c \"n\" c1 \"n1\"");
	diff("c \"foo bar\"", "c \"foo  bar\"");

	diff("c \"n\" peer c \"n\"", "c \"n1\" peer c \"n\"");
	diff("c \"n\" peer c \"n\"", "C \"n\" peer c \"n\"");
	diff("c \"n\" peer c \"n\"", "c \"N\" peer c \"n\"");
	diff("c \"*\" peer c \"n\"", "c1 \"n\" peer c \"n\"");
	diff("c1 \"n1\" c2 \"n2\" peer c \"n\"",
	     "c2 \"n1\" c1 \"n2\" peer c \"n\"");
	diff("c1 \"*\" c2 \"*\" peer c \"n\"",
	     "c3 \"n2\" c1 \"n1\" peer c \"n\"");
	diff("c \"*\" peer c \"n\"", "c \"n\" c1 \"n1\" peer c \"n\"");
	diff("c \"foo bar\" peer c \"n\"", "c \"foo  bar\" peer c \"n\"");

	ldiff("c \"n\" peer c \"n\"", "c \"n\" peer c \"n1\"");
	ldiff("c \"n\" peer c \"n\"", "c \"n\" peer C \"n\"");
	ldiff("c \"n\" peer c \"n\"", "c \"n\" peer c \"N\"");
	ldiff("c \"n\" peer c1 \"n1\" c2 \"n2\"",
	      "c \"n\" peer c2 \"n1\" c1 \"n2\"");
	ldiff("c \"n\" peer c \"foo bar\"", "c \"n\" peer c \"foo  bar\"");

	// target syntax
	bad("", "target name is missing elements");
	bad("   ", "target name is missing elements");
	bad("peer", "target name is missing elements");
	bad("c \"n\" peer", "target name is missing elements");
	bad("c", "missing name after class");
	bad("c \"n\" c", "missing name after class");
	bad("c \"n\" peer    ", "target name is missing elements");
	bad("c \"n\" peer c", "missing name after class");
	bad("c \"n\" peer c \"n\" c", "missing name after class");
	bad("* n", "name must be in quotes");
	bad("peer c \"n\"", "target name is missing elements");
	bad("c \"n\" peer * \"n\"", "peer class cannot be *");
	bad("c \"n\" peer c \"*\"", "peer name cannot be \"*\"");
	bad("c n", "name must be in quotes");
	bad("c \"n", "name must be in quotes");
	bad("c n\"", "name must be in quotes");
	bad("c \"n\" peer c n", "name must be in quotes");
	bad("c \"n\" peer c \"n", "name must be in quotes");
	bad("c \"n\" peer c n\"", "name must be in quotes");

	// action equality
	aequals("connect", "CoNnEcT");
	aequals("delegate", "DeLeGaTe");
	aequals("accept", "AcCePt");
	aequals("connect", "connect,connect");
	aequals("delegate", "connect,delegate");
	aequals("accept", "accept,accept");
	aequals("connect,accept", "accept,connect");
	aequals("connect,accept", "connect, accept");
	aequals("connect,accept", "accept , connect");
	aequals("delegate,accept", "accept,delegate");
	aequals("delegate,accept", "accept,connect,delegate");
	aequals("accept", "accept,listen");
	aequals("connect,accept", "listen,connect,accept");
	aequals("delegate,accept", "accept,connect,delegate,listen");

	// multi-action implies
	aimplies("connect", "listen", "connect,listen");
	aimplies("delegate", "listen", "connect,listen");
	aimplies("delegate", "listen", "delegate,listen");
	aimplies("connect", "accept", "connect,accept");
	aimplies("connect", "accept", "connect,listen");
	aimplies("delegate", "accept", "connect,accept");
	aimplies("delegate", "accept", "delegate,accept");
	aimplies("delegate", "accept", "connect,listen");
	aimplies("delegate", "accept", "delegate,listen");

	// action syntax
	abad("");
	abad("foo");
	abad("connect,foo");
	abad("connect,");
	abad(",");
	abad(",connect");
	abad("accept,,connect");
	abad("foo,connect");
	abad("connect accept");

	check(new APC(null), "list cannot be null");
	check(new APC(Collections.singletonList(null)),
	      "element must be an AuthenticationPermission");
	check(new APC(Collections.singletonList("foo")),
	      "element must be an AuthenticationPermission");
    }
}
