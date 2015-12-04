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
package org.apache.river.test.spec.security.util;

// java.io
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.FileNotFoundException;

// java.net
import java.net.URL;
import java.net.MalformedURLException;

// java.util
import java.util.Arrays;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

// java.security
import java.security.Permission;


/**
 * Utility class for security spec-tests.
 */
public class Util {

    /** Comparator for sorting classes */
    private static final ClassComparator classComparator
            = new ClassComparator();

    /** Comparator for sorting permissions */
    private static final Comparator permComparator
            = new PermissionComparator();

    /**
     * Creates resource jar-file with specified resource and specified names
     * of classes.
     *
     * @param rName resource name
     * @param clNames array of classes whose names will be put to the given
     *        resource
     * @return created jar-file with resources or null if rName is null
     *         or empty string
     */
    public static File createResourceJar(String rName, Class[] clNames)
            throws IOException, FileNotFoundException {
        if (rName == null || rName.length() == 0) {
            return null;
        }
        File jarFile = File.createTempFile("jini.test.spec.security", ".jar");
        JarOutputStream jos =
                new JarOutputStream(new FileOutputStream(jarFile));
        ZipEntry ze = new ZipEntry(rName);
        byte[] bArr;
        jos.putNextEntry(ze);

        for (int i = 0; i < clNames.length; ++i) {
            if (clNames[i] != null) {
                bArr = clNames[i].getName().concat("\n").getBytes();
                jos.write(bArr, 0, bArr.length);
            }
        }
        jos.closeEntry();
        jos.close();
        return jarFile;
    }

    /**
     * Returns string representation of array of classes.
     *
     * @param list array of classes
     * @return string representation of the list
     */
    public static String arrayToString(Class[] list) {
        if (list == null) {
            return null;
        }
        String res = "[ ";

        for (int i = 0; i < list.length; ++i) {
            res += list[i].getName() + " ";
        }
        res += "]";
        return res;
    }

    /**
     * Returns string representation of array of objects.
     *
     * @param list array of objects
     * @return string representation of the list
     */
    public static String arrayToString(Object[] list) {
        if (list == null) {
            return null;
        }
        String res = "[ ";

        for (int i = 0; i < list.length; ++i) {
            res += list[i] + " ";
        }
        res += "]";
        return res;
    }

    /**
     * Convert a string containing a space-separated list of URLs into a
     * corresponding array of URL objects, throwing a MalformedURLException
     * if any of the URLs are invalid.
     *
     * @param str string with a list of URLs
     * @return array of URL objects from str
     * @throws MalformedURLException if any of the URLs are invalid
     */
    public static URL[] strToUrls(String str) throws MalformedURLException {
        StringTokenizer st = new StringTokenizer(str);
        URL[] urls = new URL[st.countTokens()];

        for (int i = 0; st.hasMoreTokens(); i++) {
            urls[i] = new URL(st.nextToken());
        }
	return urls;
    }

    /**
     * Return classes from 2-nd given array (expClasses) which are not present
     * in 1-st array (testClasses). It compares arrays by name because
     * classes could be defined in different class loaders.
     *
     * @param testClasses list of classes wheres search classes from
     *        expClasses
     * @param expClasses list of classes which should be present in
     *        testClasses
     * @return classes from expClasses which are not present in testClasses or
     *         null if all classes from expClasses are in testClasses
     */
    public static Class[] containsClasses(Class[] testClasses, 
            Class[] expClasses) {
        ArrayList res = new ArrayList();
        Arrays.sort(testClasses, classComparator);

        for (int i = 0; i < expClasses.length; ++i) {
            if (Arrays.binarySearch(testClasses, expClasses[i],
                    classComparator) < 0) {
                res.add(expClasses[i]);
            }
        }
        return (res.size() > 0) ? ((Class []) res.toArray(
                new Class[res.size()])) : null;
    }

    /**
     * Compares 2 arrays of permissions where elements could be unordered.
     *
     * @param arr1 1-st array for comparison
     * @param arr2 2-nd array for comparison
     * @return true if 1-st array contains the same values as 2-nd one
     *         (possibly unordered)
     */
    public static boolean comparePermissions(Object[] arr1, Object[] arr2) {
        if (((arr1 == null) ? (arr2 == null) : ((arr2 != null)
                && (arr1.length == arr2.length))) && (arr1 != null)) {
            Arrays.sort(arr1, permComparator);
            Arrays.sort(arr2, permComparator);
            return Arrays.equals(arr1, arr2);
        } else {
            return false;
        }
    }

    /**
     * Excludes from 1-st array values from 2-nd one.
     *
     * @param arr1 1-st array
     * @param arr2 2-nd array
     * @return array containing values from 1-st array which is not in 2-nd one
     */
    public static Object[] excludeValues(Object[] arr1, Object[] arr2) {
        ArrayList list = new ArrayList();

        for (int i = 0; i < arr1.length; ++i) {
            boolean isFound = false;

            for (int j = 0; j < arr2.length; ++j) {
                if (arr1[i].equals(arr2[j])) {
                    isFound = true;
                    break;
                }
            }

            if (!isFound) {
                list.add(arr1[i]);
            }
        }
       return list.toArray();
    }


    /** Comparator for sorting permissions. */
    private static class PermissionComparator implements Comparator {
        public PermissionComparator() {}

        /** Alphabetical for 'toString' methods. */
        public int compare(Object o1, Object o2) {
            Permission p1 = (Permission) o1;
            Permission p2 = (Permission) o2;

            if (p1 == p2) {
                return 0;
            }
            return p1.toString().compareTo(p2.toString());
        }
    }


    /** Comparator for sorting classes. */
    private static class ClassComparator implements Comparator {
        public ClassComparator() {}

        /** Alphabetical within a given class. */
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
