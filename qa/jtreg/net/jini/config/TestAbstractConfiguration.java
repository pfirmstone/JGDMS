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
 * @summary Tests the AbstractConfiguration class
 * 
 * @library ../../../unittestlib
 * @build UnitTestUtilities BasicTest Test
 * @run main TestAbstractConfiguration
 */

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import net.jini.config.AbstractConfiguration.Primitive;
import net.jini.config.AbstractConfiguration;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.config.NoSuchEntryException;

public class TestAbstractConfiguration extends UnitTestUtilities {

    static List tests = new ArrayList();

    public static void main(String[] args) {
	test(tests);
    }

    /* -- Test Primitive constructor and getValue method -- */

    static {
	tests.add(TestPrimitive.localtests);
    }

    public static class TestPrimitive extends BasicTest {
	static Test[] localtests = {
	    new TestPrimitive(null,
			      new NullPointerException(
				  "value is not a primitive: null")),
	    new TestPrimitive(new BigInteger("33"),
			      new IllegalArgumentException(
				  "value is not a primitive: 33")),
	    new TestPrimitive(new Integer(3), new Integer(3))
	};

	private final Object value;

	public static void main(String[] args) {
	    test(localtests);
	}

	TestPrimitive(Object value, Object result) {
	    super(String.valueOf(value), result);
	    this.value = value;
	}

	public Object run() {
	    try {
		return new Primitive(value);
	    } catch (Exception e) {
		return e;
	    }
	}

	public void check(Object result) {
	    Object compareTo = getCompareTo();
	    if ((result instanceof Primitive &&
		 safeEquals(((Primitive) result).getValue(), compareTo)) ||
		(compareTo instanceof Throwable &&
		 result instanceof Throwable &&
		 safeEquals(((Throwable) compareTo).getMessage(),
			    ((Throwable) result).getMessage())))
	    {
		return;
	    }
	    throw new FailedException("Should be: " + compareTo);
	}
    }

    /* -- Test Primitive.equals and hashCode -- */

    static {
	tests.add(TestPrimitiveEquals.localtests);
    }

    public static class TestPrimitiveEquals extends BasicTest {
	static Test[] localtests = {
	    new TestPrimitiveEquals(new Primitive(new Integer(3)),
				    new Primitive(new Integer(3)),
				    true),
	    new TestPrimitiveEquals(new Primitive(new Integer(3)),
				    new Primitive(new Integer(4)),
				    false),
	    new TestPrimitiveEquals(new Primitive(new Integer(3)),
				    new Primitive(new Short((short) 3)),
				    false),
	    new TestPrimitiveEquals(new Primitive(new Integer(3)),
				    new Integer(3),
				    false),
	    new TestPrimitiveEquals(new Primitive(new Integer(3)),
				    null,
				    false)
	};

	private final Object x;
	private final Object y;

	public static void main(String[] args) {
	    test(localtests);
	}

	TestPrimitiveEquals(Object x, Object y, boolean result) {
	    super(x + ", " + y, Boolean.valueOf(result));
	    this.x = x;
	    this.y = y;
	}

	public Object run() {
	    return Boolean.valueOf(x == null ? y == null : x.equals(y));
	}

	public void check(Object result) throws Exception {
	    super.check(result);
	    super.check(Boolean.valueOf(y == null ? x == null : y.equals(x)));
	    Object compareTo = getCompareTo();
	    if (Boolean.TRUE.equals(compareTo) &&
		x.hashCode() != y.hashCode())
	    {
		throw new FailedException("Hash codes differ");
	    }
	}
    }

    /* -- Test getEntry of AbstractConfiguration subclass -- */

    static {
	tests.add(TestGetEntry.localtests);
    }

    public static class TestGetEntry extends BasicTest {
	static Test[] localtests = {
	    new TestGetEntry("", "a", Object.class,
			     IllegalArgumentException.class),
	    new TestGetEntry("a", "b.c", Object.class,
			     IllegalArgumentException.class),
	    new TestGetEntry("a", "b", Integer.TYPE, new Long(3),
			     IllegalArgumentException.class),
	    new TestGetEntry("nope", "intEntry", Object.class,
			     NoSuchEntryException.class),
	    new TestGetEntry("comp", "nope", Object.class,
			     NoSuchEntryException.class),
	    new TestGetEntry("comp", "intEntry", Object.class,
			     ConfigurationException.class),
	    new TestGetEntry("comp", "intEntry", Integer.class,
			     ConfigurationException.class),
	    new TestGetEntry("comp", "booleanEntry", Object.class,
			     ConfigurationException.class),
	    new TestGetEntry("comp", "booleanEntry", Integer.class,
			     ConfigurationException.class),
	    new TestGetEntry("comp", "booleanEntry", Integer.TYPE,
			     ConfigurationException.class),
	    new TestGetEntry("comp", "intEntry", Boolean.TYPE,
			     ConfigurationException.class),
	    new TestGetEntry("comp", "MapEntry", Integer.TYPE,
			     ConfigurationException.class),
	    new TestGetEntry("comp", "MapEntry", Integer.class,
			     ConfigurationException.class),
	    new TestGetEntry("comp", "intEntry", Integer.TYPE,
			     new Integer(33)),
	    new TestGetEntry("comp", "charEntry", Integer.TYPE,
			     ConfigurationException.class),
	    new TestGetEntry("comp", "charEntry", Long.TYPE,
			     ConfigurationException.class),
	    new TestGetEntry("comp", "intEntry", Integer.TYPE, new Integer(50),
			     new Integer(33)),
	    new TestGetEntry("comp", "nope", Integer.TYPE, new Integer(50),
			     new Integer(50)),
	    new TestGetEntry("data", "data", Object.class,
			     ConfigurationException.class),
	    new TestGetEntry("data", "data", Object.class, "some default",
			     ConfigurationException.class),
	    new TestGetEntry("data", "data", Object.class, "some default",
			     "some data", "some data")
	};

	static Object[] entries = {
	    "comp.intEntry", new Primitive(new Integer(33)),
	    "comp.charEntry", new Primitive(new Character('a')),
	    "comp.booleanEntry", new Primitive(Boolean.TRUE),
	    "comp.MapEntry", new HashMap()
	};

	static Configuration config = new AbstractConfiguration() {
	    protected Object getEntryInternal(String component,
					      String name,
					      Class type,
					      Object data)
		throws ConfigurationException
	    {
		if (component == null || name == null || type == null) {
		    throw new FailedException(
			"component, name and type should not be null");
		}
		String full = component + '.' + name;
		if ("data.data".equals(full)) {
		    if (data == NO_DATA) {
			throw new ConfigurationException("No data");
		    }
		    return data;
		}
		for (int i = 0; i < entries.length; i += 2) {
		    if (entries[i].equals(full)) {
			return (entries[i + 1]);
		    }
		}
		throw new NoSuchEntryException("Entry not found");
	    }
	};

	private final String component;
	private final String name;
	private final Class type;
	private final boolean hasDefault;
	private final Object defaultValue;
	private final boolean hasData;
	private final Object data;

	public static void main(String[] args) {
	    test(localtests);
	}

	TestGetEntry(String component, String name, Class type, Object value) {
	    this(component, name, type, false, null, false, null, value);
	}

	TestGetEntry(String component,
		     String name,
		     Class type,
		     Object defaultValue,
		     Object value)
	{
	    this(component, name, type, true, defaultValue, false, null, value);
	}

	TestGetEntry(String component,
		     String name,
		     Class type,
		     Object defaultValue,
		     Object data,
		     Object value)
	{
	    this(component, name, type, true, defaultValue, true, data, value);
	}

	TestGetEntry(String component,
		     String name,
		     Class type,
		     boolean hasDefault,
		     Object defaultValue,
		     boolean hasData,
		     Object data,
		     Object value)
	{
	    super(component + ", " + name + ", " + type +
		  (hasDefault ? ", " + defaultValue : "") +
		  (hasData ? ", " + data : ""),
		  value);
	    this.component = component;
	    this.name = name;
	    this.type = type;
	    this.hasDefault = hasDefault;
	    this.defaultValue = defaultValue;
	    this.hasData = hasData;
	    this.data = data;
	}

	public Object run() {
	    try {
		return hasData
		    ? config.getEntry(component, name, type, defaultValue,
				      data)
		    : hasDefault
		    ? config.getEntry(component, name, type, defaultValue)
		    : config.getEntry(component, name, type);
	    } catch (Exception e) {
		return e;
	    }
	}

	public void check(Object result) {
	    Object compareTo = getCompareTo();
	    if (safeEquals(result, compareTo) ||
		(compareTo instanceof Class &&
		 result != null &&
		 result.getClass() == compareTo) ||
		(compareTo instanceof Throwable &&
		 result instanceof Throwable &&
		 safeEquals(((Throwable) compareTo).getMessage(),
			    ((Throwable) result).getMessage())))
	    {
		return;
	    }
	    throw new FailedException("Should be: " + compareTo);
	}
    }
}
