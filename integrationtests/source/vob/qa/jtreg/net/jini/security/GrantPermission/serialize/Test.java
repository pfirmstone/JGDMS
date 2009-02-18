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
 * @summary Test GrantPermission serialization.
 */

import java.io.*;
import java.security.*;
import net.jini.security.GrantPermission;

public class Test {
    public static void main(String[] args) throws Exception {
	testSerialize(new Permission[]{
	    new Permission0(),
	    new Permission1("1n"),
	    new Permission2("2n", "2a")
	});
	testSerialize(new Permission[]{
	    new Permission1(null),
	    new Permission2(null, null)
	});
	testSerialize(new Permission[]{
	    new Permission2("2n", null)
	});
	testSerialize(new Permission[]{
	    new Permission2("a \"quoted\" string", 
		"\b\f\n\r\t \"\b\f\n\r\t\"")
	});
	testSerialize(new Permission[]{
	    new GrantPermission(new Permission1("1n")),
	    new Permission0a()
	});
	testSerializeUnresolved(new Permission[]{
	    new UnresolvedPermission("foo", "bar", "", null),
	    new Permission0()
	});
    }
    
    static void testSerialize(Permission[] grants) throws Exception {
	GrantPermission gp = new GrantPermission(grants);
	PermissionCollection gpc = gp.newPermissionCollection();
	gpc.add(gp);

	ByteArrayOutputStream bout = new ByteArrayOutputStream();
	ObjectOutputStream oout = new ObjectOutputStream(bout);
	oout.writeObject(gp);
	oout.writeObject(gpc);
	oout.close();

	ObjectInputStream oin = new ObjectInputStream(
	    new ByteArrayInputStream(bout.toByteArray()));
	gp = (GrantPermission) oin.readObject();
	gpc = (PermissionCollection) oin.readObject();
	for (int i = 0; i < grants.length; i++) {
	    Permission p = new GrantPermission(grants[i]);
	    if (!(gp.implies(p) && gpc.implies(p))) {
		System.out.println(gp.getName());
		throw new Error();
	    }
	}
    }

    static void testSerializeUnresolved(Permission[] grants) throws Exception {
	GrantPermission gp = new GrantPermission(grants);
	ObjectOutputStream oout = 
	    new ObjectOutputStream(new ByteArrayOutputStream());
	try {
	    oout.writeObject(gp);
	    throw new Error();
	} catch (NotSerializableException e) {
	}
    }
}
