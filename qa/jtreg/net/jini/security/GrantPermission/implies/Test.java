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
 * @summary Verify proper functioning of GrantPermission.implies() and equals()
 */

import java.security.Permission;
import java.security.UnresolvedPermission;
import net.jini.security.GrantPermission;

public class Test {
    public static void main(String[] args) throws Exception {
        /* 21st July 2010 - Peter Firmstone commented: I changed the constructors
         * from using string representations that relied on class visibility
         * to generate UnresolvedPermission's, this test failled due to 
         * the Permission's classes being successfully resolved by GrantPermission
         * due to that fact that jsk-policy.jar was no longer loaded by the
         * jre/lib/ext Extension ClassLoader.
         * 
         * I figure this is ok as the tests states that it is testing implies()
         * and equals(), not the proper parsing of string's in constructors.
         */ 
        UnresolvedPermission foo = new UnresolvedPermission("FooPermission", null, null, null),
                             foo2 = new UnresolvedPermission("FooPermission", null, null, null),
                             bar = new UnresolvedPermission("BarPermission", null, null, null);
        Permission [] foos = {foo, foo2};
        Permission [] fooBar = {foo, bar};
	Permission gp1 = new GrantPermission(foo),
		   gp2 = new GrantPermission(foos),
		   gp3 = new GrantPermission(new FooPermission()),
		   gp4 = new GrantPermission(fooBar),
		   gp5 = new GrantPermission(bar);
		   
	// sanity check
	if (!(gp1.equals(gp2) && gp1.implies(gp2) &&
	      gp1.hashCode() == gp2.hashCode())) 
	{
	    throw new Error();
	}
	// unresolved FooPerm should imply resolved FooPerm
	if (!gp1.implies(gp3)) {
	    throw new Error("GrantPermission, containing UnresolvedPermission" +
                    "identical to a resolved instance of that permission" +
                    "does not imply as expected");
	}
	// resolved FooPerm should not imply or equal unresolved FooPerm
	if (gp1.equals(gp3) || gp3.implies(gp1)) {
	    throw new Error();
	}
	// original equality should still hold despite gp1 "inflation"
	if (!(gp1.equals(gp2) && gp1.implies(gp2) && gp2.implies(gp1) &&
	      gp1.hashCode() == gp2.hashCode())) 
	{
	    throw new Error();
	}
	// unresolved FooPerm should not imply or equal unresolved FooPerm +
	// unresolved BarPerm
	if (gp1.implies(gp4) || gp1.equals(gp4)) {
	    throw new Error();
	}
	// ...but unresolved FooPerm, BarPerm should imply unresolved FooPerm
	if (!gp4.implies(gp1)) {
	    throw new Error();
	}
	// disjoint unresolved perms shouldn't imply or equal each other
	if (gp1.implies(gp5) || gp5.implies(gp1) || gp1.equals(gp5)) {
	    throw new Error();
	}
	// sanity check
	if (!gp4.implies(gp5)) {
	    throw new Error();
	}
	// test self implies(), equals()
	Permission[] gpa = { gp1, gp2, gp3, gp4, gp5 };
	for (int i = 0; i < gpa.length; i++) {
	    Permission gp = gpa[i];
	    if (!(gp.implies(gp) && gp.equals(gp))) {
		throw new Error();
	    }
	}
    }
}
