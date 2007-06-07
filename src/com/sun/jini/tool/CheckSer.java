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
package com.sun.jini.tool;

import java.io.File;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ResourceBundle;
import java.util.MissingResourceException;
import java.text.MessageFormat;


/**
 * Tool to check for serializable classes that do not have explicit
 * <code>serialVersionUID</code> fields.
 *
 * @author Sun Microsystems, Inc.
 */
public class CheckSer {

    private static final int MASK = Modifier.STATIC | Modifier.FINAL;

    /**
     * Checks class file directory hierarchies for serializable classes
     * that do not have explicit <code>serialVersionUID</code> fields,
     * and prints the names of such classes to the standard output stream.
     * The only options are zero or more filenames that specify the roots
     * of directory hierarchies; if no filenames are specified, the single
     * root <code>/vob/jive/classes</code> is used. In those hierarchies,
     * each file with a name ending in the suffix <code>.class</code> is
     * treated as a class file; the corresponding class name is obtained
     * from the filename by stripping off both the original prefix root
     * filename and the <code>.class</code> suffix, and replacing each file
     * separator character with a period (<code>.</code>). Each such class
     * is loaded from the class loader of this tool. If the class is not an
     * interface, directly or indirectly implements {@link Serializable},
     * and does not have a declared <code>static</code> <code>final</code>
     * field named <code>serialVersionUID</code>, then the name of the class
     * is printed to the standard output stream.
     *
     * @param args the roots of directory hierarchies
     */
    public static void main(String[] args) {
	if (args.length == 0)
	    args = new String[]{"/vob/jive/classes"};
	for (int i = 0; i < args.length; i++) {
	    check(args[i], args[i].length() + 1);
	}
    }

    /**
     * Checks the class file directory hierarchy starting from the specified
     * directory. In the hierarchy, each file with a name ending in the
     * suffix <code>.class</code> is treated as a class file; the
     * corresponding class name is obtained from the filename by stripping
     * off the first <code>strip</code> characters of prefix and the
     * <code>.class</code> suffix, and replacing each file separator
     * character with a period (<code>.</code>). Each such class is loaded
     * from the class loader of this tool. If the class is not an interface,
     * directly or indirectly implements {@link Serializable}, and does not
     * have a declared <code>static</code> <code>final</code> field named
     * <code>serialVersionUID</code>, then the name of the class is printed
     * to the standard output stream.
     *
     * @param dir directory hierarchy root
     * @param strip number of characters of prefix to strip from each
     * class file name
     */
    public static void check(String dir, int strip) {
	String[] files = new File(dir).list();
	if (files == null) {
	    print("checkser.nodir", dir);
	    return;
	}
	for (int i = 0; i < files.length; i++) {
	    String file = dir + File.separatorChar + files[i];
	    if (file.endsWith(".class"))
		checkClass(file, strip);
	    else if (new File(file).isDirectory())
		check(file, strip);
	}
    }

    private static void checkClass(String file, int strip) {
	file = file.substring(strip, file.length() - 6);
	file = file.replace(File.separatorChar, '.');
	try {
	    Class c = Class.forName(file);
	    if (c.isInterface() || !Serializable.class.isAssignableFrom(c))
		return;
	    Field f = c.getDeclaredField("serialVersionUID");
	    if ((f.getModifiers() & MASK) != MASK)
		System.out.println(file);
	} catch (ClassNotFoundException e) {
	    print("checkser.failed", file);
	} catch (NoClassDefFoundError e) {
	    print("checkser.failed", file);
	} catch (NoSuchFieldException e) {
	    System.out.println(file);
	}
    }

    private static ResourceBundle resources;
    private static boolean resinit = false;

    private static synchronized String getString(String key) {
	if (!resinit) {
	    try {
		resources = ResourceBundle.getBundle(
				     "com.sun.jini.tool.resources.checkser");
		resinit = true;
	    } catch (MissingResourceException e) {
		e.printStackTrace();
	    }
	}
	try {
	    return resources.getString(key);
	} catch (MissingResourceException e) {
	    return null;
	}
    }

    private static void print(String key, String val) {
	String fmt = getString(key);
	if (fmt == null)
	    fmt = "no text found: \"" + key + "\" {0}";
	System.out.println(MessageFormat.format(fmt, new String[]{val}));
    }
}
