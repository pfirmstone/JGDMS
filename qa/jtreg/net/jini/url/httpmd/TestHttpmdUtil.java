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
 * @summary Test the MdUtil class
 * @library ../../../../unittestlib
 * @build UnitTestUtilities BasicTest Test
 * @run main/othervm
 *	-Djava.protocol.handler.pkgs=net.jini.url
 *	TestHttpmdUtil
 */

import java.io.File;
import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import net.jini.url.httpmd.HttpmdUtil;

public class TestHttpmdUtil extends UnitTestUtilities {

    static final String srcDir;

    static final String srcURL;

    static {
        String root = System.getProperty("test.root", ".");  
        StringBuilder sb = new StringBuilder();
        sb.append(root).append(File.separator).append("net").append(File.separator);
        sb.append("jini").append(File.separator).append("url");
        sb.append(File.separator).append("httpmd").append(File.separator);
        srcDir = sb.toString();
        srcURL = srcDir.replace(File.separatorChar, '/') + "/";
	System.setProperty("java.protocol.handler.pkgs",
			   "net.jini.url");
    }

    static Object[] tests = {
	TestComputeDigest.tests,
	TestComputeDigestCodebase.tests
    };

    public static void main(String[] args) {
	test(tests);
    }

    public static class TestComputeDigest extends BasicTest {
	static Test[] tests = {
	    new TestComputeDigest("file:" + srcURL + "notfound", "md5",
				  FileNotFoundException.class),
	    new TestComputeDigest("file:" + srcURL + "empty.test", "unknown",
				  NoSuchAlgorithmException.class),
	    new TestComputeDigest(null, "md5", NullPointerException.class),
	    new TestComputeDigest("file:" + srcURL + "empty.test", null,
				  NullPointerException.class),
	    new TestComputeDigest("file:" + srcURL + "empty.test", "md5",
				  "d41d8cd98f00b204e9800998ecf8427e"),
	    new TestComputeDigest("file:" + srcURL + "abc.test", "sha1",
				  "8c723a0fa70b111017b4a6f06afe1c0dbcec14e3")
	};

	String url;
	String algorithm;

	public static void main(String[] args) {
	    test(tests);
	}

	TestComputeDigest(String url, String algorithm, Object shouldBe) {
	    super(url + ", " + algorithm, shouldBe);
	    this.url = url;
	    this.algorithm = algorithm;
	}

	public Object run() throws Exception {
	    URL u = url != null ? new URL(url) : null;
	    try {
		return HttpmdUtil.computeDigest(u, algorithm);
	    } catch (Exception e) {
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
		super.check(result);
	    }
	}
    }

    public static class TestComputeDigestCodebase extends BasicTest {
	static Test[] tests = {
	    new TestComputeDigestCodebase(
		srcDir,
		"httpmd://foo/empty.test;md5=0 httpmd://foo/unknown;sha=0",
		FileNotFoundException.class),
	    new TestComputeDigestCodebase(
		"/thisisanunknowndirectory", "httpmd://foo/empty.test;md5=0",
		FileNotFoundException.class),

	    new TestComputeDigestCodebase(
		srcDir, "unknownprotocol://foo",
		IllegalArgumentException.class),
	    new TestComputeDigestCodebase(
		srcDir, "http://foo", IllegalArgumentException.class),
	    new TestComputeDigestCodebase(
		srcDir, "httpmd", IllegalArgumentException.class),
	    new TestComputeDigestCodebase(
		srcDir, "//foo/bar;sha=33", IllegalArgumentException.class),

	    new TestComputeDigestCodebase(
		srcDir, "httpmd://foo/bar;", MalformedURLException.class),
	    new TestComputeDigestCodebase(
		srcDir, "httpmd://foo/bar;sha=",
		MalformedURLException.class),
	    new TestComputeDigestCodebase(
		srcDir, "httpmd://foo/bar;=abcd",
		MalformedURLException.class),
	    new TestComputeDigestCodebase(
		srcDir, "httpmd://foo/bar;sha=xyz",
		MalformedURLException.class),
	    new TestComputeDigestCodebase(
		srcDir, "httpmd://foo/bar;unknown=abc",
		MalformedURLException.class),
	    new TestComputeDigestCodebase(
		srcDir, "httpmd://foo/bar;sha=abcd,!badcomment!",
		MalformedURLException.class),

	    new TestComputeDigestCodebase(
		null, "httpmd://foo/bar;md5=0", NullPointerException.class),
	    new TestComputeDigestCodebase(
		srcDir, null, NullPointerException.class),
	    new TestComputeDigestCodebase(
		null, null, NullPointerException.class),

	    new TestComputeDigestCodebase(
		srcDir, "httpmd://foo/empty.test;md5=0,foo+bar",
		"httpmd://foo/empty.test" +
		";md5=d41d8cd98f00b204e9800998ecf8427e,foo+bar"),
	    new TestComputeDigestCodebase(
		srcDir,
		"httpmd://foo:44/empty.test;p=v;sha=0#r " +
		"httpmd://alpha/A.txt;md5=33?q",
		"httpmd://foo:44/empty.test" +
		";p=v;sha=da39a3ee5e6b4b0d3255bfef95601890afd80709#r " +
		"httpmd://alpha/A.txt" +
		";md5=05bbfa4ba88e575ec71f61e3100aaa4a?q"),
	    new TestComputeDigestCodebase(
		srcDir,
		"httpmd:abc.test;md5=abcdef",
		"httpmd:abc.test;md5=e302f9ecd2d189fa80aac1c3392e9b9c"),
	    new TestComputeDigestCodebase(
		srcDir,
		"httpmd:A.txt;md5=abcdef",
		"httpmd:A.txt;md5=05bbfa4ba88e575ec71f61e3100aaa4a"),
	    new TestComputeDigestCodebase(
		srcDir,
		"httpmd:%41.txt;md5=abcdef",
		"httpmd:%41.txt;md5=05bbfa4ba88e575ec71f61e3100aaa4a")
	};

	String sourceDirectory;
	String codebase;

	public static void main(String[] args) {
	    test(tests);
	}

	TestComputeDigestCodebase(String sourceDirectory,
				  String codebase,
				  Object shouldBe)
	{
	    super(sourceDirectory + ", '" + codebase + "'", shouldBe);
	    this.sourceDirectory = sourceDirectory;
	    this.codebase = codebase;
	}

	public Object run() throws Exception {
	    try {
		return HttpmdUtil.computeDigestCodebase(
		    sourceDirectory, codebase);
	    } catch (Exception e) {
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
	    } else if (result == null
		       || compareTo == null
		       /*
			* Compare string values since URL comparison ignores
			* comments.
			*/
		       || !result.toString().equals(compareTo.toString()))
	    {
		throw new FailedException("Should be: " + compareTo);
	    }
	}
    }
}
