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
 * @summary Tests ConfigurationProvider.getInstance
 * @author Tim Blackman
 * @library ../../../../unittestlib
 * @build UnitTestUtilities BasicTest Test
 * @run main/othervm/policy=policy TestGetInstance
 */

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;
import net.jini.config.AbstractConfiguration;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.config.ConfigurationProvider;
import net.jini.config.ConfigurationFile;
import net.jini.config.ConfigurationNotFoundException;

public class TestGetInstance extends BasicTest {

    static final String src =
	System.getProperty("test.src", ".") + File.separator;
    static final String classes =
	System.getProperty("test.classes", "classes") + File.separator;

    static {
	if (System.getProperty("java.security.policy") == null) {
	    System.setProperty("java.security.policy", src + "policy");
	}
	if (System.getSecurityManager() == null) {
	    System.setSecurityManager(new SecurityManager());
	}
    }

    static class NonPublicClass extends AbstractConfiguration {
	public NonPublicClass(String[] options, ClassLoader cl) { }
	public Object getEntryInternal(String c, String n, Class t, Object d) {
	    return null;
	}
    }

    public static class NonPublicConstructor extends AbstractConfiguration {
	NonPublicConstructor(String[] options, ClassLoader cl) { }
	public Object getEntryInternal(String c, String n, Class t, Object d) {
	    return null;
	}
    }

    public static class WrongConstructor extends AbstractConfiguration {
	public WrongConstructor(String[] options) { }
	public Object getEntryInternal(String c, String n, Class t, Object d) {
	    return null;
	}
    }

    public static abstract class Abstract extends AbstractConfiguration {
	public Abstract(String[] options , ClassLoader cl) { }
	public Object getEntryInternal(String c, String n, Class t, Object d) {
	    return null;
	}
    }

    static class NonPublicOuterClass {
	public static class Inside extends AbstractConfiguration {
	    public Inside(String[] options, ClassLoader cl) { }
	    public Object getEntryInternal(
		String c, String n, Class t, Object d)
	    {
		return null;
	    }
	}
    }

    public static class OK extends AbstractConfiguration {
	public OK(String[] options, ClassLoader cl) { }
	public Object getEntryInternal(String c, String n, Class t, Object d) {
	    return null;
	}
    }

    static Test[] tests = {

	new TestGetInstance(null, ConfigurationFile.class),
	new TestGetInstance(new String[0], ConfigurationFile.class),
	new TestGetInstance(sa("-"), ConfigurationFile.class),	
	new TestGetInstance(sa(null),
			    new ConfigurationException("option is null")),
	new TestGetInstance(sa(""), ConfigurationNotFoundException.class),
	new TestGetInstance(sa(src + "config"), ConfigurationFile.class),
	new TestGetInstance(
	    sa("-", ""),
	    new ConfigurationException(
		"Override 1: Line 1: " +
		"expected keyword 'static' or 'private', " +
		"or fully qualified entry name, found end of file")),
	new TestGetInstance(
	    sa("-", "a"),
	    new ConfigurationException(
		"Override 1: Line 1: " +
		"expected fully qualified entry name, found 'a'")),
	new TestGetInstance(
	    sa("-", "a.b"),
	    new ConfigurationException(
		"Override 1: Line 1: " +
		"expected '=', found end of file")),
	new TestGetInstance(
	    sa("-", "static a.b = $data"),
	    new ConfigurationException(
		"Override 1: Line 1: " +
		"cannot use '$data' in a static entry")),
	new TestGetInstance("Null location and classloader", null, null,
			    ConfigurationFile.class),
	new TestGetInstance("Null classloader", sa(src + "config"), null,
			    ConfigurationFile.class),
	new TestGetInstance(
	    "No resource, null location", null,
	    new FileClassLoader(new HashMap()), ConfigurationFile.class),
	new TestGetInstance(
	    "No access to location", sa("http://localhost:7/bar.html"), null,
	    ConfigurationException.class),
	new TestGetInstance(
	    "No resource", sa(src + "config"),
	    new FileClassLoader(new HashMap()),
	    ConfigurationFile.class),
	new TestGetInstance(
	    "Empty resource", null, new FileClassLoader(providerResource("")),
	    ConfigurationException.class),
	new TestGetInstance(
	    "Two items in resource", null,
	    new FileClassLoader(providerResource("a\nb")),
	    ConfigurationException.class),
	new TestGetInstance(
	    "Unknown class in resource", null,
	    new FileClassLoader(providerResource("UnknownClass")),
	    ConfigurationException.class),
	new TestGetInstance(
	    "Non-public class in resource", null,
	    new FileClassLoader(
		providerResource("TestGetInstance$NonPublicClass")),
	    ConfigurationException.class),
	new TestGetInstance(
	    "Class with non-public constructor in resource", null,
	    new FileClassLoader(
		providerResource("TestGetInstance$NonPublicConstructor")),
	    ConfigurationException.class),
	new TestGetInstance(
	    "Class with wrong constructor in resource", null,
	    new FileClassLoader(
		providerResource("TestGetInstance$WrongConstructor")),
	    ConfigurationException.class),
	new TestGetInstance(
	    "Abstract class in resource", null,
	    new FileClassLoader(providerResource("TestGetInstance$Abstract")),
	    ConfigurationException.class),

	/*
	 * It appears to be OK to create an instance of a public class nested
	 * inside a non-public class -- reflection permits it.
	 * -tjb[18.May.2001]
	 */
	new TestGetInstance(
	    "Class in resource is defined in a non-public class", null,
	    new FileClassLoader(
		providerResource(
		    "TestGetInstance$NonPublicOuterClass$Inside")),
	    NonPublicOuterClass.Inside.class),

	new TestGetInstance(
	    "Class in resource doesn't implement Configuration", null,
	    new FileClassLoader(providerResource("java.lang.Integer")),
	    ConfigurationException.class),
	new TestGetInstance(
	    "Class in resource is OK", null,
	    new FileClassLoader(
		providerResource(
		    "#\n" +
		    "  TestGetInstance$OK # a b c\n" +
		    "#\n")),
	    OK.class),
	new TestGetInstance(
	    "Find most specific resource", null,
	    new FileClassLoader(
		providerResource("TestGetInstance$OK"),
		new FileClassLoader(
		    providerResource("java.lang.Integer"))),
	    OK.class),
	new TestGetInstance(
	    "Pass classloader to default provider", sa(src + "config"),
	    new FileClassLoader(
		createMap(
		    new String[] {
			"ClassLoaderDefined.class",
			src + "ClassLoaderDefined1class"
		    })),
	    ConfigurationFile.class)
	{
	    public void check(Object result) throws Exception {
		super.check(result);
		Configuration config = (Configuration) result;
		Object value =
		    config.getEntry(
			"comp", "classLoaderDefinedEntry", Object.class);
		debugPrint(30, "value: " + value);
		if (!"ClassLoaderDefined1".equals(value)) {
		    throw new FailedException(
			"Wrong value: " + value +
			", expected: ClassLoaderDefined1");
		}
	    }
	},
	new TestGetInstance("Use null context class loader",
			    sa(src + "config"), null, ConfigurationFile.class)
	{
	    public Object run() {
		Thread thread = Thread.currentThread();
		ClassLoader ccl = thread.getContextClassLoader();
		try {
		    thread.setContextClassLoader(null);
		    return super.run();
		} finally {
		    thread.setContextClassLoader(ccl);
		}
	    }
	}
    };

    private final String[] options;
    private boolean clSupplied;
    private ClassLoader cl;

    public static void main(String[] args) {
	test(tests);
    }

    private TestGetInstance(String[] options, Object result) {
	super(toString(options), result);
	this.options = options;
    }

    private TestGetInstance(String name,
			    String[] options,
			    ClassLoader cl,
			    Object result)
    {
	super(name, result);
	this.options = options;
	clSupplied = true;
	this.cl = cl;
    }

    public Object run() {
	try {
	    if (clSupplied) {
		return ConfigurationProvider.getInstance(options, cl);
	    } else {
		return ConfigurationProvider.getInstance(options);
	    }
	} catch (Exception e) {
	    return e;
	}
    }

    public void check(Object result) throws Exception {
	Object compareTo = getCompareTo();
	if ((result != null && compareTo == result.getClass()) ||
	    (compareTo instanceof Throwable &&
	     result instanceof Throwable &&
	     safeEquals(((Throwable) compareTo).getMessage(),
			((Throwable) result).getMessage())))
	{
	    return;
	}
	throw new FailedException("Should be: " + compareTo);
    }

    /**
     * Returns a map that maps the resource name for configuration providers
     * to a temp file containing the specified contents.
     */
    static Map providerResource(String contents) {
	Writer out = null;
	try {
	    File temp = File.createTempFile("tst", null, new File(classes));
	    temp.deleteOnExit();
	    out = new FileWriter(temp);
	    out.write(contents);
	    Map map = new HashMap(2);
	    map.put("META-INF/services/" +
		    "net.jini.config.Configuration",
		    temp.getAbsolutePath());
	    return map;
	} catch (IOException e) {
	    throw unexpectedException(e);
	} finally {
	    if (out != null) {
		try {
		    out.close();
		} catch (IOException e) {
		}
	    }
	}
    }

    static Map createMap(String[] resources) {
	Map map = new HashMap(resources.length / 2);
	for (int i = 0; i < resources.length; i += 2) {
	    map.put(resources[i], resources[i + 1]);
	}
	return map;
    }

    static String[] sa(String s) {
	return new String[] { s };
    }

    static String[] sa(String s1, String s2) {
	return new String[] { s1, s2 };
    }
}
