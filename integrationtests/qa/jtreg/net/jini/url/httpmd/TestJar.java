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
 * @summary Tests using md: URLs for JAR files
 * @library ../../../../unittestlib
 * @build UnitTestUtilities BasicTest Test HttpServer
 * @run main/othervm
 *	-Djava.protocol.handler.pkgs=net.jini.url
 *	TestJar
 */

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.StringTokenizer;

public class TestJar extends BasicTest {

    static final String src =
	System.getProperty("test.src", ".").replace(File.separatorChar, '/');

    static {
	System.setProperty("java.protocol.handler.pkgs",
			   "net.jini.url");
    }

    static Test[] tests = {
	new TestJar("A", "http:a.jar", "A"),
	new TestJar("A", "httpmd:a.jar;md5=abcd", null),
	new TestJar(
	    "A",
	    "httpmd:a.jar;md5=152c17ed9cf9aaf78a3bcdcdab6f887b,a.jar",
	    "A"),

	new TestJar("B", "http:b.jar", null),
	new TestJar(
	    "B", "httpmd:b.jar;md5=0f2f6eb1e26584a61528febe85601f86", "B"),
	new TestJar(
	    "B",
	    "httpmd:b.jar;sha=747f1a890580d2ad03ccd6d283e96fe947cfb6e2,+.jar",
	    "B"),
	new TestJar("B", "http:a.jar http:b.jar", "B"),
	new TestJar(
	    "B",
	    "httpmd:a.jar;sha=1bafb9abfacb64a13582a16675b355428ca3e464 " +
	    "httpmd:b.jar;md5=0f2f6eb1e26584a61528febe85601f86",
	    "B"),
	new TestJar("B",
		    "httpmd:wrapped.jar;md5=4f7b148d292eaf352cd0d3e4f0482173",
		    "B"),
	new TestJar(
	    "B",
	    "httpmd:b-indexed.jar;" +
	    "sha=b5c0849b7a958e24a525405b7817ed74000fd429,hi",
	    "B")
    };

    String className;
    String specs;
    HttpServer server;

    public static void main(String[] args) {
	test(tests);
    }

    TestJar(String className, String specs, Object shouldBe) {
	super(className + ", " + specs, shouldBe);
	this.className = className;
	this.specs = specs;
    }

    public Object run() throws Exception {
	server = new HttpServer(src);
	try {
	    StringTokenizer tokens = new StringTokenizer(specs);
	    URL[] urls = new URL[tokens.countTokens()];
	    int i = 0;
	    while (tokens.hasMoreTokens()) {
		String spec = tokens.nextToken();
		int colon = spec.indexOf(':');
		urls[i++] = new URL(spec.substring(0, colon),
				    "localhost", server.port,
				    '/' + spec.substring(colon + 1));
	    }
	    ClassLoader cl = new URLClassLoader(urls);
	    try {
		return Class.forName(className, false, cl).newInstance();
	    } catch (Throwable e) {
		return e;
	    }
	} catch (Exception e) {
	    server.shutdown();
	    throw e;
	}
    }

    public void check(Object result) throws Exception {
	try {
	    Object compareTo = getCompareTo();
	    if (compareTo == null) {
		if (result == null
		    || !(result instanceof ClassNotFoundException
			 || result instanceof NoClassDefFoundError))
		{
		    throw new FailedException(
			"Should be instance of ClassNotFoundException " +
			"or NoClassDefFoundError");
		}
	    } else {
		if (result == null
		    || !compareTo.equals(result.getClass().getName()))
		{
		    throw new FailedException(
			"Should be instance of " + compareTo);
		}
	    }
	} finally {
	    if (server != null) {
		server.shutdown();
	    }
	}
    }
}
