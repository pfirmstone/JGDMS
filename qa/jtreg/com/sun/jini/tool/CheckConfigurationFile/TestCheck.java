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
 * @summary Tests CheckConfigurationFile
 * 
 * @library ../../../../../unittestlib ../../../../../testlibrary
 * @build UnitTestUtilities BasicTest TestLibrary Test
 * @run main/othervm/policy=policy/secure=NoExit TestCheck
 */

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.rmi.server.RMIClassLoader;
import java.util.StringTokenizer;

public class TestCheck extends UnitTestUtilities {

    static {
	if (System.getProperty("test.src") == null) {
	    System.setProperty("test.src", ".");
	}
	if (System.getProperty("test.classes") == null) {
	    System.setProperty("test.classes", "classes");
	}
    }

    static final String src =
	System.getProperty("test.src") + File.separator;
    static final String classes =
	System.getProperty("test.classes") + File.separator;

    static {
	if (System.getProperty("java.security.policy") == null) {
	    System.setProperty("java.security.policy", src + "policy");
	}
	if (System.getSecurityManager() == null) {
	    System.setSecurityManager(new NoExit());
	}
    }

    static Object[] tests = {
	TestArgs.localtests,
	TestSources.localtests
    };

    static final PrintStream systemErr = System.err;
    static Method mainMethod;

    static void invokeMain(String[] args) {
	try {
	    mainMethod.invoke(null, new Object[]{args});
	} catch (IllegalAccessException e) {
	    throw new AssertionError(e);
	} catch (InvocationTargetException e) {
	    Throwable t = e.getTargetException();
	    if (t instanceof RuntimeException) {
		throw (RuntimeException) t;
	    } else {
		throw (Error) t;
	    }
	}
    }

    public static void main(String[] args) throws Exception {
	try {
	    String toolsJar =
		(TestLibrary.getExtraProperty("jsk.home",
					      TestLibrary.jskHome) +
		 File.separator + "lib" + File.separator + "tools.jar");
	    Class c = RMIClassLoader.loadClass(
				   new File(toolsJar).toURI().toURL(),
				   "com.sun.jini.tool.CheckConfigurationFile");
	    mainMethod = c.getMethod("main", new Class[]{String[].class});
	    NoExit.badFiles.add(src + "entries-no-perm");
	    NoExit.badFiles.add(src + "config-no-perm");
	    test(tests);
	} finally {
	    /* Permit calling exit again, since jtreg needs to do this */
	    NoExit.noExit = false;
	}
    }

    /** Test argument parsing */
    public static class TestArgs extends BasicTest {
	static Test[] localtests = {
	    new TestArgs("", false, "arguments:..."),
	    new TestArgs("-help", true, "arguments:..."),
	    new TestArgs("-entries", false, "arguments:..."),
	    new TestArgs("-entries " + src + "entries-prim",
			 false, "arguments:..."),
	    new TestArgs("-entries " + src + "entries-not-found " +
			 src + "config-prim",
			 false, "couldn't find: ..."),
	    new TestArgs("-entries " + src + "entries-no-perm " +
			 src + "config-prim",
			 false,
			 "java.security.AccessControlException reading: ..."),
	    new TestArgs("-entries " + src + " " + src + "config-prim",
			 false, "couldn't find: ..."),
	    new TestArgs(src + "config-not-found", false,
			 "couldn't find: ..."),
	    new TestArgs(src + "config-no-perm", false,
			 "java.security.AccessControlException reading: ..."),
	    new TestArgs(src + "config-prim", true, ""),
	    new TestArgs("-entries " + src + "entries-obj " +
			 src + "config-not-found",
			 false, "couldn't find: ..."),
	    new TestArgs("-entries " +
			 src + "entries-prim" + File.pathSeparator +
			 src + "entries-obj " +
			 src + "config-obj",
			 true, ""),
	    new TestArgs("-entries " +
			 src + "entries-prim" + File.pathSeparator +
			 src + "entries-obj " +
			 src + "config-prim",
			 true, ""),
	    new TestArgs("-entries " +
			 src + "entries-prim" + File.pathSeparator +
			 src + "entries-obj" + File.pathSeparator +
			 src + "entries-foo " +
			 src + "config-obj",
			 true, ""),
	    new TestArgs("-entries " +
			 src + "entries-prim" + File.pathSeparator +
			 src + "entries-obj " +
			 src + "config-foo",
			 false, "entry not permitted: ..."),
	    new TestArgs(src + "config-prim $%?", false,
			 "problem reading: ..."),
	    new TestArgs("-entries " + src + "entries-prim " +
			 src + "config-prim $%?",
			 false, "problem reading: ..."),
	    new TestArgs("- comp.foo=abcd", false,
			 "problem obtaining actual type of entry ..."),
	    new TestArgs("-entries " + src + "entries-prim - " +
			 "comp.booleanEntry=3", false,
			 "type mismatch for entry ..."),
	    new TestArgs("- comp.foo=3", true, ""),
	    new TestArgs("-entries " + src + "entries-prim - " +
			 "comp.intEntry=3", true, "")
	};

	String[] args;

	TestArgs(String args, boolean status, String output) {
	    super("'" + args + "'", status + ", " + output);
	    StringTokenizer tokens = new StringTokenizer(args);
	    this.args = new String[tokens.countTokens()];
	    for (int i = 0; tokens.hasMoreTokens(); i++) {
		this.args[i] = tokens.nextToken();
	    }
	}

	public Object run() {
	    ByteArrayOutputStream baos = new ByteArrayOutputStream();
	    try {
		System.setErr(new PrintStream(baos));
		invokeMain(args);
		System.err.flush();
		return "true, " + baos;
	    } catch (SecurityException e) {
		System.err.flush();
		return "false, " + baos;
	    } finally {
		System.setErr(systemErr);
	    }
	}

	public void check(Object result) {
	    String compareTo = (String) getCompareTo();
	    String string = (String) result;
	    if (compareTo.endsWith("...")) {
		compareTo = compareTo.substring(0, compareTo.length() - 3);
		if (!string.startsWith(compareTo)) {
		    throw new FailedException(
			"Should start with '" + compareTo + "'");
		}
	    } else if (!compareTo.equals(string)) {
		throw new FailedException("Should be '" + compareTo + "'");
	    }
	}

	public static void main(String[] args) {
	    test(localtests);
	}
    }

    /** Test checking configuration file sources */
    public static class TestSources extends BasicTest {
	static Test[] localtests = {
	    new TestSources("", null, true, ""),
	    new TestSources("", "", true, ""),
	    new TestSources("", "foo int", true, ""),
	    new TestSources("blah", null, false, "problem reading: "),
	    new TestSources("blah", "bar String", false, "problem reading: "),
	    new TestSources("comp { foo = new Boolean(33); }", null, false,
			    "problem obtaining actual type of entry "),
	    new TestSources("comp { foo = new foo.bar.Baz(); }", null, false,
			    "problem obtaining actual type of entry "),
	    new TestSources("comp { foo = 3; }", "comp.foo int", true, ""),
	    new TestSources("comp { foo = 3L; }", "comp.foo int", false,
			    "type mismatch for entry "),
	    new TestSources("comp { foo = new Integer(3); }", "comp.foo int",
			    false, "type mismatch for entry "),
	    new TestSources("comp { foo = null; }", "comp.foo int", false,
			    "type mismatch for entry "),
	    new TestSources("comp { foo = new Integer(3); }",
			    "comp.foo Integer", true, ""),
	    new TestSources("comp { foo = \"Hi\"; }", "comp.foo Integer",
			    false, "type mismatch for entry "),
	    new TestSources("comp { foo = 3; }", "comp.foo Integer", false,
			    "type mismatch for entry "),
	    new TestSources("comp { foo = null; }", "comp.foo Integer", true,
			    ""),
	    new TestSources("comp { foo = new java.util.HashMap(); }",
			    "comp.foo java.util.Map",
			    true, ""),
	    new TestSources("comp { foo = new java.util.Properties(); }",
			    "comp.foo java.util.Hashtable",
			    true, ""),
	    new TestSources("foo = new java.util.Hashtable();",
			    "foo java.util.Properties",
			    false, "problem reading: "),
	    new TestSources("comp { foo = 3; }", "comp.foo foo.bar.Baz", false,
			    "class not found for entry "),
	    /* Arrays */
	    new TestSources("comp { foo = new Integer[] { new Integer(3) }; }",
			    "comp.foo = [Ljava.lang.Integer\\;", true, ""),
	    new TestSources("comp { foo = new Integer[] { new Integer(3) }; }",
			    "comp.foo = Integer[]", true, ""),
	    new TestSources("comp { foo = new Integer[] { new Integer(3) }; }",
			    "comp.foo = [I", false,
			    "type mismatch for entry "),
	    new TestSources("comp { foo = new Integer[] { new Integer(3) }; }",
			    "comp.foo = int[]", false,
			    "type mismatch for entry "),
	    new TestSources("comp { foo = new Integer[] { new Integer(3) }; }",
			    "comp.foo = [Ljava.lang.Object\\;", true, ""),
	    new TestSources("comp { foo = new Integer[] { new Integer(3) }; }",
			    "comp.foo = java.lang.Object[]", true, ""),
	    new TestSources("comp { foo = new Object[] { null }; }",
			    "comp.foo = [Ljava.lang.Integer\\;", false,
			    "type mismatch for entry "),
	    new TestSources("comp { foo = new Object[] { null }; }",
			    "comp.foo = Integer[]", false,
			    "type mismatch for entry "),
	    new TestSources("comp { foo = new java.util.Map.Entry[] { }; }",
			    "comp.foo = java.util.Map.Entry", false,
			    "type mismatch for entry "),
	    new TestSources("comp { foo = new int[] { 3 }; }", "comp.foo = [I",
			    true, ""),
	    new TestSources("comp { foo = new int[] { 3 }; }",
			    "comp.foo = int[]",
			    true, ""),
	    new TestSources("comp { foo = null; }", "comp.foo = [[I", true,
			    ""),
	    new TestSources("comp { foo = null; }", "comp.foo = int[][]", true,
			    ""),
	    new TestSources("comp { foo = null; }", "comp.foo = [+", false,
			    "class not found for entry "),
	    new TestSources("comp { foo = null; }", "comp.foo = int[", false,
			    "class not found for entry "),
	    new TestSources("comp { foo = null; }", "comp.foo = int]", false,
			    "class not found for entry "),
	    new TestSources("comp { foo = null; }", "comp.foo = int[[", false,
			    "class not found for entry "),
	    new TestSources("comp { foo = null; }", "comp.foo = int][", false,
			    "class not found for entry "),
	    new TestSources("comp { foo = null; }", "comp.foo = int]]", false,
			    "class not found for entry "),
	    new TestSources("comp { foo = null; }", "comp.foo = int[[[", false,
			    "class not found for entry "),
	    new TestSources("comp { foo = null; }", "comp.foo = int[[]", false,
			    "class not found for entry "),
	    new TestSources("comp { foo = null; }", "comp.foo = int[][", false,
			    "class not found for entry "),
	    new TestSources("comp { foo = null; }", "comp.foo = int[]]", false,
			    "class not found for entry "),
	    new TestSources("comp { foo = null; }", "comp.foo = int][[", false,
			    "class not found for entry "),
	    new TestSources("comp { foo = null; }", "comp.foo = int][]", false,
			    "class not found for entry "),
	    new TestSources("comp { foo = null; }", "comp.foo = int]][", false,
			    "class not found for entry "),
	    new TestSources("comp { foo = null; }", "comp.foo = int]]]", false,
			    "class not found for entry "),
	    /* Nested classes */
	    new TestSources("comp { foo = null; }",
			    "comp.foo java.util.Map.Entry",
			    true, ""),
	    new TestSources("comp { foo = null; }",
			    "comp.foo java.util.Map$Entry",
			    true, ""),
	    /* $data */
	    new TestSources("comp {\n" +
			    "    a = $data;\n" +
			    "    b = a;\n" +
			    "    c1 = b;\n" +
			    "    static c2 =\n" +
			    "        a;\n" +
			    "}",
			    null, false,
			    "problem obtaining actual type of entry ")
	};

	final String config;
	final String entries;
	final String output;

	public static void main(String[] args) {
	    test(localtests);
	}

	TestSources(String config,
		    String entries,
		    boolean shouldBe,
		    String output)
	{
	    super("\n  Config: '" + config + "'" +
		  "\n  Entries: " +
		  (entries == null ? "[none]" : "'" + entries + "'"),
		  Boolean.valueOf(shouldBe));
	    this.config = config;
	    this.entries = entries;
	    this.output = output;
	}

	public Object run() {
	    ByteArrayOutputStream baos = new ByteArrayOutputStream();
	    try {
		write(config, classes + "config");
		if (entries != null) {
		    write(entries, classes + "entries");
		}
		String[] noEntries = { classes + "config" };
		String[] withEntries = {
		    "-entries", classes + "entries", classes + "config" };
		String[] args = entries == null ? noEntries : withEntries;
		System.setErr(new PrintStream(baos));
		invokeMain(args);
		System.err.flush();
		return "true, " + baos;
	    } catch (SecurityException e) {
		System.err.flush();
		return "false, " + baos;
	    } catch (IOException e) {
		throw unexpectedException(e);
	    } finally {
		System.setErr(systemErr);
	    }
	}

	private static void write(String value, String file)
	    throws IOException
	{
	    Writer out = new FileWriter(file);
	    out.write(value);
	    out.flush();
	    out.close();
	}

	public void check(Object result) {
	    boolean compareTo = Boolean.TRUE.equals(getCompareTo());
	    String string = (String) result;
	    String systemInResult = systemInResult();
	    debugPrint(30, "  System.in result: " + systemInResult);
	    if (compareTo) {
		if (!string.equals("true, ")) {
		    throw new FailedException("Should be '" + compareTo + "'");
		} else if (!systemInResult.equals("true, ")) {
		    throw new FailedException(
			"Different result for System.in: '" + systemInResult +
			"'");
		}
	    } else {
		String start = "false, " + this.output;
		if (!string.startsWith(start)) {
		    throw new FailedException(
			"Should start with '" + start + "'");
		} else if (!systemInResult.startsWith(start)) {
		    throw new FailedException(
			"Result for System.in should start with '" + start +
			"': '" + systemInResult + "'");
		}
	    }
	}

	private String systemInResult() {
	    ByteArrayOutputStream baos = new ByteArrayOutputStream();
	    InputStream systemIn = System.in;
	    try {
		String[] noEntries = { "-stdin" };
		String[] withEntries = {
		    "-entries", classes + "entries", "-stdin" };
		String[] args = entries == null ? noEntries : withEntries;
		System.setErr(new PrintStream(baos));
		System.setIn(
		    new BufferedInputStream(
			new FileInputStream(classes + "config")));
		invokeMain(args);
		System.err.flush();
		return "true, " + baos;
	    } catch (SecurityException e) {
		System.err.flush();
		return "false, " + baos;
	    } catch (IOException e) {
		throw unexpectedException(e);
	    } finally {
		System.setErr(systemErr);
		if (System.in != systemIn) {
		    try {
			System.in.close();
		    } catch (IOException e) {
		    }
		}
		System.setIn(systemIn);
	    }
	}
    }
}
