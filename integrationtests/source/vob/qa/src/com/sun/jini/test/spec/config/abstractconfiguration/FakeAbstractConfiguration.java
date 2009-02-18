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

package com.sun.jini.test.spec.config.abstractconfiguration;
import net.jini.config.AbstractConfiguration;
import net.jini.config.AbstractConfiguration.Primitive;
import net.jini.config.ConfigurationException;
import net.jini.config.NoSuchEntryException;
import com.sun.jini.test.spec.config.util.TestComponent;
import com.sun.jini.test.spec.config.util.DefaultTestComponent;


/**
 * Some Configuration that is abstract.
 */
public class FakeAbstractConfiguration extends AbstractConfiguration {
    /**
     * Is some constructor was called.
     */
    private boolean wasCalled = false;

    /**
     * Storing the component argument for future control.
     */
    private String component = null;

    /**
     * Component argument for future control.
     */
    private String name = null;

    /**
     * Type argument for future control.
     */
    private Class type = null;

    /**
     * Data argument for future control.
     */
    private Object data = null;

    /**
     * Last return data for future control.
     */
    private Object lastReturn = null;

    /**
     * Exception that should be thrown in next call.
     */
    private Throwable testException = null;

    /**
     * Value that should be returned in next call.
     */
    private Object returnValue = null;
  
    /**
     * Valid component argument.
     */
    public final static String validComponentName =
            TestComponent.class.getName();
  
    /**
     * Valid entry argument.
     */
    public final static String validEntryName = "entry";
  
    /**
     * Table of test cases for all primitive classes.
     * Structure: entry, return value, default value, class
     */
    public final static Object[][] primitiveCases = {
            { "booleanEntry",
                new Boolean(false),
                new Boolean(true),
                boolean.class
            },
            { "byteEntry",
                new Byte((byte) 0),
                new Byte((byte) 1),
                byte.class
            },
            { "charEntry",
                new Character('a'),
                new Character('b'),
                char.class
            },
            { "shortEntry",
                new Short((short) 0),
                new Short((short) 1),
                short.class
            },
            { "intEntry",
                new Integer(0),
                new Integer(1),
                int.class
            },
            { "longEntry",
                new Long((long) 0),
                new Long((long) 1),
                long.class
            },
            { "floatEntry",
                new Float(0.5),
                new Float(1.0),
                float.class
            },
            { "doubleEntry",
                new Double(0.5),
                new Double(1.0),
                double.class
            }};
    
    /**
     * {@inheritDoc}
     */
    public FakeAbstractConfiguration()
            throws ConfigurationException {
        super();
        wasCalled = true;
    }

    /**
     * emulation of getEntryInternal
     */
    protected Object getEntryInternal(String component,
				      String name,
				      Class type,
				      Object data)
	    throws ConfigurationException {
        wasCalled = true;
        this.component = component;
        this.name = name;
        this.type = type;
        this.data = data;
        if (testException != null) {
            if (testException instanceof ConfigurationException) {
                throw (ConfigurationException)testException;
            } if (testException instanceof RuntimeException) { 
                throw (RuntimeException)testException;
            } if (testException instanceof Error) { 
                throw (Error)testException;
            }
        }
        if (returnValue != null) {
            lastReturn = returnValue;
            returnValue = null;
            return lastReturn;
        }
        if (component.equals(validComponentName)
                && name.equals(validEntryName)) {
            lastReturn = new TestComponent();
            return lastReturn;
        } else {
            for (int j = 0; j < primitiveCases.length; ++j) {
                Object[] subCase = primitiveCases[j];
                if (component.equals(validComponentName)
                        && name.equals(subCase[0])) {
                    lastReturn = new Primitive(subCase[1]);
                    return lastReturn;
                }
            }
        }
	throw new NoSuchEntryException(
	        "entry not found for component " + component +
	        ", name " + name);
    };

    /**
     * returns last component argument of getEntryInternal method
     */
    public String getComponent() {
        return component;
    };

    /**
     * returns last name argument of getEntryInternal method
     */
    public String getName() {
        return name;
    };

    /**
     * returns last type argument of getEntryInternal method
     */
    public Class getType() {
        return type;
    };

    /**
     * returns last data argument of getEntryInternal method
     */
    public Object getData() {
        return data;
    };

    /**
     * returns last getEntryInternal return value
     */
    public void setReturn(Object returnValue) {
        this.returnValue = returnValue;
    };

    /**
     * returns last getEntryInternal return value
     */
    public Object getReturn() {
        return lastReturn;
    };

    /**
     * stores an exception that should be thrown in next call
     * of getEntryInternal method
     */
    public void setException(Throwable e) {
        testException = e;
    };

    /**
     * Checks if the argument is a valid <i>Identifier</i>, as defined in the
     * <i>Java(TM) Language Specification</i>.
     *
     * @param name the name to check
     * @return <code>true</code> if <code>name</code> is a valid
     *	       <i>Identifier</i>, else <code>false</code>
     */
    public static boolean validIdentifierPublic(String name) {
	return validIdentifier(name);
    }

    /**
     * Checks if the argument is a valid <i>QualifiedIdentifier</i>, as defined
     * in the <i>Java Language Specification</i>.
     *
     * @param name the name to check
     * @return <code>true</code> if <code>name</code> is a valid
     *	       <i>QualifiedIdentifier</i>, else <code>false</code>
     */
    public static boolean validQualifiedIdentifierPublic(String name) {
	return validQualifiedIdentifier(name);
    }
}
