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
 * @summary test basic operations of AccessPermission
 * 
 * @run main/othervm Operations
 */
import java.io.*;
import java.security.Permission;
import net.jini.security.AccessPermission;

public class Operations {

    public static class FooPermission extends AccessPermission {
	public FooPermission(String name) {
	    super(name);
	}
    }

    public static class BarPermission extends AccessPermission {
	public BarPermission(String name) {
	    super(name);
	}
    }

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
     * Return an AccessPermission instance with the given target.
     */
    public static AccessPermission ap(String name) {
	return new AccessPermission(name);
    }

    /**
     * Return a FooPermission instance with the given target.
     */
    public static FooPermission fp(String name) {
	return new FooPermission(name);
    }

    /**
     * Return a BarPermission instance with the given target.
     */
    public static BarPermission bp(String name) {
	return new BarPermission(name);
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
    }

    /**
     * Check that the two targets are functionally equivalent.
     */
    public static void equals(String name1, String name2) {
	equals(ap(name1), ap(name2));
	equals(fp(name1), fp(name2));
	equals(bp(name1), bp(name2));
	diff(ap(name1), fp(name2));
	diff(fp(name1), bp(name2));
    }

    /**
     * Check that the two permissions are not equal and that p1 implies p2.
     */
    public static void implies(Permission p1, Permission p2) {
	v(p1.implies(p2));
	v(!p2.implies(p1));
	v(!p1.equals(p2));
	v(!p2.equals(p1));
    }

    /**
     * Check that the two targets are not equal and that name1 implies name2.
     */
    public static void implies(String name1, String name2) {
	implies(ap(name1), ap(name2));
	implies(fp(name1), fp(name2));
	implies(bp(name1), bp(name2));
	diff(ap(name1), fp(name2));
	diff(fp(name1), bp(name2));
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
    }

    /**
     * Check that the two targets are not equal and neither implies the other.
     */
    public static void diff(String name1, String name2) {
	diff(ap(name1), ap(name2));
	diff(ap(name1), fp(name2));
	diff(fp(name1), bp(name2));
    }

    /**
     * Check that the string is not a valid target.
     */
    public static void nullp() throws Exception {
	String why = "name cannot be null";
	check(new AP(null), why);
	try {
	    new AccessPermission(null);
	} catch (NullPointerException e1) {
	    if (!why.equals(e1.getMessage())) {
		throw e1;
	    }
	    try {
		new FooPermission(null);
	    } catch (NullPointerException e2) {
		if (!why.equals(e2.getMessage())) {
		    throw e2;
		}
		try {
		    new BarPermission(null);
		} catch (NullPointerException e3) {
		    if (why.equals(e3.getMessage())) {
			return;
		    }
		    throw e3;
		}
		throw new RuntimeException();
	    }
	    throw new RuntimeException();
	}
	throw new RuntimeException();
    }

    /**
     * Check that the string is not a valid target.
     */
    public static void bad(String name, String why) throws Exception {
	check(new AP(name), why);
	try {
	    new AccessPermission(name);
	} catch (IllegalArgumentException e1) {
	    if (!why.equals(e1.getMessage())) {
		throw e1;
	    }
	    try {
		new FooPermission(name);
	    } catch (IllegalArgumentException e2) {
		if (!why.equals(e2.getMessage())) {
		    throw e1;
		}
		try {
		    new BarPermission(name);
		} catch (IllegalArgumentException e3) {
		    if (why.equals(e3.getMessage())) {
			return;
		    }
		    throw e3;
		}
		throw new RuntimeException();
	    }
	    throw new RuntimeException();
	}
	throw new RuntimeException();
    }

    /**
     * Serialization-equivalent of AccessPermission.
     */
    public static class AP extends Permission {

	public AP(String name) {
	    super(name);
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
     * Stream to serialize AP as AccessPermission.
     */
    public static class Output extends ObjectOutputStream {

	public Output(OutputStream out) throws IOException {
	    super(out);
	}

	protected void writeClassDescriptor(ObjectStreamClass desc)
	    throws IOException
	{
	    if (desc.forClass() == AP.class) {
		desc = ObjectStreamClass.lookup(AccessPermission.class);
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
	equals("*", "*");
	equals("foobar", "foobar");
	equals("*bar", "*bar");
	equals("foo*", "foo*");
	equals("iface.*", "iface.*");
	equals("com.sun.iface.*", "com.sun.iface.*");
	equals("iface.foobar", "iface.foobar");
	equals("com.sun.iface.foobar", "com.sun.iface.foobar");
	equals("iface.*bar", "iface.*bar");
	equals("com.sun.iface.*bar", "com.sun.iface.*bar");
	equals("iface.foo*", "iface.foo*");
	equals("com.sun.iface.foo*", "com.sun.iface.foo*");

	implies("*", "foobar");
	implies("*", "*bar");
	implies("*", "foo*");
	implies("*", "iface.*");
	implies("*", "com.sun.iface.*");
	implies("*", "iface.foobar");
	implies("*", "com.sun.iface.foobar");
	implies("*", "iface.*bar");
	implies("*", "com.sun.iface.*bar");
	implies("*", "iface.foo*");
	implies("*", "com.sun.iface.foo*");

	implies("*bar", "foobar");
	implies("*bar", "bar");
	implies("*bar", "*foobar");
	implies("*r", "*bar");
	implies("*r", "r");
	implies("*bar", "iface.foobar");
	implies("*bar", "com.sun.iface.foobar");
	implies("*bar", "iface.bar");
	implies("*bar", "com.sun.iface.bar");
	implies("*bar", "iface.*bar");
	implies("*bar", "com.sun.iface.*bar");
	implies("*bar", "iface.*foobar");
	implies("*bar", "com.sun.iface.*foobar");
	implies("*r", "iface.*bar");
	implies("*r", "iface.*r");
	implies("*r", "com.sun.iface.*bar");
	implies("*r", "com.sun.iface.*r");

	implies("foo*", "foobar");
	implies("foo*", "foo");
	implies("foo*", "foobar*");
	implies("f*", "f");
	implies("f*", "foo*");
	implies("foo*", "iface.foobar");
	implies("foo*", "com.sun.iface.foobar");
	implies("foo*", "iface.foo");
	implies("foo*", "com.sun.iface.foo");
	implies("foo*", "iface.foo*");
	implies("foo*", "com.sun.iface.foo*");
	implies("foo*", "iface.foobar*");
	implies("foo*", "com.sun.iface.foobar*");
	implies("f*", "iface.foo*");
	implies("f*", "com.sun.iface.foo*");
	implies("f*", "iface.f*");
	implies("f*", "com.sun.iface.f*");

	implies("iface.*", "iface.foobar");
	implies("com.sun.iface.*", "com.sun.iface.foobar");
	implies("iface.*", "iface.*bar");
	implies("com.sun.iface.*", "com.sun.iface.*bar");
	implies("iface.*", "iface.foo*");
	implies("com.sun.iface.*", "com.sun.iface.foo*");

	implies("iface.*bar", "iface.foobar");
	implies("com.sun.iface.*bar", "com.sun.iface.foobar");
	implies("iface.*bar", "iface.bar");
	implies("com.sun.iface.*bar", "com.sun.iface.bar");
	implies("iface.*bar", "iface.*foobar");
	implies("com.sun.iface.*bar", "com.sun.iface.*foobar");
	implies("iface.*r", "iface.*bar");
	implies("com.sun.iface.*r", "com.sun.iface.*bar");
	implies("iface.*r", "iface.r");
	implies("com.sun.iface.*r", "com.sun.iface.r");

	implies("iface.foo*", "iface.foobar");
	implies("com.sun.iface.foo*", "com.sun.iface.foobar");
	implies("iface.foo*", "iface.foo");
	implies("com.sun.iface.foo*", "com.sun.iface.foo");
	implies("iface.foo*", "iface.foobar*");
	implies("com.sun.iface.foo*", "com.sun.iface.foobar*");
	implies("iface.f*", "iface.f");
	implies("com.sun.iface.f*", "com.sun.iface.f");
	implies("iface.f*", "iface.foo*");
	implies("com.sun.iface.f*", "com.sun.iface.foo*");

	diff("foo", "bar");
	diff("com.sun.Foo$Bar.foo", "com.sun.Foo$Bar.bar");

	diff("*bar", "baz");
	diff("*bar", "*baz");
	diff("*bar", "foo*");
	diff("*bar", "iface.*");
	diff("*bar", "com.sun.iface.*");
	diff("*bar", "iface.baz");
	diff("*bar", "com.sun.iface.baz");
	diff("*bar", "iface.*baz");
	diff("*bar", "com.sun.iface.*baz");
	diff("*bar", "iface.foo*");
	diff("*bar", "com.sun.iface.foo*");

	diff("foo*", "baz");
	diff("foo*", "goo*");
	diff("foo*", "foo.*");
	diff("foo*", "iface.*");
	diff("foo*", "com.sun.iface.*");
	diff("foo*", "iface.baz");
	diff("foo*", "com.sun.iface.baz");
	diff("foo*", "iface.goo*");
	diff("foo*", "com.sun.iface.goo*");
	diff("foo*", "iface.*bar");
	diff("foo*", "com.sun.iface.*bar");

	diff("iface.*", "foobar");
	diff("com.sun.iface.*", "foobar");
	diff("iface.*", "xface.*");
	diff("com.sun.iface.*", "iface.*");
	diff("iface.*", "com.sun.iface.*");
	diff("com.sun.iface.*", "com.sun.xface.*");
	diff("iface.*", "xface.foobar");
	diff("com.sun.iface.*", "iface.foobar");
	diff("iface.*", "com.sun.iface.foobar");
	diff("com.sun.iface.*", "com.sun.xface.foobar");
	diff("iface.*", "xface.*bar");
	diff("com.sun.iface.*", "iface.*bar");
	diff("iface.*", "com.sun.iface.*bar");
	diff("com.sun.iface.*", "com.sun.xface.*bar");
	diff("iface.*", "xface.foo*");
	diff("com.sun.iface.*", "iface.foo*");
	diff("iface.*", "com.sun.iface.foo*");
	diff("com.sun.iface.*", "com.sun.xface.foo*");

	diff("iface.foobar", "xface.foobar");
	diff("com.sun.iface.foobar", "iface.foobar");
	diff("iface.foobar", "com.sun.iface.foobar");
	diff("com.sun.iface.foobar", "com.sun.xface.foobar");
	diff("iface.foobar", "iface.foo");
	diff("com.sun.iface.foobar", "com.sun.iface.foo");

	diff("iface.*bar", "xface.*bar");
	diff("com.sun.iface.*bar", "iface.*bar");
	diff("iface.*bar", "com.sun.iface.*bar");
	diff("com.sun.iface.*bar", "com.sun.xface.*bar");
	diff("iface.*bar", "iface.foo*");
	diff("com.sun.iface.*bar", "iface.foo*");

	diff("iface.foo*", "xface.foo*");
	diff("com.sun.iface.foo*", "iface.foo*");

	nullp();

	bad("", "name cannot be empty");
	bad(".", "invalid interface name");
	bad("*.", "invalid interface name");
	bad(".*", "invalid interface name");
	bad("*.*", "invalid interface name");
	bad("**", "invalid method name");
	bad("123", "invalid method name");
	bad("*.foobar", "invalid interface name");
	bad("iface.1*", "invalid method name");
	bad("123.*", "invalid interface name");
	bad("iface.1bar", "invalid method name");
	bad("foo.*.bar", "invalid interface name");
	bad("foo..bar", "invalid interface name");
	bad("foo.bar.", "invalid method name");
	bad(".foo", "invalid interface name");
	bad("foo.", "invalid method name");
	bad("foo**", "invalid method name");
	bad("**bar", "invalid method name");
	bad("iface.foo**", "invalid method name");
	bad("iface.**bar", "invalid method name");
	bad("foo*bar", "invalid method name");
	bad("foo*.bar", "invalid interface name");
	bad("*foo.bar", "invalid interface name");
    }
}
