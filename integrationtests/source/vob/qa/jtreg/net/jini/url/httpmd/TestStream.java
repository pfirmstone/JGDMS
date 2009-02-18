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
 * @summary Tests reading from md: URL streams
 * @library ../../../../unittestlib
 * @build UnitTestUtilities BasicTest Test HttpServer
 * @run main/othervm
 *	-Djava.protocol.handler.pkgs=net.jini.url
 *	TestStream
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import net.jini.url.httpmd.WrongMessageDigestException;

public class TestStream extends BasicTest {

    static final String src =
	System.getProperty("test.src", ".").replace(File.separatorChar, '/');

    static {
	System.setProperty("java.protocol.handler.pkgs",
			   "net.jini.url");
    }

    static Test[] tests = {

	/* Bad URL syntax */
	new TestStream(createURL(""), MalformedURLException.class),
	new TestStream(createURL("abc.test"), MalformedURLException.class),
	new TestStream(createURL(";"), MalformedURLException.class),
	new TestStream(createURL(";="), MalformedURLException.class),
	new TestStream(createURL(";md5="), MalformedURLException.class),
	new TestStream(createURL(";=abcd"), MalformedURLException.class),
	new TestStream(createURL(";ugh=abcd"), MalformedURLException.class),
	new TestStream(createURL(";md5=abcx"), MalformedURLException.class),
	new TestStream(createURL(";md5=abcd,a!bc"),
		       MalformedURLException.class),

	/* Good URL syntax */
	new TestStream("TestStream.java;md5=abc",
		       WrongMessageDigestException.class),
	new TestStream("TestStream.java;md5=12345678901234567890123456789012",
		       WrongMessageDigestException.class),
	new TestStream(";md5=12345678", FileNotFoundException.class),
	new TestStream("empty.test;md5=d41d8cd98f00b204e9800998ecf8427e", ""),
	new TestStream(
	    "empty.test;sha=da39a3ee5e6b4b0d3255bfef95601890afd80709", ""),
	new TestStream(
	    "abc.test;md5=e302f9ecd2d189fa80aac1c3392e9b9c,This_is_a_comment",
	    "abcdefghijklmnopqrstuvwxyz\n"),
	new TestStream(
	    "abc.test;sha-1=8c723a0fa70b111017b4a6f06afe1c0dbcec14e3",
	    "abcdefghijklmnopqrstuvwxyz\n"),
	new TestStream("Read partial, ", "abc.test;sha1=abcd", "abcd") {
	    String read(BufferedReader in) throws IOException {
		char[] buf = new char[4];
		for (int i = 0; i < 4; i++) {
		    int n = in.read();
		    if (n < 0) {
			throw new FailedException("Too few chars");
		    }
		    buf[i] = (char) n;
		}
		return new String(buf);
	    }
	}
    };

    Object spec;
    HttpServer server;

    public static void main(String[] args) {
	test(tests);
    }

    TestStream(Object spec, Object shouldBe) {
	this("", spec, shouldBe);
    }

    TestStream(String name, Object spec, Object shouldBe) {
	super(name + spec, shouldBe);
	this.spec = spec;
    }

    public Object run() throws Exception {
	server = new HttpServer(src);
	try {
	    URL url = (spec instanceof URL)
		? (URL) spec
		: new URL("httpmd://localhost:" + server.port + '/' + spec);

	    BufferedReader in = null;
	    try {
		in = new BufferedReader(
		    new InputStreamReader(url.openStream()));
		return read(in);
	    } catch (Exception e) {
		return e;
	    } finally {
		if (in != null) {
		    try {
			in.close();
		    } catch (WrongMessageDigestException e) {
		    }
		}
	    }
	} catch (Exception e) {
	    server.shutdown();
	    throw e;
	}
    }

    String read(BufferedReader in) throws IOException {
	StringBuffer sb = new StringBuffer();
	char[] buf = new char[100];
	while (true) {
	    int n = in.read(buf);
	    if (n < 0) {
		break;
	    }
	    sb.append(buf, 0, n);
	}
	return sb.toString();
    }

    public void check(Object result) throws Exception {
	try {
	    Object compareTo = getCompareTo();
	    if (compareTo instanceof Class) {
		if (result == null || compareTo != result.getClass()) {
		    throw new FailedException(
			"Should be of type " + compareTo);
		}
	    } else {
		super.check(result);
	    }
	} finally {
	    if (server != null) {
		server.shutdown();
	    }
	}
    }

    static URL createURL(String file) {
	try {
	    return new URL("httpmd", "", -1, file);
	} catch (MalformedURLException e) {
	    throw unexpectedException(e);
	}
    }
}
