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
 * @summary test BasicMethodConstraints descriptor ordering restrictions
 * 
 * @run main/othervm Ordering
 */
import net.jini.constraint.BasicMethodConstraints;
import net.jini.constraint.BasicMethodConstraints.MethodDesc;

public class Ordering {

    static MethodDesc desc(String n, Class[] t) {
	if (t == null) {
	    return new MethodDesc(n, null);
	} else {
	    return new MethodDesc(n, t, null);
	}
    }

    static void legal(MethodDesc d1, MethodDesc d2) {
	new BasicMethodConstraints(new MethodDesc[]{d1, d2});
    }

    static void legal(String n1, Class[] t1, String n2, Class[] t2) {
	legal(desc(n1, t1), desc(n2, t2));
    }

    static void illegal(MethodDesc d1, MethodDesc d2) {
	try {
	    new BasicMethodConstraints(new MethodDesc[]{d1, d2});
	    throw new RuntimeException("illegal ordering succeeded");
	} catch (IllegalArgumentException e) {
	    System.out.println(e.getMessage());
	}
    }

    static void illegal(String n1, Class[] t1, String n2, Class[] t2) {
	illegal(desc(n1, t1), desc(n2, t2));
    }

    static Class[] types1 = { String.class };
    static Class[] types2 = { String.class, int.class };

    static void legal(String n1, String n2) {
	legal(n1, null, n2, null);
	if (n2.indexOf('*') < 0) {
	    legal(n1, null, n2, types1);
	    legal(n1, null, n2, types2);
	}
	if (n1.indexOf('*') < 0) {
	    legal(n1, types1, n2, null);
	    if (n2.indexOf('*') < 0) {
		legal(n1, types1, n2, types1);
		legal(n1, types1, n2, types2);
	    }
	    legal(n1, types2, n2, null);
	    if (n2.indexOf('*') < 0) {
		legal(n1, types2, n2, types1);
		legal(n1, types2, n2, types2);
	    }
	}
    }

    static void illegal(String n1, String n2) {
	illegal(n1, null, n2, null);
	if (n2.indexOf('*') < 0) {
	    illegal(n1, null, n2, types1);
	    illegal(n1, null, n2, types2);
	}
	if (n1.indexOf('*') < 0) {
	    illegal(n1, types1, n2, null);
	    if (n2.indexOf('*') < 0) {
		illegal(n1, types1, n2, types1);
		illegal(n1, types1, n2, types2);
	    }
	    illegal(n1, types2, n2, null);
	    if (n2.indexOf('*') < 0) {
		illegal(n1, types2, n2, types1);
		illegal(n1, types2, n2, types2);
	    }
	}
    }

    static void unordered(String n1, String n2) {
	legal(n1, n2);
	legal(n2, n1);
    }

    static void ordered(String n1, String n2) {
	legal(n1, n2);
	illegal(n2, n1);
    }

    static void def(String n) {
	MethodDesc d = new MethodDesc(null);
	legal(desc(n, null), d);
	illegal(d, desc(n, null));
	if (n.indexOf('*') < 0) {
	    legal(desc(n, types1), d);
	    illegal(d, desc(n, types1));
	    legal(desc(n, types2), d);
	    illegal(d, desc(n, types2));
	}
    }

    public static void main(String[] args) {
	unordered("foo", "foobar");
	ordered("foo", "foo*");
	unordered("foo", "bar*");
	unordered("foo", "foobar*");
	ordered("foo", "f*");
	ordered("foo", "*foo");
	ordered("foobar", "*bar");
	unordered("foo", "*bar");
	ordered("foobar*", "foo*");
	unordered("foo*", "bar*");
	unordered("*foo", "*bar");
	ordered("*foobar", "*bar");
	unordered("foo*", "*bar");
	illegal("foo", null, "foo", null);
	illegal("foo", null, "foo", types1);
	illegal("foo", null, "foo", types2);
	legal("foo", types1, "foo", null);
	legal("foo", types1, "foo", types2);
	legal("foo", types2, "foo", null);
	legal("foo", types2, "foo", types1);
	illegal("foo*", null, "foo*", null);
	illegal("*foo", null, "*foo", null);
	def("foo");
	def("foo*");
	def("*foo");
    }
}
