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
 * @summary Test GrantPermission readObject method.
 * @run main/othervm Test
 */

import java.io.*;
import java.security.*;
import java.util.*;
import net.jini.security.GrantPermission;

public class Test {

    /**
     * Serialization equivalent of GrantPermission.
     */
    static class GP extends Permission { 
	GP(String name) { 
	    super(name); 
	}

	public String getActions() {
	    return "";
	}

	public int hashCode() {
	    return 0;
	}

	public boolean equals(Object o) {
	    return false;
	}

	public boolean implies(Permission p) {
	    return false;
	}
    }

    /**
     * Serialization equivalent of GrantPermission$GrantPermissionCollection.
     */
    static class GPC extends PermissionCollection {

	final List perms;

	GPC(List perms) {
	    this.perms = perms;
	}

	public void add(Permission p) {
	}

	public boolean implies(Permission p) {
	    return false;
	}

	public Enumeration elements() {
	    return null;
	}
    }

    /**
     * Stream to serialize serialization-equivalent classes as actual classes.
     */
    static class Output extends ObjectOutputStream {

	static final Class gpcc;
	static {
	    Permission p = new GrantPermission(new Permission[0]);
	    gpcc = p.newPermissionCollection().getClass();
	}

	Output(OutputStream out) throws IOException {
	    super(out);
	}

	protected void writeClassDescriptor(ObjectStreamClass desc)
	    throws IOException
	{
	    Class c = desc.forClass();
	    if (c.equals(GP.class)) {
		desc = ObjectStreamClass.lookup(GrantPermission.class);
	    } else if (c.equals(GPC.class)) {
		desc = ObjectStreamClass.lookup(gpcc);
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
	check(new GP(null), null);
	check(new GP(""), "expected permission type");
	check(new GP("foo \"bar\" \"baz\""), "expected ',' or ';'");
	check(new GPC(null), "list cannot be null");
	check(new GPC(Arrays.asList(
			  new Permission[]{ new RuntimePermission("foo") })),
	      "element must be a GrantPermission");
	check(new GPC(Arrays.asList(new Object[1])),
	      "element must be a GrantPermission");
    }
}
