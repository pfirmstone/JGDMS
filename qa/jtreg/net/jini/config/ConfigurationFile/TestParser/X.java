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
/*
 * 
 * Class used by TestParser
 */

import java.util.*;
import java.io.Serializable;
import java.security.PrivilegedAction;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;

public class X {
    static Object packageField;

    private static int count;

    public X() { }

    X(Object o) { }

    public X(String o1, Object o2) { }

    public X(Object o, String s) { }

    public X(Object o1, Object o2, Object o3) { }

    X(Object o1, Object o2, String s) { }

    public String toString() { return "X"; }

    public static int count(int incr) {
	return count += incr;
    }

    public static Object throwObject(Throwable t) throws Throwable {
	throw t;
    }

    static Object nonPublic() { return "nonPublic"; }

    public static Object objectMethod(Object o) { return o; }

    public static Hashtable hashtableMethod(Hashtable h) { return h; }

    public static Properties propertiesMethod(Properties p) { return p; }

    public static Serializable serializableMethod(Serializable s) {
	return s;
    }

    public static PrivilegedAction actionMethod(PrivilegedAction p) {
	return p;
    }

    public static Map mapMethod(Map m) { return m; }

    public static Object ambiguous(Object o, String s) {
	return "ambiguous(Object, String)"; }

    public static Object ambiguous(String s, Object o) {
	return "ambiguous(String, Object)";
    }

    public static Object notAmbiguous(Object o, String s) {
	return "notAmbiguous(Object, String)";
    }

    static Object notAmbiguous(String s, Object o) {
	return "notAmbiguous(String, Object)";
    }

    public static Object notAmbiguous2(Object o) { return "X.notAmbiguous2"; }

    public static Object notAmbiguous3(Object o) { return "Object"; }
    public static Object notAmbiguous3(Serializable s) {
	return "Serializable";
    }
    public static Object notAmbiguous3(Set s) { return "Set"; }
    public static Object notAmbiguous3(HashSet s) { return "HashSet"; }

    public static class Y { }

    public static class N extends X {
	public static Object ambiguous(Class c, String s) {
	    return "ambiguous(Class, String)"; }

	public static Object notAmbiguous2(Object o) {
	    return "X.N.notAmbiguous2";
	}
    }

    public static class Throw {
	public Throw(Throwable t) throws Throwable {
	    throw t;
	}
    }

    public static Object getConfigEntry(Configuration config,
					String component,
					String name,
					Class type)
	throws ConfigurationException
    {
	return config.getEntry(component, name, type);
    }

    public static class MyClassLoader extends ClassLoader { }
}
