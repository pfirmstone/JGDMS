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

import java.io.FileNotFoundException;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.text.MessageFormat;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import net.jini.url.httpmd.HttpmdUtil;

/**
 * Prints the message digest for the contents of a URL. This utility is run
 * from the {@linkplain #main command line}. <p>
 *
 * An example command line (shown with lines wrapped for readability) is:
 *
 * <blockquote>
 * <pre>
 * java -jar <var><b>install_dir</b></var>/lib/computedigest.jar \
 *      <var><b>install_dir</b></var>/lib/reggie.jar \
 *      SHA-1
 * </pre>
 * </blockquote>
 *
 * where <var><b>install_dir</b></var> is the directory where the Apache River release
 * is installed. This command prints out the message digest for the
 * <code>reggie.jar</code> JAR file, using the <code>SHA-1</code> algorithm.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
public class ComputeDigest {
    private static ResourceBundle resources;
    private static boolean resinit = false;

    private ComputeDigest() { }

    /**
     * Prints the message digest for the contents of a URL. The command
     * line arguments are:
     * <pre>
     * <var><b>url</b></var> [ <var><b>algorithm</b></var> ]
     * </pre>
     * The first argument specifies the URL, which is parsed in the context
     * of a <code>file:</code> URL. The second argument, if present,
     * specifies the message digest algorithm, which defaults to
     * <code>SHA-1</code>.
     */
    public static void main(String[] args) {
	if (args.length < 1 || args.length > 2) {
	    print("computedigest.usage", null);
	    System.exit(1);
	}
	String algorithm = args.length > 1 ? args[1] : "SHA-1";
	try {
	    URL url = new URL(new URL("file:"), args[0]);
	    System.out.println(HttpmdUtil.computeDigest(url, algorithm));
	    return;
	} catch (FileNotFoundException e) {
	    print("computedigest.notfound", args[0]);
	} catch (NoSuchAlgorithmException e) {
	    print("computedigest.badalg", algorithm);
	} catch (Throwable t) {
	    t.printStackTrace();
	}
	System.exit(1);
    }

    private static synchronized String getString(String key) {
	try {
	    if (!resinit) {
		resources = ResourceBundle.getBundle(
				 "com.sun.jini.tool.resources.computedigest");
		resinit = true;
	    }
	    return resources.getString(key);
	} catch (MissingResourceException e) {
	    e.printStackTrace();
	    System.err.println("Unable to find a required resource.");
	    System.exit(1);
	    return null;
	}
    }

    private static void print(String key, String val) {
	String fmt = getString(key);
	if (fmt == null)
	    fmt = "no text found: \"" + key + "\" {0}";
	System.err.println(MessageFormat.format(fmt, new String[]{val}));
    }
}
