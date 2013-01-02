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

package com.sun.jini.test.spec.config.abstractconfiguration.primitive;

import java.util.logging.Level;
import com.sun.jini.qa.harness.QATestEnvironment;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;
import java.util.logging.Logger;
import java.util.logging.Level;
import net.jini.config.AbstractConfiguration.Primitive;

/**
 * <pre>
 * Purpose:
 *   This test verifies the behavior of the methods of
 *   AbstractConfiguration.Primitive class.
 *
 * Test Cases:
 *   This test contains 8 test cases, one for each primitive type from list:
 *    boolean
 *    byte
 *    char
 *    short
 *    int
 *    long
 *    float
 *    double
 *
 * Actions:
 *   Test checks set of assertions and performs the following steps for that:
 *    1) equals(Object) returns <code>true</code> if the argument is a
 *       <code>Primitive</code> for which the result of calling
 *       <code>getValue</code> is the same as the value for this instance,
 *       otherwise <code>false</code>.
 *           Steps:
 *       construct three AbstractConfiguration.Primitive objects
 *       for the test case type, two objects should be constructed for the same
 *       value of primitive type, third object - for a different one;
 *       invoke equals method from one object passing the same object as
 *       an argument;
 *       assert that true is returned;
 *       invoke equals method from one object passing the object constrained
 *       from the same value of primitive type as an argument;
 *       assert that true is returned;
 *       invoke equals method from one object passing the object constrained
 *       from the different value of primitive type as an argument;
 *       assert that false is returned;
 *       invoke equals method from one object passing non Primitive class
 *       object as an argument;
 *       assert that false is returned;
 *       invoke equals method from one object passing null as an argument;
 *       assert that false is returned;
 *    2) getType() returns the primitive type of the value associated
 *       with this object.
 *           Steps:
 *       construct AbstractConfiguration.Primitive object
 *       for the test case type;
 *       invoke getType method from the object;
 *       assert that corresponding primitive type is returned;
 *    3) getValue() returns the primitive value associated with
 *       this object, represented as a primitive wrapper instance.
 *           Steps:
 *       construct AbstractConfiguration.Primitive object
 *       for the test case type;
 *       invoke getValue method from the object;
 *       assert that the same as passed in constructor primitive wrapper
 *       instance is returned;
 *    4) hashCode() returns a hash code value for this object.
 *           Steps:
 *       construct two different AbstractConfiguration.Primitive objects
 *       for the same primitive value of the test case type;
 *       assert that calls of the hashCode method for those objects returns
 *       the same value;
 *       assert that the hashCode method consistently returns the same
 *       value when it is invoked on the same object more twice;
 *    5) toString() returns a string representation of this object.
 *           Steps:
 *       construct AbstractConfiguration.Primitive object
 *       for the test case type;
 *       assert that not null is returned;
 *       assert that not empty string is returned;
 * </pre>
 */
public class Methods_Test extends QATestEnvironment implements Test {
    /**
     * Table of test cases for all primitive classes.
     * Structure: config content, type, data value
     */
    final static Object[] [] primitiveCases = {
        {   boolean.class,
            new Boolean(true),
            new Boolean(false)
        },
        {   byte.class,
            new Byte((byte) 5),
            new Byte((byte) 6)
        },
        {   char.class,
            new Character('f'),
            new Character('g')
        },
        {   short.class,
            new Short((short) 11222),
            new Short((short) 11223)
        },
        {   int.class,
            new Integer(1222333),
            new Integer(1222334)
        },
        {   long.class,
            new Long(111222333444L),
            new Long(111222333445L)
        },
        {   float.class,
            new Float(1.5f),
            new Float(1.6f)
        },
        {   double.class,
            new Double(2.5d),
            new Double(2.6d)
        }
    };

    /**
     * Start test case.
     */
    public void runCase(Object[] testCase) throws Exception {
        logger.log(Level.INFO, "--> " + testCase[0].toString());
        
        // 1 equals
        Class type = (Class)testCase[0];
        Object somePrimitiveValue = testCase[1];
        Object differentPrimitiveValue = testCase[2];
        Primitive p = new Primitive(somePrimitiveValue);
        Primitive p2 = new Primitive(somePrimitiveValue);
        Primitive p3 = new Primitive(differentPrimitiveValue);
        if (!p.equals(p) || !p.equals(p2) || p.equals(p3) || p.equals(type)) {
            throw new TestException(
                    "equals method returns invalid value");
        }
        
        // 2 getType
        if (p.getType() != type) {
            throw new TestException(
                    "getType method returns invalid value");
        }
        
        // 3 getValue
        if (p.getValue() != somePrimitiveValue) {
            throw new TestException(
                    "getType method returns invalid value");
        }
        
        // 4 hashCode
        if (p.hashCode() != p.hashCode() || p.hashCode() != p2.hashCode()) {
            throw new TestException(
                    "hashCode method returns invalid value");
        }
        
        // 5 toString
        if (p.toString() == null || p.toString().length() == 0) {
            throw new TestException(
                    "toString method returns invalid value");
        }
    }

    /**
     * This method performs all actions mentioned in class description.
     */
    public void run() throws Exception {
        for (int i = 0; i < primitiveCases.length; ++i) {
            runCase(primitiveCases[i]);
        }
    }
}
