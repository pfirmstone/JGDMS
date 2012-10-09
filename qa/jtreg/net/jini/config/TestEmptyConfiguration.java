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
 * @summary Tests the EmptyConfiguration class
 * 
 * @library ../../../unittestlib
 * @build UnitTestUtilities BasicTest Test
 * @run main TestEmptyConfiguration
 */

import java.io.Serializable;
import net.jini.config.ConfigurationException;
import net.jini.config.Configuration;
import net.jini.config.EmptyConfiguration;
import net.jini.config.NoSuchEntryException;

public class TestEmptyConfiguration extends BasicTest {

    static Test[] tests = {

	new TestEmptyConfiguration(null, null, null,
				   NullPointerException.class),
	new TestEmptyConfiguration(null, null, Object.class,
				   NullPointerException.class),
	new TestEmptyConfiguration(null, "e", Object.class,
				   NullPointerException.class),
	new TestEmptyConfiguration("c", null, Object.class,
				   NullPointerException.class),

	new TestEmptyConfiguration("", "e", Object.class,
				   IllegalArgumentException.class),
	new TestEmptyConfiguration("3", "e", Object.class,
				   IllegalArgumentException.class),
	new TestEmptyConfiguration("a.", "e", Object.class,
				   IllegalArgumentException.class),

	new TestEmptyConfiguration("c", "", Object.class,
				   IllegalArgumentException.class),
	new TestEmptyConfiguration("c", "e!", Object.class,
				   IllegalArgumentException.class),
	new TestEmptyConfiguration("c", "a.b", Object.class,
				   IllegalArgumentException.class),

	new TestEmptyConfiguration("comp", "booleanEntry", boolean.class,
				   NoSuchEntryException.class),
	new TestEmptyConfiguration("comp", "booleanEntry", boolean.class, null,
				   IllegalArgumentException.class),
	new TestEmptyConfiguration("comp", "booleanEntry", boolean.class,
				   new Integer(3),
				   IllegalArgumentException.class),
	new TestEmptyConfiguration("comp", "booleanEntry", boolean.class,
				   Boolean.TRUE, Boolean.TRUE),

	new TestEmptyConfiguration("comp", "BooleanEntry", Boolean.class,
				   NoSuchEntryException.class),
	new TestEmptyConfiguration("comp", "BooleanEntry", Boolean.class, null,
				   null),
	new TestEmptyConfiguration("comp", "BooleanEntry", Boolean.class,
				   new Short((short) 3),
				   IllegalArgumentException.class),
	new TestEmptyConfiguration("comp", "BooleanEntry", Boolean.class,
				   Boolean.TRUE, Boolean.TRUE),

	new TestEmptyConfiguration("comp", "byteEntry", byte.class, null,
				   IllegalArgumentException.class),
	new TestEmptyConfiguration("comp", "byteEntry", byte.class, new Long(3),
				   IllegalArgumentException.class),
	new TestEmptyConfiguration("comp", "byteEntry", byte.class,
				   new Byte((byte) 1), new Byte((byte) 1)),

	new TestEmptyConfiguration("comp", "ByteEntry", Byte.class, null, null),
	new TestEmptyConfiguration("comp", "ByteEntry", Byte.class,
				   new Integer(3),
				   IllegalArgumentException.class),
	new TestEmptyConfiguration("comp", "ByteEntry", Byte.class,
				   new Byte((byte) 1), new Byte((byte) 1)),

	new TestEmptyConfiguration("comp", "charEntry", char.class,
				   null, IllegalArgumentException.class),
	new TestEmptyConfiguration("comp", "charEntry", char.class,
				   new Integer(3),
				   IllegalArgumentException.class),
	new TestEmptyConfiguration("comp", "charEntry", char.class,
				   new Character('a'), new Character('a')),

	new TestEmptyConfiguration("comp", "CharEntry", Character.class, null,
				   null),
	new TestEmptyConfiguration("comp", "CharEntry", Character.class,
				   new Integer(3),
				   IllegalArgumentException.class),
	new TestEmptyConfiguration("comp", "CharEntry", Character.class,
				   new Character('a'), new Character('a')),

	new TestEmptyConfiguration("comp", "shortEntry", short.class,
				   null, IllegalArgumentException.class),
	new TestEmptyConfiguration("comp", "shortEntry", short.class,
				   new Integer(3),
				   IllegalArgumentException.class),
	new TestEmptyConfiguration("comp", "shortEntry", short.class,
				   new Short((short) 3), new Short((short) 3)),

	new TestEmptyConfiguration("comp", "ShortEntry", Short.class, null,
				   null),
	new TestEmptyConfiguration("comp", "ShortEntry", Short.class,
				   new Integer(3),
				   IllegalArgumentException.class),
	new TestEmptyConfiguration("comp", "ShortEntry", Short.class,
				   new Short((short) 3), new Short((short) 3)),

	new TestEmptyConfiguration("comp", "intEntry", int.class, null,
				   IllegalArgumentException.class),
	new TestEmptyConfiguration("comp", "intEntry", int.class, new Long(3),
				   IllegalArgumentException.class),
	new TestEmptyConfiguration("comp", "intEntry", int.class,
				   new Integer(3), new Integer(3)),

	new TestEmptyConfiguration("comp", "IntegerEntry", Integer.class, null,
				   null),
	new TestEmptyConfiguration("comp", "IntegerEntry", Integer.class,
				   new Long(3),
				   IllegalArgumentException.class),
	new TestEmptyConfiguration("comp", "IntegerEntry", Integer.class,
				   new Integer(3), new Integer(3)),

	new TestEmptyConfiguration("comp", "longEntry", long.class, null,
				   IllegalArgumentException.class),
	new TestEmptyConfiguration("comp", "longEntry", long.class,
				   new Integer(3),
				   IllegalArgumentException.class),
	new TestEmptyConfiguration("comp", "longEntry", long.class, new Long(3),
				   new Long(3)),

	new TestEmptyConfiguration("comp", "LongEntry", Long.class, null, null),
	new TestEmptyConfiguration("comp", "LongEntry", Long.class,
				   new Integer(3),
				   IllegalArgumentException.class),
	new TestEmptyConfiguration("comp", "LongEntry", Long.class,
				   new Long(3), new Long(3)),

	new TestEmptyConfiguration("comp", "floatEntry", float.class, null,
				   IllegalArgumentException.class),
	new TestEmptyConfiguration("comp", "floatEntry", float.class,
				   new Integer(3),
				   IllegalArgumentException.class),
	new TestEmptyConfiguration("comp", "floatEntry", float.class,
				   new Float(3.0), new Float(3.0)),

	new TestEmptyConfiguration("comp", "FloatEntry", Float.class, null,
				   null),
	new TestEmptyConfiguration("comp", "FloatEntry", Float.class,
				   new Integer(3),
				   IllegalArgumentException.class),
	new TestEmptyConfiguration("comp", "FloatEntry", Float.class,
				   new Float(3.0), new Float(3.0)),

	new TestEmptyConfiguration("comp", "doubleEntry", double.class, null,
				   IllegalArgumentException.class),
	new TestEmptyConfiguration("comp", "doubleEntry", double.class,
				   new Integer(3),
				   IllegalArgumentException.class),
	new TestEmptyConfiguration("comp", "doubleEntry", double.class,
				   new Double(3.0), new Double(3.0)),

	new TestEmptyConfiguration("comp", "DoubleEntry", Double.class, null,
				   null),
	new TestEmptyConfiguration("comp", "DoubleEntry", Double.class,
				   new Integer(3),
				   IllegalArgumentException.class),
	new TestEmptyConfiguration("comp", "DoubleEntry", Double.class,
				   new Double(3.0), new Double(3.0)),

	new TestEmptyConfiguration("comp", "ObjectEntry", Object.class,
				   new Integer(3), new Integer(3)),

	new TestEmptyConfiguration("comp", "ThreadEntry", Thread.class,
				   new Object(),
				   IllegalArgumentException.class),
	new TestEmptyConfiguration("comp", "ThreadEntry", Thread.class,
				   new Integer(3),
				   IllegalArgumentException.class),

	new TestEmptyConfiguration("comp", "InterfaceEntry", Serializable.class,
				   new Integer(3), new Integer(3)),
	new TestEmptyConfiguration("comp", "InterfaceEntry", Serializable.class,
				   new Thread(),
				   IllegalArgumentException.class)
    };

    private final String component;
    private final String name;
    private final Class type;
    private final boolean hasDefault;
    private final Object defaultValue;

    public static void main(String[] args) {
	test(tests);
    }

    private TestEmptyConfiguration(String component,
				   String name,
				   Class type,
				   Object shouldBe) {
	this(component, name, type, null, false, shouldBe);
    }

    private TestEmptyConfiguration(String component,
				   String name,
				   Class type,
				   Object defaultValue,
				   Object shouldBe)
    {
	this(component, name, type, defaultValue, true, shouldBe);
    }

    private TestEmptyConfiguration(String component,
				   String name,
				   Class type,
				   Object defaultValue,
				   boolean hasDefault,
				   Object shouldBe)
    {
	super(component + ", " + name + ", " +
	      (type == null ? "null" : type.getName()) +
	      (hasDefault ? ", " + defaultValue : ""),
	      shouldBe);
	this.component = component;
	this.name = name;
	this.type = type;
	this.defaultValue = defaultValue;
	this.hasDefault = hasDefault;
    }

    public Object run() {
	try {
	    if (hasDefault) {
		return EmptyConfiguration.INSTANCE.getEntry(
		    component, name, type, defaultValue);
	    } else {
		return EmptyConfiguration.INSTANCE.getEntry(
		    component, name, type);
	    }
	} catch (Exception e) {
	    return e;
	}
    }

    public void check(Object result) throws Exception {
	Object compareTo = getCompareTo();
	if (compareTo instanceof Class) {
	    if (result == null || compareTo != result.getClass()) {
		throw new FailedException(
		    "Should be of type " + compareTo);
	    }
	} else {
	    super.check(result);
	}
    }
}
