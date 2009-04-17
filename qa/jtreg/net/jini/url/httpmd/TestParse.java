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
 * @summary Tests parsing httpmd: URLs
 * @library ../../../../unittestlib
 * @build UnitTestUtilities BasicTest Test
 * @run main/othervm
 *	-Djava.protocol.handler.pkgs=net.jini.url
 *	TestParse
 */

import java.net.URL;
import java.net.MalformedURLException;

public class TestParse extends BasicTest {

    static {
	System.setProperty("java.protocol.handler.pkgs",
			   "net.jini.url");
    }

    static Test[] tests = {

	/* Bad absolute syntax */
	new TestParse("httpmd:", MalformedURLException.class),
	new TestParse("httpmd://foo:20/bar/baz?q#r",
		      MalformedURLException.class),
	new TestParse("httpmd://foo/bar/baz;?q#r",
		      MalformedURLException.class),
	new TestParse("httpmd:/bar/baz;md5?q#r",
		      MalformedURLException.class),
	new TestParse("httpmd:baz;md5=?q#r", MalformedURLException.class),
	new TestParse("httpmd:baz;md5=abxd#r", MalformedURLException.class),
	new TestParse("httpmd:baz;ugh=abcd", MalformedURLException.class),
	new TestParse("httpmd:baz;=", MalformedURLException.class),
	new TestParse("httpmd:baz;=abcd", MalformedURLException.class),
	new TestParse("httpmd:baz;md5=abcd;ugh=1234",
		      MalformedURLException.class),
	new TestParse("httpmd:baz?;md5=abcd", MalformedURLException.class),
	new TestParse("httpmd:baz?q#;md5=abcd", MalformedURLException.class),
	new TestParse("httpmd:baz?q;md5=abcd,!", MalformedURLException.class),
	new TestParse("httpmd:,", MalformedURLException.class),
	new TestParse("httpmd:,comment", MalformedURLException.class),

	/* Good absolute syntax */
	new TestParse("  HTTPMD://FOO:20/bar/baz;p1=v1;MD5=ABCD?q#r",
		      "httpmd://FOO:20/bar/baz;p1=v1;MD5=ABCD?q#r"),
	new TestParse("httpmd://foo:20/bar/baz;sha=1234?q  ",
		      "httpmd://foo:20/bar/baz;sha=1234?q"),
	new TestParse("httpmd://foo:20/bar/baz;md5=1234",
		      "httpmd://foo:20/bar/baz;md5=1234"),
	new TestParse("httpmd://foo/bar/baz;md5=1234",
		      "httpmd://foo/bar/baz;md5=1234"),
	new TestParse("httpmd:/bar/baz;md5=1234",
		      "httpmd:/bar/baz;md5=1234"),
	new TestParse("httpmd:/bar/baz;md5=1234,?q#r",
		      "httpmd:/bar/baz;md5=1234,?q#r"),
	new TestParse("httpmd:/bar/baz;md5=1234,Hello7-_.~*'():@&=+$,#r",
		      "httpmd:/bar/baz;md5=1234,Hello7-_.~*'():@&=+$,#r"),
	new TestParse("httpmd:/bar/baz;md5=1234,x*?q",
		      "httpmd:/bar/baz;md5=1234,x*?q"),
	new TestParse("httpmd:/bar,baz;md5=1234,c1",
		      "httpmd:/bar,baz;md5=1234,c1"),
	new TestParse(
	    "httpmd:baz;sha-1=99f6837808c0a79398bf69d83cfb1b82d20cf0cf",
	    "httpmd:baz;sha-1=99f6837808c0a79398bf69d83cfb1b82d20cf0cf"),
	new TestParse("httpmd:;md5=1234", "httpmd:;md5=1234"),

	/* Bad relative syntax */
	new TestParse("httpmd://alpha/beta/gamma;p2=v2;md5=abcd?q2#r2",
		      ";sha", MalformedURLException.class),
	new TestParse("httpmd://alpha/beta/gamma;p2=v2;md5=abcd?q2#r2",
		      ";sha=", MalformedURLException.class),
	new TestParse("httpmd://alpha/beta/gamma;p2=v2;md5=abcd?q2#r2",
		      ";=", MalformedURLException.class),
	new TestParse("httpmd://alpha/beta/gamma;p2=v2;md5=abcd?q2#r2",
		      ";=abcd", MalformedURLException.class),
	new TestParse("httpmd://alpha/beta/gamma;p2=v2;md5=abcd?q2#r2",
		      ";sha=abxd", MalformedURLException.class),
	new TestParse("httpmd://alpha/beta/gamma;p2=v2;md5=abcd?q2#r2",
		      "//foo:20/bar/baz?q#r",
		      MalformedURLException.class),
	new TestParse("httpmd://alpha/beta/gamma;p2=v2;md5=abcd?q2#r2",
		      "//foo/bar/baz;?q#r",
		      MalformedURLException.class),
	new TestParse("httpmd://alpha/beta/gamma;p2=v2;md5=abcd?q2#r2",
		      "/bar/baz;sha?q#r",
		      MalformedURLException.class),
	new TestParse("httpmd://alpha/beta/gamma;p2=v2;md5=abcd?q2#r2",
		      "/bar/baz;=?q#r", MalformedURLException.class),
	new TestParse("httpmd://alpha/beta/gamma;p2=v2;md5=abcd?q2#r2",
		      "baz;sha=?q#r", MalformedURLException.class),
	new TestParse("httpmd://alpha/beta/gamma;p2=v2;md5=abcd?q2#r2",
		      "baz;sha=abxd#r",
		      MalformedURLException.class),
	new TestParse("httpmd://alpha/beta/gamma;p2=v2;md5=abcd?q2#r2",
		      "baz;ugh=abcd", MalformedURLException.class),
	new TestParse("httpmd://alpha/beta/gamma;p2=v2;md5=abcd?q2#r2",
		      "baz;sha=abcd;ugh=1234",
		      MalformedURLException.class),
	new TestParse("httpmd://alpha/beta/gamma;p2=v2;md5=abcd?q2#r2",
		      "baz?;sha=abcd", MalformedURLException.class),
	new TestParse("httpmd://alpha/beta/gamma;p2=v2;md5=abcd?q2#r2",
		      "baz?q#;sha=abcd", MalformedURLException.class),
	new TestParse("httpmd://alpha/beta/gamma;p2=v2;md5=abcd?q2#r2",
		      "baz", MalformedURLException.class),
	new TestParse("httpmd://alpha/beta/gamma;p2=v2;md5=abcd?q2#r2",
		      "baz,comment", MalformedURLException.class),
	new TestParse("httpmd://alpha/beta/gamma;p2=v2;md5=abcd?q2#r2",
		      ",!bad-comment", MalformedURLException.class),
	new TestParse("httpmd://alpha/beta/gamma;p2=v2;md5=abcd?q2#r2",
		      "baz;sha=abcd,{?q#r", MalformedURLException.class),
	new TestParse(createURL(""), ",abc", MalformedURLException.class),
	new TestParse(createURL("/abc,def/"), ",ghi",
		      MalformedURLException.class),

	/* Good relative syntax */
	new TestParse("httpmd://alpha:20/beta/gamma;p2=v2;md5=abcd,c1?q2#r2",
		      "httpmd://foo:30/bar/baz;sha=1234,c2?q#r",
		      "httpmd://foo:30/bar/baz;sha=1234,c2?q#r"),
	new TestParse("httpmd://alpha:20/beta/gamma;p2=v2;md5=abcd,c1?q2#r2",
		      "httpmd://foo:30/bar/baz;a=b,c;sha=1234?q",
		      "httpmd://foo:30/bar/baz;a=b,c;sha=1234?q"),
	new TestParse("httpmd://alpha:20/beta/gamma;p2=v1,2;md5=abcd?q2#r2",
		      "//foo:30/bar/baz;sha=1234,c2#r",
		      "httpmd://foo:30/bar/baz;sha=1234,c2#r"),
	new TestParse("httpmd://alpha:20/beta/gamma;p2=v2;md5=abcd?q2#r2",
		      "//foo:30/bar/baz;sha=1234",
		      "httpmd://foo:30/bar/baz;sha=1234"),
	new TestParse("httpmd://alpha:20/beta/gamma;p2=v2;md5=abcd?q2#r2",
		      "/bar/baz;sha=1234?q#r",
		      "httpmd://alpha:20/bar/baz;sha=1234?q#r"),
	new TestParse("httpmd://alpha:20/beta/gamma;p2=v2;md5=abcd?q2#r2",
		      "baz;sha=1234?q#r",
		      "httpmd://alpha:20/beta/baz;sha=1234?q#r"),
	new TestParse("httpmd://alpha:20/beta/gamma;p2=v2;md5=abcd?q2#r2",
		      "baz;sha=1234?q#r",
		      "httpmd://alpha:20/beta/baz;sha=1234?q#r"),
	new TestParse("httpmd://alpha:20/beta/gamma;p2=v2;md5=abcd?q2#r2",
		      ";sha=1234?q#r",
		      "httpmd://alpha:20/beta/;sha=1234?q#r"),
	new TestParse("httpmd://alpha:20/beta/gamma;p2=v2;md5=abcd,c2?q2#r2",
		      ",c1?q1#r1",
		      "httpmd://alpha:20/beta/gamma;p2=v2;md5=abcd,c1?q1#r1"),
	new TestParse("httpmd://alpha:-1/beta/gamma;p2=v2;md5=abcd,c2?q2#r2",
		      ",c1?q1",
		      "httpmd://alpha:-1/beta/gamma;p2=v2;md5=abcd,c1?q1"),
	new TestParse("httpmd://alpha/beta/gamma;p2=v2;md5=abcd,c2?q2#r2",
		      ",c1#r1",
		      "httpmd://alpha/beta/gamma;p2=v2;md5=abcd,c1#r1"),
	new TestParse("httpmd://alpha/beta/gamma;p2=v2;md5=abcd,c2?q2#r2",
		      ",c1",
		      "httpmd://alpha/beta/gamma;p2=v2;md5=abcd,c1"),
	new TestParse("httpmd://alpha/beta/gamma;p2=v2;md5=abcd,c2?q2#r2",
		      ",",
		      "httpmd://alpha/beta/gamma;p2=v2;md5=abcd,"),
	new TestParse("httpmd://alpha/beta/gamma;p2=v2;md5=abcd,c2?q2",
		      ",?q1#r1",
		      "httpmd://alpha/beta/gamma;p2=v2;md5=abcd,?q1#r1"),
	new TestParse("httpmd://alpha/beta/gamma;p2=v2;md5=abcd,c2?q2",
		      ",#r1",
		      "httpmd://alpha/beta/gamma;p2=v2;md5=abcd,#r1"),
	new TestParse(createURL("/abc,def/"), "ghi;md5=1234",
		      "httpmd:/abc,def/ghi;md5=1234")
    };

    URL context;
    String spec;

    public static void main(String[] args) {
	test(tests);
    }

    TestParse(String spec, Object shouldBe) {
	this((String) null, spec, shouldBe);
    }

    TestParse(String context, String spec, Object shouldBe) {
	super((context == null ? "" : "'" + context + "', ") +
	      "'" + spec + "'", shouldBe);
	if (context != null) {
	    try {
		this.context = new URL(context);
	    } catch (MalformedURLException e) {
		throw unexpectedException(e);
	    }
	}
	this.spec = spec;
    }

    TestParse(URL context, String spec, Object shouldBe) {
	super("'" + context + "', '" + spec + "'", shouldBe);
	this.context = context;
	this.spec = spec;
    }

    static URL createURL(String file) {
	try {
	    return new URL("httpmd", "", file);
	} catch (MalformedURLException e) {
	    throw unexpectedException(e);
	}
    }

    public Object run() throws Exception {
	try {
	    return new URL(context, spec);
	} catch (MalformedURLException e) {
	    return e;
	}
    }

    public void check(Object result) throws Exception {
	Object compareTo = getCompareTo();
	if (compareTo instanceof Class) {
	    if (result == null || compareTo != result.getClass()) {
		throw new FailedException(
		    "Should be instance of " + compareTo);
	    }
	} else {
	    String compareToString = ((String) compareTo).trim();
	    if (!(result instanceof URL)) {
		throw new FailedException(
		    "Should be instance of " + URL.class);
	    } else if (!result.toString().trim().equals(compareToString)) {
		throw new FailedException(
		    "String value should equal " + compareToString);
	    } else {
		URL compareToURL = new URL(compareToString);
		if (!compareToURL.equals(result)) {
		    throw new FailedException(
			"Should be equal to " + compareToURL);
		}
	    }
	}
    }
}
