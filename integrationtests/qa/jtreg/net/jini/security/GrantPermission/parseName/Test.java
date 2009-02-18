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
 * @summary Test GrantPermission constructor which parses list of Permissions.
 * @build Test BasePermission Permission0 Permission1 Permission2
 * @run main Test
 */

import java.security.Permission;
import net.jini.security.GrantPermission;

public class Test {
    public static void main(String[] args) throws Exception {
	Permission[] pa = new Permission[]{
	    new Permission0(),
	    new Permission1("1n"),
	    new Permission2("2n", "2a")
	};
	Permission gp = new GrantPermission(
	    "Permission0;" +
	    "Permission1 \"1n\";" +
	    "Permission2 \"2n\", \"2a\";"
	);
	for (int i = 0; i < pa.length; i++) {
	    if (!gp.implies(new GrantPermission(pa[i]))) {
		throw new Error();
	    }
	}
	if (gp.implies(new GrantPermission(new Permission1("other")))) {
	    throw new Error();
	}
	
	gp = new GrantPermission(
	    "delim=| " +
	    "Permission0;" +
	    "Permission1 |1n|;" +
	    "Permission2 |2n|, |2a|;"
	);
	for (int i = 0; i < pa.length; i++) {
	    if (!gp.implies(new GrantPermission(pa[i]))) {
		throw new Error();
	    }
	}
	if (gp.implies(new GrantPermission(new Permission1("other")))) {
	    throw new Error();
	}

	
	pa = new Permission[]{
	    new Permission1(null), new Permission2(null, null)
	};
	gp = new GrantPermission("Permission1; Permission2");
	for (int i = 0; i < pa.length; i++) {
	    if (!gp.implies(new GrantPermission(pa[i]))) {
		throw new Error();
	    }
	}
	
	gp = new GrantPermission("Permission2 \"2n\"");
	if (!gp.implies(new GrantPermission(new Permission2("2n", null)))) {
	    throw new Error();
	}
	
	gp = new GrantPermission(
	    "Permission2 \"a \\\"nested\\\" quote\", \"foo\"");
	if (!gp.implies(
	    new GrantPermission(new Permission2("a \"nested\" quote", "foo"))))
	{
	    throw new Error();
	}
	
	gp = new GrantPermission(
	    "delim=' Permission2 'a \"nested\" quote', 'foo'");
	if (!gp.implies(
	    new GrantPermission(new Permission2("a \"nested\" quote", "foo"))))
	{
	    throw new Error();
	}
	
	String[] illegal = new String[]{
	    "",
	    ";",
	    "FooPermission \"a\" \"b\"",
	    "FooPermission ;;",
	    "FooPermission \"a\", \"b\", \"c\"",
	    "\"FooPermission\"",
	    "FooPermission unquoted",
	    "delim=bad FooPermission",
	    "delim=; FooPermission",
	    "delim=|",
	    "delim=| delim=% FooPermission",
	    "delim=\t FooPermission",
	    "delim=\n FooPermission"
	};
	for (int i = 0; i < illegal.length; i++) {
	    try {
		new GrantPermission(illegal[i]);
		throw new Error();
	    } catch (IllegalArgumentException ex) {
	    }
	}
    }
}
