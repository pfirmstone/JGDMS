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
import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import net.jini.url.httpmd.Handler;
import net.jini.url.httpmd.HttpmdUtil;

/**
 * Computes the message digests for a codebase with HTTPMD URLs. This utility
 * is run from the {@linkplain #main command line}. <p>
 * A description of HTTPMD URLs can be found in the {@link net.jini.url.httpmd}
 * package and its {@link net.jini.url.httpmd.Handler} class.<p>
 *
 * An example command line (shown with lines wrapped for readability) is:
 *
 * <blockquote>
 * <pre>
 * java -jar <var><b>install_dir</b></var>/lib/computehttpmdcodebase.jar \
 *      <var><b>install_dir</b></var>/lib-dl \
 *      "httpmd://<var><b>your_host</b></var>:<var><b>http_port</b></var>/sdm-dl.jar;md5=0"
 * </pre>
 * </blockquote>
 *
 * where <var><b>install_dir</b></var> is the directory where the Apache River release
 * is installed, <var><b>your_host</b></var> is the host where the HTTP server
 * for the <code>sdm-dl.jar</code> JAR file will be running, and
 * <var><b>http_port</b></var> is the port for that server. This command prints
 * out the download codebase for use by a client that uses the {@link
 * net.jini.lookup.ServiceDiscoveryManager}, using an HTTPMD URL to guarantee
 * integrity for the classes in the <code>sdm-dl.jar</code> JAR file. The
 * message digest will be computed using the <code>md5</code> algorithm, and
 * the <code>0</code> will be replaced by the computed digest.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
public class ComputeHttpmdCodebase {
    private static ResourceBundle resources;
    private static boolean resinit = false;

    private ComputeHttpmdCodebase() { }

    /**
     * Computes the message digests for a codebase made up of HTTPMD URLs.
     * The command line arguments are:
     * <pre>
     * <var><b>source-directory</b></var> <var><b>url</b></var>...
     * </pre>
     * The first argument is the filename or URL of the directory containing
     * the source files for the HTTPMD URLs. The remaining arguments specify
     * the HTTPMD URLs that make up the codebase. The digest values specified
     * in the HTTPMD URLs will be ignored (zeroes are typically used). The
     * path portion of each HTTPMD URL, without the message digest parameters,
     * names a source file relative to the source directory; the message
     * digest for that source file is computed and replaces the digest value
     * in the HTTPMD URL. The resulting HTTPMD URLs are printed, separated by
     * spaces.
     * <p>
     * Do not use a directory on a remote filesystem, or a directory URL, if
     * the underlying network access protocol does not provide adequate data
     * integrity or authentication of the remote host.
     */
    public static void main(String[] args) {
	if (args.length < 2) {
	    print("computecodebase.usage", (String) null);
	    System.exit(1);
	}
	Handler handler = new Handler();
	StringBuffer codebase = new StringBuffer();
	try {
	    boolean isURL;
	    URL base;
	    try {
		base = new URL(args[0].endsWith("/") ?
			       args[0] : args[0] + "/");
		isURL = true;
	    } catch (MalformedURLException e) {
		File sourceDirectory = new File(args[0]);
		if (!sourceDirectory.isDirectory()) {
		    print("computecodebase.notdir", args[0]);
		    System.exit(1);
		}
		base = sourceDirectory.toURI().toURL();
		isURL = false;
	    }
	    for (int i = 1; i < args.length; i++) {
		String spec = args[i];
		if (!"httpmd:".regionMatches(true, 0, spec, 0, 7)) {
		    print("computecodebase.nonhttpmd", spec);
		    System.exit(1);
		}
		URL url;
		try {
		    url = new URL(null, spec, handler);
		} catch (MalformedURLException e) {
		    print("computecodebase.badurl",
			  new String[]{spec, e.getLocalizedMessage()});
		    System.exit(1);
		    return; // not reached, make compiler happy
		}
		String path = url.getPath();
		int paramIndex = path.lastIndexOf(';');
		int equalsIndex = path.indexOf('=', paramIndex);
		int commentIndex = path.indexOf(',', equalsIndex);
		String algorithm = path.substring(paramIndex + 1, equalsIndex);
		URL source =
		    new URL(base,
			    path.substring(path.startsWith("/") ? 1 : 0,
					   path.indexOf(';')));
		String digest;
		try {
		    digest = HttpmdUtil.computeDigest(source, algorithm);
		} catch (FileNotFoundException e) {
		    print("computecodebase.notfound",
			  isURL ? source.toExternalForm() : source.getPath());
		    System.exit(1);
		    return; // not reached, make compiler happy
		}
		URL result = new URL(
		    url,
		    path.substring(0, equalsIndex + 1) + digest +
		    (commentIndex < 0 ? "" : path.substring(commentIndex)) +
		    (url.getQuery() == null ? "" : '?' + url.getQuery()) +
		    (url.getRef() == null ? "" : '#' + url.getRef()));
		if (codebase.length() > 0) {
		    codebase.append(' ');
		}
		codebase.append(result);
	    }
	} catch (Throwable t) {
	    t.printStackTrace();
	    System.exit(1);
	}
	System.out.println(codebase);
    }

    private static synchronized String getString(String key) {
	try {
	    if (!resinit) {
		resources = ResourceBundle.getBundle(
			       "com.sun.jini.tool.resources.computecodebase");
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
	print(key, new String[]{val});
    }

    private static void print(String key, String[] vals) {
	String fmt = getString(key);
	if (fmt == null)
	    fmt = "no text found: \"" + key + "\" {0}";
	System.err.println(MessageFormat.format(fmt, vals));
    }
}
