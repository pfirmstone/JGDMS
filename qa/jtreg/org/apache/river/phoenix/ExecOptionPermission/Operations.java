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
 * @summary test basic operations of ExecOptionPermission
 * @author Bob Scheifler
 * @library ../../../../../testlibrary
 * @build RMID Operations
 * @run main/othervm/policy=security.policy Operations
 */
import java.io.File;
import java.lang.reflect.Constructor;
import java.rmi.server.RMIClassLoader;
import java.security.Permission;

public class Operations {

    /**
     * ExecOptionPermission(String) constructor.
     */
    private static Constructor cons;

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
     * Return an instance with the given target.
     */
    public static Permission eop(String name) {
	try {
	    return (Permission) cons.newInstance(new Object[]{name});
	} catch (Exception e) {
	    throw new Error("oops", e);
	}
    }

    /**
     * Replace / with File.separatorChar.
     */
    public static String sc(String s) {
	if (File.separatorChar != '/') {
	    s = s.replace('/', File.separatorChar);
	}
	return s;
    }

    /**
     * Check that the two permissions are functionally equivalent.
     */
    public static void equals(String n1, String n2) {
	Permission p1 = eop(n1);
	Permission p2 = eop(n2);
	v(p1.equals(p2));
	v(p2.equals(p1));
	v(p1.hashCode() == p2.hashCode());
	v(p1.implies(p2));
	v(p2.implies(p1));
    }

    /**
     * Check that the two permissions are not equal and that p1 implies p2.
     */
    public static void implies(String n1, String n2) {
	Permission p1 = eop(n1);
	Permission p2 = eop(n2);
	v(p1.implies(p2));
	v(!p2.implies(p1));
	v(!p1.equals(p2));
	v(!p2.equals(p1));
    }

    /**
     * Check that the two permissions are not equal and neither implies the
     * other.
     */
    public static void diff(String n1, String n2) {
	Permission p1 = eop(n1);
	Permission p2 = eop(n2);
	v(!p1.equals(p2));
	v(!p2.equals(p1));
	v(!p1.implies(p2));
	v(!p2.implies(p1));
    }

    public static void main(String[] args) throws Exception {
	if (System.getSecurityManager() == null) {
	    System.setSecurityManager(new SecurityManager());
	}
	cons = RMIClassLoader.loadClass(
		    new File(RMID.getDefaultLocation()).toURL(),
		    "org.apache.river.phoenix.ExecOptionPermission").
	    getConstructor(new Class[]{String.class});
	equals("foo", "foo");
	equals("foo", "\"foo\"");
	equals("foo\"", "\"foo\"\"");
	equals("foo}", "\"foo}\"");
	equals("}", "\"}\"");
	equals("foo*", "foo*");
	equals("foo{bar}", "foo{bar}");
	equals("\"", "\"\"\"");
	equals("", "\"\"");
	equals("*", "{<<ALL FILES>>}");
	equals("foo*", "foo{<<ALL FILES>>}");
	implies("foo*", "foo");
	implies("foo*", "foobar");
	implies("foo*", "\"foo*\"");
	implies("*", "foobar");
	implies("*", "");
	implies("foo*", "foobar*");
	implies("foo*", "foo{*}");
	implies("foo*", "foo{bar}");
	implies("foo*", sc("foo{bar/*}"));
	implies("foo*", sc("foo{bar/-}"));
	implies("foo*", "foobar{baz}");
	implies("foo*", sc("foo{/bar/baz}"));
	implies("foo{bar}", "foobar");
	implies("foo{bar}", "foo" + new File("bar").getCanonicalPath());
	implies("foo{*}", "foobar");
	implies("foo{*}", "foo" + new File("bar").getCanonicalPath());
	implies("foo{*}", "foo{bar}");
	implies(sc("foo{bar/-}"), sc("foo{bar/*}"));
	implies(sc("foo{bar/-}"), sc("foobar/baz/biz"));
	implies(sc("foo{bar/-}"),
		"foo" + new File("bar/baz/biz").getCanonicalPath());
	implies("foo{-}", "foo{*}");
	implies("foo{-}", sc("foo{bar/*}"));
	implies("foo{<<ALL FILES>>}", "foobar");
	implies("foo{<<ALL FILES>>}", "foo{bar}");
	implies("foo{<<ALL FILES>>}", sc("foo{/bar/baz}"));
	implies("{}", System.getProperty("user.dir"));
	implies("foo{}", "foo" + System.getProperty("user.dir"));
	diff("foo", "bar");
	diff("foo", "\"foo");
	diff("foo", "foo\"");
	diff("", "\"");
	diff("foo*", "bar*");
	diff("foo{bar}", "foo{foo}");
	diff("foo{bar}", "bar{bar}");
	diff(sc("foo{bar/*}"), sc("foobar/baz/biz"));
	diff(sc("foo{bar/-}"), sc("foobars/baz/biz"));
	diff(sc("foo{bar/-}"), sc("foobar/{-}"));
    }
}
