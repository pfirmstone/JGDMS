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
package org.apache.river.test.spec.io.marshalledinstance;

import java.util.logging.Level;

import org.apache.river.qa.harness.QATestEnvironment;
import org.apache.river.qa.harness.TestException;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;

import net.jini.io.MarshalledInstance;

import org.apache.river.test.spec.io.util.FakeObject;
import org.apache.river.test.spec.io.util.FakeRMIClassLoaderSpi;

import java.io.File;
import java.io.IOException;
import java.rmi.MarshalledObject;
import java.util.logging.Level;

/**
 * <pre>
 * Purpose
 *   This test verifies the behavior of MarshalledInstance
 *   equals, fullyEquals, and hash methods.
 * 
 * Test Cases
 *   This test contains 5 test cases:
 *     1) Same serialized forms, same codebases
 *     2) Different serialized forms, same codebases
 *     3) Same serialized form, different codebases
 *     4) Different serialized forms, different codebases
 *     5) MarshalledInstance and non-MarshalledInstance comparison
 * 
 * Infrastructure
 *   This test requires the following infrastructure:
 *     1) FakeObject
 *          -implements Serializable
 *          -custom readObject method throws AssertionError
 *     3) FakeRMIClassLoaderSpi
 *          -extends RMIClassLoaderSpi
 *          -getClassAnnotation methods returns configurable string
 * 
 * Actions
 *   The test performs the following steps:
 *     Same serialized forms, same codebases
 *       1) construct two instances of MarshalledInstance with the
 *          same FakeObject
 *       2) verify instances are .equals and .fullyEquals to 
 *          themselves (reflexive)
 *          and .equals and .fullyEquals to each other (symmetric)
 *       3) verify instances .hashCode methods return the same value
 *       4) repeat steps 1 to 3, except construct one MarshalledInstance with
 *          FakeObject and the other with MarshalledObject(FakeObject)
 *     Different serialized forms, same codebases  
 *       5) construct two instances of MarshalledInstance with different objects
 *       6) verify instances are not .equals or .fullyEquals to each other
 *     Same serialized form, different codebases
 *       7) construct an instance of MarshalledInstance with FakeObject o
 *       8) set FakeRMIClassLoaderSpi.getClassAnnotationReturn to "http://fake"
 *       9) construct an instance of MarshalledInstance with the 
 *          same FakeObject o
 *      10) verify instances are .equals but not .fullyEquals to each other
 *      11) verify instances .hashCode methods return the same value
 *     Different serialized forms, different codebases
 *      12) set FakeRMIClassLoaderSpi.getClassAnnotationReturn to "http://fake1"
 *      13) construct an instance of MarshalledInstance with Object o1
 *      14) set FakeRMIClassLoaderSpi.getClassAnnotationReturn to "http://fake2"
 *      15) construct an instance of MarshalledInstance with Object o2
 *      16) verify instances are not .equals or .fullyEquals to each other
 *     MarshalledInstance and non-MarshalledInstance comparison
 *      17) construct an instance of MarshalledInstance, passing in FakeObject
 *      18) verify instance is not .equals or .fullyEquals to FakeObject
 * </pre>
 */
public class ObjectMethodsTest extends QATestEnvironment implements Test {

    class FakeMarshalledInstance extends MarshalledInstance {
        public FakeMarshalledInstance(Object obj) throws IOException {
            super(obj);
        }
    }

    // inherit javadoc
    public Test construct(QAConfig sysConfig) throws Exception {
        return this;
    }

    // inherit javadoc
    public void run() throws Exception {
        FakeObject fo = new FakeObject(new AssertionError());
        MarshalledInstance mi1;
        MarshalledInstance mi2;

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 1: "
            + "Same serialized forms, same codebases");
        logger.log(Level.FINE,"");

        // construct 2 MarshalledInstances using same constructor
        mi1 = new MarshalledInstance(fo);
        mi2 = new MarshalledInstance(fo);
        checkEquals(true, mi1, mi2);
        checkFullyEquals(true, mi1, mi2);

        // construct 2 MarshalledInstances using different constructor
        mi1 = new MarshalledInstance(fo);
        mi2 = new MarshalledInstance(new MarshalledObject(fo));
        checkEquals(true, mi1, mi2);
        checkFullyEquals(true, mi1, mi2);

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 2: "
            + "Different serialized forms, same codebases");
        logger.log(Level.FINE,"");

        mi1 = new MarshalledInstance(fo);
        mi2 = new MarshalledInstance(new File("foo"));
        checkEquals(false, mi1, mi2);
        checkFullyEquals(false, mi1, mi2);

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 3: "
            + "Same serialized form, different codebases");
        logger.log(Level.FINE,"");

        mi1 = new MarshalledInstance(fo);

        FakeRMIClassLoaderSpi.getClassAnnotationReturn = "http://fake";
        mi2 = new MarshalledInstance(fo);
        FakeRMIClassLoaderSpi.getClassAnnotationReturn = null;

        checkEquals(true, mi1, mi2);
        checkFullyEquals(false, mi1, mi2);

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 4: "
            + "Different serialized forms, different codebases");
        logger.log(Level.FINE,"");

        FakeRMIClassLoaderSpi.getClassAnnotationReturn = "http://fake1";
        mi1 = new MarshalledInstance(fo);

        FakeRMIClassLoaderSpi.getClassAnnotationReturn = "http://fake2";
        mi2 = new MarshalledInstance(new File("fakeFile"));
        FakeRMIClassLoaderSpi.getClassAnnotationReturn = null;

        checkEquals(false, mi1, mi2);
        checkFullyEquals(false, mi1, mi2);

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 5: "
            + "MarshalledInstance and non-MarshalledInstance comparison");
        logger.log(Level.FINE,"");

        mi1 = new MarshalledInstance(fo);
        checkEquals(false, mi1, fo);
        assertion(! mi1.fullyEquals(fo));
    }

    // inherit javadoc
    public void tearDown() {
    }

    private void checkEquals(boolean equal, Object mi1, Object mi2)
        throws TestException
    {
        // verify equals, hashCode, and toString methods
        assertion(! mi1.equals(null));
        assertion(equal == mi1.equals(mi2));
        assertion(equal == mi2.equals(mi1));
        assertion(mi1.equals(mi1));
        assertion(mi2.equals(mi2));
        if (equal) {
            assertion(mi1.hashCode() == mi2.hashCode());
            assertion(mi1.toString() != null);
            assertion(mi2.toString() != null);
        }
    }

    private void checkFullyEquals(boolean equal, MarshalledInstance mi1, 
        MarshalledInstance mi2) throws TestException
    {
        // verify fullyEquals
        assertion(! mi1.fullyEquals(null));
        assertion(equal == mi1.fullyEquals(mi2));
        assertion(equal == mi2.fullyEquals(mi1));
        assertion(mi1.fullyEquals(mi1));
        assertion(mi2.fullyEquals(mi2));
    }

}

