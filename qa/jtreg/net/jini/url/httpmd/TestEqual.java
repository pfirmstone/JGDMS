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
 * @summary Tests equal on httpmd: URLs
 * @library ../../../../unittestlib
 * @build UnitTestUtilities BasicTest Test
 * @run main/othervm
 *	-Djava.protocol.handler.pkgs=net.jini.url
 *	TestEqual
 */

import java.net.MalformedURLException;
import java.net.URL;

public class TestEqual extends BasicTest {

    static {
	System.setProperty("java.protocol.handler.pkgs",
			   "net.jini.url");
    }

    static Test[] tests = {
	/* Case sensitivity */
	new TestEqual("HTTPMD://FOO:20/bar/baz;p1=v1;MD5=ABCD?q#r",
		      "httpmd://foo:20/bar/baz;p1=v1;md5=abcd?q#r",
		      true),
	new TestEqual("httpmd://foo:20/BAR/baz;p1=v1;md5=abcd?q#r",
		      "httpmd://foo:20/bar/baz;p1=v1;md5=abcd?q#r",
		      false),
	new TestEqual("httpmd://foo:20/bar/baz;P1=V1;md5=abcd?q#r",
		      "httpmd://foo:20/bar/baz;p1=v1;md5=abcd?q#r",
		      false),
	new TestEqual("httpmd://foo:20/bar/baz;p1=v1;md5=abcd?Q#r",
		      "httpmd://foo:20/bar/baz;p1=v1;md5=abcd?q#r",
		      false),
	new TestEqual("httpmd://foo:20/bar/baz;p1=v1;md5=abcd?q#R",
		      "httpmd://foo:20/bar/baz;p1=v1;md5=abcd?q#r",
		      false, true),
	new TestEqual("httpmd://foo:20/bar/baz;p1=v1;md5=abcd,abC?q#r",
		      "httpmd://foo:20/bar/baz;p1=v1;md5=abcd,abc?q#r",
		      true),
	/* Content differences */
	new TestEqual("httpmd://u1@foo:88/bar/baz;p1=v1;md5=abcd?q#r",
		      "httpmd://u2@foo:88/bar/baz;p1=v1;md5=abcd?q#r",
		      true),
	new TestEqual("httpmd://u1@foo:88/bar/baz;p1=v1;md5=abcd?q#r",
		      "httpmd://foo:88/bar/baz;p1=v1;md5=abcd?q#r",
		      true),
	new TestEqual("httpmd://foo:88/bar/baz;p1=v1;md5=abcd?q#r",
		      "httpmd://alpha:88/bar/baz;p1=v1;md5=abcd?q#r",
		      false),
	new TestEqual("httpmd://foo:80/bar/baz;p1=v1;md5=abcd?q#r",
		      "httpmd://foo:-1/bar/baz;p1=v1;md5=abcd?q#r",
		      true),
	new TestEqual("httpmd://foo:80/bar/baz;p1=v1;md5=abcd?q#r",
		      "httpmd://foo/bar/baz;p1=v1;md5=abcd?q#r",
		      true),
	new TestEqual("httpmd://foo:-1/bar/baz;p1=v1;md5=abcd?q#r",
		      "httpmd://foo/bar/baz;p1=v1;md5=abcd?q#r",
		      true),
	new TestEqual("httpmd://foo:80/bar/baz;p1=v1;md5=abcd?q#r",
		      "httpmd://foo:22/bar/baz;p1=v1;md5=abcd?q#r",
		      false),
	new TestEqual("httpmd:/bar/baz;p1=v1;md5=abcd?q#r",
		      "httpmd:/beta/baz;p1=v1;md5=abcd?q#r",
		      false),
	new TestEqual("httpmd:/bar/baz;p1=v1;md5=abcd?q#r",
		      "httpmd:/baz;p1=v1;md5=abcd?q#r",
		      false),
	new TestEqual("httpmd:/bar/baz;p1=v1;md5=abcd?q#r",
		      "httpmd:/bar/gamma;p1=v1;md5=abcd?q#r",
		      false),
	new TestEqual("httpmd:/bar/baz;p1=v1;md5=abcd?q#r",
		      "httpmd:/bar/baz;p2=v2;md5=abcd?q#r",
		      false),
	new TestEqual("httpmd:baz;p1=v1;md5=abcd?q#r",
		      "httpmd:baz;p1=v1;sha=abcd?q#r",
		      false),
	new TestEqual("httpmd:baz;p1=v1;sha=abcd?q#r",
		      "httpmd:baz;p1=v1;sha-1=abcd?q#r",
		      false),
	new TestEqual("httpmd:baz;md5=abcd?q#r",
		      "httpmd:baz;md5=abc?q#r",
		      false),
	new TestEqual("httpmd:baz;md5=abcd?q#r",
		      "httpmd:baz;md5=abc1?q#r",
		      false),
	new TestEqual("httpmd:baz;md5=abcd",
		      "httpmd:baz;md5=1bcd",
		      false),
	new TestEqual("httpmd:baz;md5=abcd?q#r",
		      "httpmd:baz;md5=abcd?q#r2",
		      false, true),
	new TestEqual("httpmd:baz;md5=abcd?q#r",
		      "httpmd:baz;md5=abcd?q",
		      false, true),
	new TestEqual("httpmd:baz;md5=abcd?q#",
		      "httpmd:baz;md5=abcd?q",
		      false, true),
	new TestEqual("httpmd:baz;md5=abcd?q#r",
		      "httpmd:baz;md5=abcd?q2#r",
		      false),
	new TestEqual("httpmd:baz;md5=abcd?q",
		      "httpmd:baz;md5=abcd?q2",
		      false),
	new TestEqual("httpmd:baz;md5=abcd?",
		      "httpmd:baz;md5=abcd",
		      false),
	new TestEqual("httpmd://foo/bar/baz;md5=abcd,",
		      "httpmd://foo/bar/baz;md5=abcd",
		      true),
	new TestEqual("httpmd://foo/bar/baz;md5=abcd,#r",
		      "httpmd://foo/bar/baz;md5=abcd#r",
		      true),
	new TestEqual("httpmd://foo/bar/baz;md5=abcd,?q",
		      "httpmd://foo/bar/baz;md5=abcd?q",
		      true),
	new TestEqual("httpmd://foo/bar/baz;md5=abcd,abcd?q",
		      "httpmd://foo/bar/baz;md5=abcd,abc?q",
		      true),
	/* URLs with unchecked file fields */
	new TestEqual(createURL("foo"), createURL("foo"), true),
	new TestEqual(createURL("foo;"), createURL("foo"), false),
	new TestEqual(createURL("foo;"), createURL("foo;"), true),
	new TestEqual(createURL("foo;ugh=zip"), createURL("foo;"),
		      false),
	new TestEqual(createURL("foo;ugh=zip"), createURL("foo;ugh=ZIP"),
		      true),
	new TestEqual(createURL(",abc"), createURL(",def"), false),
	new TestEqual(createURL(";a=b,abc"), createURL(";a=b,def"), true),
	new TestEqual(createURL(";a=b,abc"), createURL(";a=b"), true)
    };

    URL u1;
    URL u2;
    boolean sameFile;

    public static void main(String[] args) {
	test(tests);
    }

    TestEqual(String u1, String u2, boolean result) {
	this(u1, u2, result, result);
    }

    TestEqual(String u1, String u2, boolean result, boolean sameFile) {
	super(u1 + ", " + u2, Boolean.valueOf(result));
	try {
	    this.u1 = new URL(u1);
	    this.u2 = new URL(u2);
	} catch (MalformedURLException e) {
	    throw unexpectedException(e);
	}
	this.sameFile = sameFile;
    }

    TestEqual(URL u1, URL u2, boolean result) {
	super(u1 + ", " + u2, Boolean.valueOf(result));
	this.u1 = u1;
	this.u2 = u2;
	this.sameFile = result;
    }

    static URL createURL(String file) {
	try {
	    return new URL("httpmd", "", file);
	} catch (MalformedURLException e) {
	    throw unexpectedException(e);
	}
    }

    public Object run() {
	return Boolean.valueOf(u1.equals(u2));
    }

    public void check(Object result) throws Exception {
	super.check(result);
	if (!result.equals(Boolean.valueOf(u2.equals(u1)))) {
	    throw new FailedException(
		"Different value when arguments are reversed");
	}
	if (result.equals(Boolean.TRUE) && u1.hashCode() != u2.hashCode()) {
	    throw new FailedException("Hash codes should not differ");
	}
	boolean sameFileResult = u1.sameFile(u2);
	if (sameFileResult != sameFile) {
	    throw new FailedException(
		"Wrong value for sameFile -- should be " + sameFile);
	} else if (u2.sameFile(u1) != sameFile) {
	    throw new FailedException(
		"Wrong value for sameFile with reversed arguments -- " +
		"should be " + sameFile);
	}
    }
}
