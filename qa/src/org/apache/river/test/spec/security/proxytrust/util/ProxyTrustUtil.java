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
package org.apache.river.test.spec.security.proxytrust.util;

import java.util.logging.Level;

// java.lang
import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationHandler;

// java.util
import java.util.Comparator;
import java.util.Arrays;
import java.util.ArrayList;

// java.rmi
import java.rmi.Remote;

// net.jini
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.export.Exporter;
import net.jini.security.proxytrust.ProxyTrust;
import net.jini.security.proxytrust.TrustEquivalence;
import net.jini.security.proxytrust.ProxyTrustExporter;

// org.apache.river
import org.apache.river.jeri.internal.runtime.Util;


/**
 * Utility class containing different static methods.
 */
public class ProxyTrustUtil {

    /** Comparator for sorting classes */
    private static final ClassComparator comparator = new ClassComparator();

    /**
     * Return new proxy instance, implementing interfaces which are implemented
     * by impl class, defined in the same class loader as impl clas.
     * Invocation handler with specified impl will be created.
     *
     * @param impl Implementation class for proxy instance
     * @return new proxy instance
     * @throws Error if invocation handler is null
     */
    public static Object newProxyInstance(Object impl) {
        return newProxyInstance(impl, new InvHandler(impl), null);
    }

    /**
     * Return new proxy instance, implementing interfaces which are implemented
     * by impl class, defined in the same class loader as impl class and
     * containing specified invocation handler.
     *
     * @param impl Implementation class for proxy instance
     * @param ih Invocation handler
     * @return new proxy instance
     * @throws Error if invocation handler is null
     */
    public static Object newProxyInstance(Object impl, InvocationHandler ih) {
        return newProxyInstance(impl, ih, null);
    }

    /**
     * Return new proxy instance, implementing interfaces which are implemented
     * by impl class, defined in specified class loader (or in the same class
     * loader as impl class if cl parameter is null) and containing specified
     * invocation handler.
     *
     * @param impl Implementation class for proxy instance
     * @param ih Invocation handler
     * @param cl Class loader for proxy class (if null, then the same class
     *           loader as for impl class will be used)
     * @return new proxy instance
     * @throws Error if invocation handler is null
     */
    public static Object newProxyInstance(Object impl, InvocationHandler ih,
            ClassLoader cl) {
        if (ih == null) {
            throw new Error("Invocation handler specified is null.");
        }

        if (cl == null) {
            return Proxy.newProxyInstance(impl.getClass().getClassLoader(),
                    getAllInterfaces(impl), ih);
        } else {
            return Proxy.newProxyInstance(cl, getAllInterfaces(impl), ih);
        }
    }

    /**
     * Returns an array containing all interfaces implemented
     * by the given object or null if class is null.
     *
     * @param obj object for getting interfaces
     * @return an array containing all interfaces implemented by obj
     *         or null if obj is null
     */
    public static Class[] getAllInterfaces(Object obj) {
        if (obj == null) {
            return null;
        }
        ArrayList list = new ArrayList();
        getAllInterfaces(list, obj.getClass());
        return (Class []) list.toArray(new Class[list.size()]);
    }

    /**
     * Fills the given array list with all interfaces implemented
     * by the given class.
     *
     * @param list array list for filling
     * @param class class for getting interfaces
     */
    private static void getAllInterfaces(ArrayList list, Class cl) {
        Class superclass = cl.getSuperclass();

        if (superclass != null) {
            getAllInterfaces(list, superclass);
        }
        Class[] interfaces = cl.getInterfaces();

        for (int i = 0; i < interfaces.length; i++) {
            Class intf = interfaces[i];

            if (!(list.contains(intf))) {
                list.add(intf);
            }
        }
    }

    /**
     * Returns true if the interfaces implemented by obj1's class
     * are the same (possibly in different order) as obj2's class.
     *
     * @param obj1 object1 for comparison
     * @param obj2 object2 for comparison
     * @return true of obj1's class implements the same interfaces as obj2's one
     */
    public static boolean sameInterfaces(Object obj1, Object obj2) {
        if (obj1 == obj2) {
            return true;
        }

        if ((obj1 == null || obj2 == null) && obj1 != obj2) {
            return false;
        }
        Class[] intf1 = getAllInterfaces(obj1);
        Class[] intf2 = getAllInterfaces(obj2);
        Arrays.sort(intf1, comparator);
        Arrays.sort(intf2, comparator);

        if (intf1.length != intf2.length) {
            return false;
        } else {
            for (int i = 0; i < intf1.length; i++) {
                if (intf1[i] != intf2[i]) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Returns true if the interfaces implemented by obj1's class
     * are the same (and in the same order) as obj2's class.
     *
     * @param obj1 object1 for comparison
     * @param obj2 object2 for comparison
     * @return true of obj1's class implements the same interfaces as obj2's one
     */
    public static boolean equalInterfaces(Object obj1, Object obj2) {
        if (obj1 == obj2) {
            return true;
        }

        if ((obj1 == null || obj2 == null) && obj1 != obj2) {
            return false;
        }
        Class[] intf1 = getAllInterfaces(obj1);
        Class[] intf2 = getAllInterfaces(obj2);

        if (intf1.length != intf2.length) {
            return false;
        } else {
            for (int i = 0; i < intf1.length; i++) {
                if (intf1[i] != intf2[i]) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Returns string representation of interfaces implemented by proxy
     * specified.
     *
     * @param proxy dynamic Proxy class
     * @return string representation of interfaces implemented by proxy or null
     *         if proxy is null
     */
    public static String interfacesToString(Object proxy) {
        if (proxy == null) {
            return null;
        }
        return interfacesToString(proxy.getClass().getInterfaces());
    }

    /**
     * Returns string representation of array of interfaces.
     *
     * @param intList array of interfaces
     * @return string representation for intList or null if intList is null
     */
    public static String interfacesToString(Class[] intList) {
        if (intList == null) {
            return null;
        }

        if (intList.length == 0) {
            return "[]";
        }
        int i;
        String res = "[ ";

        for (i = 0; i < intList.length; ++i) {
            res += intList[i].getName() + " ";
        }
        res += "]";
        return res;
    }

    /**
     * Returns string representation of array of objects.
     *
     * @param list array of objects
     * @return string representation for list or null if list is null
     */
    public static String arrayToString(Object[] list) {
        if (list == null) {
            return null;
        }

        if (list.length == 0) {
            return "[]";
        }
        int i;
        String res = "[ ";

        for (i = 0; i < list.length; ++i) {
            if (list[i] != null && Proxy.isProxyClass(list[i].getClass())) {
                res += "Proxy" + interfacesToString(list[i]) + " ";
            } else {
                res += list[i] + " ";
            }
        }
        res += "]";
        return res;
    }


    /** Comparator for sorting classes. */
    private static class ClassComparator implements Comparator {
        public ClassComparator() {
        }

        /** Super before subclass, alphabetical within a given class */
        public int compare(Object o1, Object o2) {
            Class c1 = (Class) o1;
            Class c2 = (Class) o2;

            if (c1 == c2) {
                return 0;
            }
            return c1.getName().compareTo(c2.getName());
        }
    }
}
