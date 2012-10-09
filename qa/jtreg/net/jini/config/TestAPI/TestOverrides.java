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
 * @summary Tests configuration file overrides
 * 
 * @library ../../../../unittestlib
 * @build UnitTestUtilities BasicTest Test
 * @run main/othervm TestOverrides
 */

import java.io.StringReader;
import java.util.Arrays;
import net.jini.config.ConfigurationFile;

/** Tests ConfigurationFile overrides. */
public class TestOverrides extends BasicTest {

    static final String DAVIS_PACKAGE = "net.jini";

    static Test[] tests = {
	/* Syntax errors */
	new TestOverrides("",
			  "",
			  "ConfigurationException: Override 1: Line 1: " +
			  "expected keyword 'static' or 'private', " +
			  "or fully qualified entry name, found end of file"),
	new TestOverrides("",
			  "a",
			  "ConfigurationException: Override 1: Line 1: " +
			  "expected fully qualified entry name, found 'a'"),
	new TestOverrides("",
			  "int",
			  "ConfigurationException: Override 1: Line 1: " +
			  "expected fully qualified entry name, found 'int'"),
	new TestOverrides("",
			  "void",
			  "ConfigurationException: Override 1: Line 1: " +
			  "expected fully qualified entry name, found 'void'"),
	new TestOverrides("",
			  "a.",
			  "ConfigurationException: Override 1: Line 1: " +
			  "expected fully qualified entry name, found 'a.'"),
	new TestOverrides("",
			  ".a",
			  "ConfigurationException: Override 1: Line 1: " +
			  "expected fully qualified entry name, found '.a'"),
	new TestOverrides("",
			  "a.b.",
			  "ConfigurationException: Override 1: Line 1: " +
			  "expected fully qualified entry name, found 'a.b.'"),
	new TestOverrides("",
			  "a.b",
			  "ConfigurationException: Override 1: Line 1: " +
			  "expected '=', found end of file"),
	new TestOverrides("",
			  "a.b;",
			  "ConfigurationException: Override 1: Line 1: " +
			  "expected '=', found ';'"),
	new TestOverrides("",
			  "a.b =",
			  "ConfigurationException: Override 1: Line 1: " +
			  "expected expression, found end of file"),
	new TestOverrides("",
			  "a.b = &yikes!",
			  "ConfigurationException: Override 1: Line 1: " +
			  "expected expression, found '&'"),
	new TestOverrides("",
			  "a.b = ;",
			  "ConfigurationException: Override 1: Line 1: " +
			  "expected expression, found ';'"),
	new TestOverrides("",
			  "a.b = b",
			  "ConfigurationException: Override 1: Line 1: " +
			  "entry with circular reference: a.b"),
	new TestOverrides("",
			  "a.b = c",
			  "ConfigurationException: Override 1: Line 1: " +
			  "entry not found: c"),
	new TestOverrides("",
			  "a.b = b.c",
			  "ConfigurationException: Override 1: Line 1: " +
			  "entry or field not found: b.c"),
	new TestOverrides("",
			  "a.b = 33;",
			  "ConfigurationException: Override 1: Line 1: " +
			  "expected no more characters, found ';'"),
	new TestOverrides("",
			  "private private a.b = 33",
			  "ConfigurationException: Override 1: Line 1: " +
			  "duplicate 'private'"),
	new TestOverrides("",
			  "private static private a.b = 33",
			  "ConfigurationException: Override 1: Line 1: " +
			  "duplicate 'private'"),
	new TestOverrides("",
			  "static static a.b = 33",
			  "ConfigurationException: Override 1: Line 1: " +
			  "duplicate 'static'"),
	new TestOverrides("",
			  "static private static a.b = 33",
			  "ConfigurationException: Override 1: Line 1: " +
			  "duplicate 'static'"),
	new TestOverrides("",
			  "a.b = new String(6)",
			  "ConfigurationException: Override 1: Line 1: " +
			  "no public constructor found: java.lang.String(int)"),
	new TestOverrides("",
			  "a.b = new java.util.HashSet(null)",
			  "ConfigurationException: Override 1: Line 1: " +
			  "problem invoking constructor " +
			  "for java.util.HashSet; caused by:\n\t" +
			  "java.lang.NullPointerException"),
	new TestOverrides("",
			  "a.b=33", "c.d=???",
			  "ConfigurationException: Override 2: Line 1: " +
			  "expected expression, found '?'"),
	new TestOverrides("",
			  "a.b=33", "a.b=44",
			  "ConfigurationException: Override 2: Line 1: " +
			  "duplicate override: a.b"),
	new TestOverrides("",
			  "a.b=\\u",
			  "ConfigurationException: " +
			  "problem reading configuration file; caused by:\n\t" +
			  "java.io.IOException: illegal Unicode escape: \\u"),
	new TestOverrides("",
			  "a.b=\\unope",
			  "ConfigurationException: " +
			  "problem reading configuration file; caused by:\n\t" +
			  "java.io.IOException: illegal Unicode escape: " +
			  "\\unope"),
	new TestOverrides("a { b = hello.there(); }",
			  "a.c = 33",
			  "ConfigurationException: Line 1: " +
			  "declaring class: hello, for method: hello.there" +
			  " was not found"),
	new TestOverrides("a {\n b = Integer.yikes(); \n}",
			  new String[] { "source", "a.c = 33" },
			  "ConfigurationException: source:2: " +
			  "no applicable public static method found: " +
			  "java.lang.Integer.yikes()"),
	new TestOverrides("",
			  new String[] { "source",  "a.c = \nnew foo.bar()" },
			  "ConfigurationException: Override 1: Line 2: " +
			  "class not found: foo.bar"),
	new TestOverrides("",
			  "a.b = 33",
			  "a.d = Integer.nope",
			  "ConfigurationException: Override 2: Line 1: " +
			  "nope is not a public field of class Integer"),
	new TestOverrides("",
			  "a.b = \nInteger.yikes()",
			  "ConfigurationException: Override 1: Line 2: " +
			  "no applicable public static method found: " +
			  "java.lang.Integer.yikes()"),
	new TestOverrides("",
			  "a.b = new Integer(null, null)",
			  "ConfigurationException: Override 1: Line 1: " +
			  "no public constructor found: " +
			  "java.lang.Integer(null, null)"),
	new TestOverrides("",
			  "a.x = ---",
			  "a.y = +++",
			  "ConfigurationException: Override 1: Line 1: " +
			  "bad numeric literal: ---"),

	/* Correct */
	new TestOverrides("",
			  new String[0],
			  ""),
	new TestOverrides("",
			  new String[] { "-" },
			  ""),
	new TestOverrides("",
			  "static a.b = 33",
			  "a.b = 33;"),
	new TestOverrides("",
			  "static a.b = Integer.MAX_VALUE " +
			  "/* Ignore this comment */",
			  "a.b = 2147483647;"),
	new TestOverrides("",
			  "static a.b = new Long(33) // Ignore this comment",
			  "a.b = 33;"),
	new TestOverrides("a { c = b; }",
			  "private a.b = 33",
			  "a.c = 33;"),
	new TestOverrides("a { c = b; }",
			  "private static a.b = 33",
			  "a.c = 33;"),
	new TestOverrides("a { c = b; }",
			  "static private a.b = 33",
			  "a.c = 33;"),
	new TestOverrides("",
			  "a.b = 33",
			  "a.b = 33;"),
	new TestOverrides("comp { a = 3; }",
			  "comp.b = 4",
			  "comp.a = 3; comp.b = 4;"),
	new TestOverrides("import java.util.*;",
			  "a.b = Set.class",
			  "a.b = interface java.util.Set;"),
	new TestOverrides("a { b = 3; }",
			  "a.b = 4",
			  "a.b = 4;"),
	new TestOverrides("a { b = c; }",
			  "private a.c = 4",
			  "a.b = 4;"),
	new TestOverrides("a { b = x; }",
			  "private a.b = 4",
			  ""),
	new TestOverrides("a { b = c; private c = 3; }",
			  "a.c = 3",
			  "a.b = 3; a.c = 3;"),
	new TestOverrides("",
			  "a.b=new\\u0020Integer(\\u00221234\\u0022)",
			  "a.b = 1234;")
    };

    private final String source;
    private final String[] options;
    private final String result;

    public static void main(String[] args) {
	test(tests);
    }

    private TestOverrides(String source,
			  String override,
			  String result)
    {
	this(source, new String[] { "-", override }, result);
    }

    private TestOverrides(String source,
			  String override1,
			  String override2,
			  String result)
    {
	this(source, new String[] { "-", override1, override2 }, result);
    }

    private TestOverrides(String source,
			  String[] options,
			  String result)
    {
	super("source = \"" + source + "\"" +
	      ", options = " + toString(options),
	      result);
	this.source = source;
	this.options = options;
	this.result = result;
    }

    public Object run() {
	StringBuffer result = new StringBuffer();
	try {
	    ConfigurationFile config =
		new ConfigurationFile(new StringReader(source), options);
	    Object[] entryNames = config.getEntryNames().toArray();
	    Arrays.sort(entryNames);
	    for (int i = 0; i < entryNames.length; i++) {
		String fullName = (String) entryNames[i];
		int dot = fullName.lastIndexOf('.');
		String component = fullName.substring(0, dot);
		String name = fullName.substring(dot + 1);
		Class type = config.getEntryType(component, name);
		if (type == null) {
		    type = Object.class;
		}
		if (i > 0) {
		    result.append(' ');
		}
		result.append(fullName).append(" = ");
		result.append(config.getEntry(component, name, type));
		result.append(";");
	    }
	} catch (Throwable t) {
	    result.setLength(0);
	    while (true) {
		String className = t.getClass().getName();
		if (className.startsWith(DAVIS_PACKAGE)) {
		    className = className.substring(
			className.lastIndexOf('.') + 1);
		}
		result.append(className);
		if (testLevel >= 30) {
		    debugPrint(30, getStackTrace(t));
		} 
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
	}
	return result.toString();
    }
}
