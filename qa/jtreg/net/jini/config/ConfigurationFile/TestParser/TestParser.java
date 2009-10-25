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
/*
 * @test 
 * @summary Tests parsing configuration files.
 * @author Tim Blackman
 * @build TestParser MultiReader X Y TestPermission
 * @build a.X a.Y a.Z a.U
 * @build a.b.X a.b.Y a.b.Z a.b.U a.b.V
 * @run shell classpath.sh shell run-java.sh TestParser
 */

import java.io.File;
import java.io.FileReader;
import java.io.LineNumberReader;
import java.io.PrintStream;
import java.lang.reflect.Array;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.Properties;
import net.jini.config.ConfigurationFile;
import net.jini.id.Uuid;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.ServerEndpoint;

/** Tests the ConfigurationFile parser. */
public class TestParser {
    static final String DAVIS_PACKAGE = "net.jini";
    static boolean verbose = false;
    static int first = 1;
    static int last = Integer.MAX_VALUE;
    static int testNum = 0;
    static boolean stopOnFail;
    static boolean printStack;
    static int passed = 0;
    static int failed = 0;

    /**
     * Tests the ConfigurationFile parser.
     *
     * Tests specify test data files in the following format:
     *
     * Lines that begin with a '#' are treated as comments; lines that begin
     * with '$' are special EOF markers to mark the end of a multi-line entry.
     * 
     * A data file is made up of a series of tests. For each test, the lines up
     * to the first EOF are the contents of the configuration file, and the
     * lines up to the second EOF are the string value of the exception or the
     * return value.
     *
     * @param args controls the test.  The arguments consist of the name of the
     *	      data file, optionally preceded by the following flags:
     *
     *        -verbose -- print verbose output (default)
     *        -quiet   -- no verbose output
     *	      -printStack -- print stack trace for unexpected exceptions
     *	      -first   -- number of the first test to perform,
     *			  counting separate tests in data files
     *	      -last    -- number of the last test to perform,
     *			  counting separate tests in data files
     *	      -stopOnFail -- stop on first failure
     */
    public static void main(String[] args) {
	/* Make sure properties used in the configuration file are set. */
	Properties props = System.getProperties();
	String src = props.getProperty("test.src");
	if (src == null) {
	    src = ".";
	    props.setProperty("test.src", ".");
	}
	if (props.getProperty("keystore") == null) {
	    props.setProperty("keystore", src + File.separator + "keystore");
	}
	if (props.getProperty("alias") == null) {
	    props.setProperty("alias", "client");
	}
	if (props.getProperty("java.security.properties") == null) {
	    props.setProperty("java.security.properties",
			      src + File.separator + "security.properties");
	}
	/*
	 * Set line.separator to a standard value so that printing results
	 * will compare on all platforms.
	 */
	props.setProperty("line.separator", "\n");
	/* Initialize security manager */
	if (props.getProperty("java.security.policy") == null) {
	    props.setProperty(
		"java.security.policy", src + File.separator + "policy");
	}
	if (System.getSecurityManager() == null) {
	    System.setSecurityManager(new SecurityManager());
	}
	int i;
	for (i = 0; i < args.length; i++) {
	    String arg = args[i];
	    if (arg.equals("-verbose")) {
		verbose = true;
	    } else if (arg.equals("-quiet")) {
		verbose = false;
	    } else if (arg.equals("-first")) {
		first = Integer.parseInt(args[++i]);
	    } else if (arg.equals("-last")) {
		last = Integer.parseInt(args[++i]);
	    } else if (arg.toLowerCase().startsWith("-stop")) {
		stopOnFail = true;
	    } else if (arg.toLowerCase().startsWith("-print")) {
		printStack = true;
	    } else {
		break;
	    }
	}
	long start = System.currentTimeMillis();
	if (i == args.length) {
	    test(src + File.separator + "parser-test");
	} else {
	    for ( ; i < args.length; i++) {
		test(args[i]);
	    }
	}
	long time = System.currentTimeMillis() - start;
	if (failed == 0) {
	    System.err.println(passed + " tests passed in " + time + " ms");
	} else {
	    throw new RuntimeException(
		failed + " tests failed in " + time + " ms");
	}
    }

    /** Runs the tests in the specified data file. */
    static void test(String file) {
	LineNumberReader fileReader = null;
	try {
	    fileReader = new LineNumberReader(new FileReader(file));
	    MultiReader reader = new MultiReader(fileReader);
	    while (true) {
		if (stopOnFail && failed > 0) {
		    return;
		} else if (reader.atRealEOF()) {
		    return;
		}
		testNum++;
		int lineno = fileReader.getLineNumber();
		if (testNum < first || testNum > last) {
		    reader.readToEOF();
		    reader.readToEOF();
		    continue;
		}

		ConfigurationFile config = null;
		Throwable throwable = null;
		StringBuffer result = new StringBuffer();
		try {
		    config = new ConfigurationFile(reader, new String[0]);
		    Object[] entryNames = config.getEntryNames().toArray();
		    Arrays.sort(entryNames);
		    String lastComponent = null;
		    for (int i = 0; i < entryNames.length; i++) {
			String fullName = (String) entryNames[i];
			int dot = fullName.lastIndexOf('.');
			String component = fullName.substring(0, dot);
			String name = fullName.substring(dot + 1);
			Class type = config.getEntryType(component, name);
			if (type == null) {
			    type = Object.class;
			}
			if (!component.equals(lastComponent)) {
			    if (lastComponent != null) {
				result.append("}\n");
			    }
			    result.append(component).append(" {\n");
			    lastComponent = component;
			}
			result.append("    ").append(name).append(" = ");
			result.append(
			    toString(config.getEntry(component, name, type)));
			result.append(";\n");
		    }
		    if (entryNames.length > 0) {
			result.append("}\n");
		    }
		} catch (Throwable t) {
		    throwable = t;
		    result.setLength(0);
		    while (true) {
			String className = t.getClass().getName();
			if (className.startsWith(DAVIS_PACKAGE)) {
			    className = className.substring(
				className.lastIndexOf('.') + 1);
			}
			result.append(className);
			String message = t.getMessage();
			if (message != null) {
			    result.append(": ").append(message);
			}
			t = t.getCause();
			if (t == null) {
			    break;
			}
			result.append("; caused by:\n\t");
		    }
		    result.append('\n');
		}
		reader.clearEOF();
		String expect = reader.readToEOF();
		boolean ok = checkResult(result.toString(), expect);
		if (ok) {
		    passed++;
		    if (verbose) {
			System.err.print("Test " + testNum +
					 ", line " + lineno + ":" + result);
			System.err.flush();
		    }
		} else {
		    failed++;
		    System.err.print(file + ":" + lineno +
				     ": Test " + testNum +
				     ":\nWrong result:\n" + result +
				     "Expected:\n" + expect);
		    System.err.flush();
		    if (throwable != null && printStack) {
			System.err.print("Stack trace: ");
			throwable.printStackTrace();
		    }
		}
	    }
	} catch (Exception e) {
	    System.err.println(file + ":" +
			       (fileReader != null
				? fileReader.getLineNumber() + ":" : ""));
	    e.printStackTrace();
	    return;
	}
    }

    /**
     * Returns true if the result matches the expected value.  The two Strings
     * must either be identical or, if the expected value ends with '...', then
     * any characters from that point on in the result are permitted.
     */
    static boolean checkResult(String result, String expected) {
	if (expected.equals(result)) {
	    return true;
	} else {
	    /*
	     * If expected ends with '...', then return true if everything
	     * before that matches the start of result.
	     */
	    return expected.endsWith("...\n")
		&& result.regionMatches(
		    0, expected, 0, expected.length() - 4);
	}
    }

    /** Returns a String version of the Object for comparision in tests. */
    static String toString(Object object) {
	if (object == null) {
	    return "null";
	} else if (object instanceof ConfigurationFile
		   || object instanceof ServerEndpoint
		   || object instanceof KeyStore
		   || object instanceof PrintStream
		   || object instanceof ClassLoader)
	{
	    String className = object.getClass().getName();
	    return className.substring(className.lastIndexOf('.') + 1);
	} else if (object instanceof BasicILFactory) {
	    BasicILFactory factory = (BasicILFactory) object;
	    Class permClass = factory.getPermissionClass();
	    return "BasicILFactory[" +
		factory.getServerConstraints() +
		(permClass != null ? ", " + permClass.getName() : "") +
		"]";
	} else if (object instanceof Uuid) {
	    return "Uuid";
	} else if (object instanceof BasicJeriExporter) {
	    BasicJeriExporter exporter = (BasicJeriExporter) object;
	    return
		"BasicJeriExporter {" +
		"\n\tendpoint: " + toString(exporter.getServerEndpoint()) +
		"\n\tilFactory: " +
		toString(exporter.getInvocationLayerFactory()) +
		"\n\tenableDGC: " + exporter.getEnableDGC() +
		"\n\tkeepAlive: " + exporter.getKeepAlive() +
		"\n\tobjID: " + toString(exporter.getObjectIdentifier()) +
		"\n    }";
	} else if (object.getClass().isArray()) {
	    Class c = object.getClass();
	    int dimensions = 0;
	    while (c.isArray()) {
		dimensions++;
		c = c.getComponentType();
	    }
	    StringBuffer sb = new StringBuffer();
	    sb.append(c.getName()).append("[] {");
	    int length = Array.getLength(object);
	    for (int i = 0; i < length; i++) {
		if (i != 0) {
		    sb.append(',');
		}
		sb.append(' ').append(toString(Array.get(object, i)));
	    }
	    sb.append(" }");
	    return sb.toString();
	} else {
	    return object.toString();
	}
    }
}
