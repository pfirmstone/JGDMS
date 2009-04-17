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
 * @summary Tests ConfigurationFile.getEntry
 * @author Tim Blackman
 * @library ../../../../unittestlib
 * @build UnitTestUtilities BasicTest Test TestGetEntryType
 * @run shell run-java.sh TestGetEntryType
 */

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.jini.config.ConfigurationException;
import net.jini.config.ConfigurationFile;
import net.jini.config.NoSuchEntryException;

public class TestGetEntryType extends BasicTest {

    static final String src =
	System.getProperty("test.src", ".") + File.separator;

    static final String location = src + "config";

    static {
	if (System.getProperty("java.security.policy") == null) {
	    System.setProperty("java.security.policy", src + "policy");
	}
	if (System.getProperty("java.security.properties") == null) {
	    System.setProperty("java.security.properties",
			       src + File.separator + "security.properties");
	}
	if (System.getSecurityManager() == null) {
	    System.setSecurityManager(new SecurityManager());
	}
    }

    /** Test using an override value */
    public static class TestGetEntryTypeOverride extends TestGetEntryType {
	final String value;

	TestGetEntryTypeOverride(String value, Object shouldBe) {
	    super(value, "comp", "a", shouldBe);
	    this.value = value;
	}

	ConfigurationFile getConfig() throws ConfigurationException {
	    return new ConfigurationFile(
		new String[] { "-", "comp.a = " + value });
	}
    }

    static Test[] tests = {

	new TestGetEntryType("comp", "booleanEntry", boolean.class),

	new TestGetEntryType("comp", "byteEntry", byte.class),
	new TestGetEntryType("comp", "byteEntry3", byte.class),
	new TestGetEntryType("comp", "byteConstant3", byte.class),
	new TestGetEntryType("comp", "byteConstantNeg3", byte.class),

	new TestGetEntryType("comp", "charEntry", char.class),
	new TestGetEntryType("comp", "charConstant3", char.class),
	new TestGetEntryType("comp", "charConstant300", char.class),
	new TestGetEntryType("comp", "charConstantFFFF", char.class),

	new TestGetEntryType("comp", "shortEntry", short.class),
	new TestGetEntryType("comp", "shortEntry3", short.class),
	new TestGetEntryType("comp", "shortConstant3", short.class),
	new TestGetEntryType("comp", "shortConstantNeg3", short.class),
	new TestGetEntryType("comp", "shortConstant300", short.class),

	new TestGetEntryType("comp", "intEntry", int.class),
	new TestGetEntryType("comp", "intConstant3", int.class),
	new TestGetEntryType("comp", "intConstantNeg3", int.class),
	new TestGetEntryType("comp", "intConstant300", int.class),
	new TestGetEntryType("comp", "intConstantFFFF", int.class),
	new TestGetEntryType("comp", "intConstant100K", int.class),
	new TestGetEntryType("comp", "intConstantNeg100K", int.class),

	new TestGetEntryType("comp", "longEntry", long.class),
	new TestGetEntryType("comp", "longConstant", long.class),

	new TestGetEntryType("comp", "floatEntry", float.class),
	new TestGetEntryType("comp", "floatConstant", float.class),

	new TestGetEntryType("comp", "doubleEntry", double.class),
	new TestGetEntryType("comp", "doubleConstant", double.class),

	new TestGetEntryType("comp", "nullEntry", null),

	new TestGetEntryType("comp", "BooleanEntry", Boolean.class),

	new TestGetEntryType("comp", "ByteEntry", Byte.class),

	new TestGetEntryType("comp", "CharacterEntry", Character.class),

	new TestGetEntryType("comp", "ShortEntry", Short.class),

	new TestGetEntryType("comp", "IntegerEntry", Integer.class),

	new TestGetEntryType("comp", "LongEntry", Long.class),

	new TestGetEntryType("comp", "FloatEntry", Float.class),

	new TestGetEntryType("comp", "DoubleEntry", Double.class),

	new TestGetEntryType("comp", "ArrayEntry", Object[].class),

	new TestGetEntryType("comp", "privateEntry",
			     NoSuchEntryException.class),
	new TestGetEntryType("comp", "staticBadEntry",
			     ConfigurationException.class),
	new TestGetEntryType("comp", "badEntry", ConfigurationException.class),

	/* Find class in classloader */
	new TestGetEntryType(
	    new FileClassLoader(
		    createMap(
			new String[] {
			    "ClassLoaderDefined.class",
			    src + "ClassLoaderDefined1class"
			})),
	    "comp", "classLoaderDefinedEntry", String.class),

	/* Find class in parent classloader */
	new TestGetEntryType(
	    new FileClassLoader(
		createMap(
		    new String[] {
			"ClassLoaderDefined.class",
			src + "ClassLoaderDefined1class"
		    }),
		new FileClassLoader(
		    createMap(
			new String[] {
			    "ClassLoaderDefined.class",
			    src + "ClassLoaderDefined2class"
			}))),
	    "comp", "classLoaderDefinedEntry", String.class),

	/* Test with no entries */
	new TestGetEntryType("comp", "notFound", NoSuchEntryException.class) {
	    ConfigurationFile getConfig() throws ConfigurationException {
		return new ConfigurationFile(null);
	    }
	},

	/* Test dynamic versus compile-time types */
	new TestGetEntryTypeOverride("System.getProperty(\"nullValue\")",
				     String.class),
	new TestGetEntryTypeOverride("(java.util.Set) new java.util.HashSet()",
				     Set.class),
	new TestGetEntryTypeOverride(
	    "TestGetEntryType.getNumber(new Integer(0))", Number.class),
	new TestGetEntryTypeOverride("Integer.parseInt(\"blah\")", int.class)
    };

    private static ConfigurationFile defaultConfig;

    private final String component;
    private final String name;
    private boolean clSpecified;
    private ClassLoader cl;

    public static void main(String[] args) {
	test(tests);
    }

    private TestGetEntryType(String component,
			     String name,
			     Object shouldBe) {
	this("", component, name, shouldBe);
    }

    private TestGetEntryType(ClassLoader cl,
			     String component,
			     String name,
			     Object shouldBe)
    {
	this(cl + ", ", component, name, shouldBe);
	clSpecified = true;
	this.cl = cl;
    }

    private TestGetEntryType(String testNamePrefix,
			     String component,
			     String name,
			     Object shouldBe) {
	super(testNamePrefix + component + ", " + name, shouldBe);
	this.component = component;
	this.name = name;
    }

    public Object run() {
	try {
	    ConfigurationFile config = getConfig();
	    return config.getEntryType(component, name);
	} catch (Exception e) {
	    return e;
	}
    }

    ConfigurationFile getConfig() throws ConfigurationException {
	return clSpecified
	    ? new ConfigurationFile(new String[]{location}, cl)
	    : getDefaultConfig();
    }

    public void check(Object result) throws Exception {
	Object compareTo = getCompareTo();
	ConfigurationFile config = getConfig();
	if (compareTo instanceof Class &&
	    Exception.class.isAssignableFrom((Class) compareTo) &&
	    result != null)
	{
	    result = result.getClass();
	}
	super.check(result);
    }

    private static ConfigurationFile getDefaultConfig() {
	if (defaultConfig == null) {
	    try {
		defaultConfig = new ConfigurationFile(new String[]{location});
	    } catch (ConfigurationException e) {
		throw unexpectedException(e);
	    }
	}
	return defaultConfig;
    }

    static Map createMap(String[] resources) {
	Map map = new HashMap(resources.length / 2);
	for (int i = 0; i < resources.length; i += 2) {
	    map.put(resources[i], resources[i + 1]);
	}
	return map;
    }

    public static Number getNumber(Number n) {
	return n;
    }
}
