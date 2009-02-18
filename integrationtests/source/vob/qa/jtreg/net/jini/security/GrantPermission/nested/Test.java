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
 * @summary Verify that implication properly handles nested GrantPermissions.
 */

import java.io.*;
import java.security.*;
import net.jini.security.GrantPermission;

public class Test {
    public static void main(String[] args) throws Exception {
	final int MAX_NEST = 4;

	RuntimePermission rp = new RuntimePermission("foo");
	GrantPermission[] perms = new GrantPermission[MAX_NEST * 2];
	for (int i = 0; i < MAX_NEST; i++) {
	    GrantPermission gp = new GrantPermission(rp);
	    for (int j = 0; j < i; j++) {
		gp = new GrantPermission(gp);
	    }
	    perms[i * 2] = gp;
	    perms[i * 2 + 1] = (GrantPermission) serializedCopy(gp);
	}
	
	for (int i = 0; i < perms.length; i++) {
	    for (int j = 0; j < perms.length; j++) {
		PermissionCollection pc = perms[i].newPermissionCollection();
		pc.add(perms[i]);
		if (!(perms[i].implies(perms[j]) &&
		      perms[i].equals(perms[j]) &&
		      perms[i].hashCode() == perms[j].hashCode() &&
		      pc.implies(perms[j])))
		{
		    throw new Error();
		}
	    }
	}
    }
    
    static Object serializedCopy(Object obj) throws Exception {
	ByteArrayOutputStream bout = new ByteArrayOutputStream();
	ObjectOutputStream oout = new ObjectOutputStream(bout);
	oout.writeObject(obj);
	oout.close();
	ObjectInputStream oin = new ObjectInputStream(
	    new ByteArrayInputStream(bout.toByteArray()));
	return oin.readObject();
    }
}
