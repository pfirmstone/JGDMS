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
package com.sun.jini.test.spec.constraint.coreconstraint;

import java.util.logging.Level;

// com.sun.jini.qa.harness
import com.sun.jini.qa.harness.TestException;

// com.sun.jini.qa
import com.sun.jini.qa.harness.QATest;
import com.sun.jini.qa.harness.QAConfig;

// java.util
import java.util.logging.Level;

// java.rmi
import java.rmi.MarshalledObject;

// Davis packages
import net.jini.core.constraint.ClientAuthentication;
import net.jini.core.constraint.Confidentiality;
import net.jini.core.constraint.Delegation;
import net.jini.core.constraint.Integrity;
import net.jini.core.constraint.ServerAuthentication;
import net.jini.core.constraint.InvocationConstraint;


/**
 * <pre>
 *
 * Purpose:
 *   This test verifies that serialization for the following classes:
 *     {@link net.jini.core.constraint.ClientAuthentication}
 *     {@link net.jini.core.constraint.Confidentiality}
 *     {@link net.jini.core.constraint.Delegation}
 *     {@link net.jini.core.constraint.Integrity}
 *     {@link net.jini.core.constraint.ServerAuthentication}
 *   is guaranteed to produce instances that are comparable with ==.
 *
 * Test Cases:
 *   Objects to test:
 *   - ClientAuthentication.NO,
 *   - ClientAuthentication.YES,
 *   - Confidentiality.NO,
 *   - Confidentiality.YES,
 *   - Delegation.NO,
 *   - Delegation.YES,
 *   - Integrity.NO,
 *   - Integrity.YES,
 *   - ServerAuthentication.NO,
 *   - ServerAuthentication.YES.
 *
 * Infrastructure:
 *     - {@link SerializationTest}
 *         this file (performs actions)
 *
 * Actions:
 *   Test creates the {@link net.jini.core.constraint.InvocationConstraint}
 *   objects to test.
 *   In each test case the following steps are performed:
 *   - {@link net.jini.core.constraint.InvocationConstraint} object is
 *     serialized and then deserialized; it's performed with creation
 *     of a {@link java.rmi.MarshalledObject} that contains a byte stream
 *     with the serialized representation of the
 *     {@link net.jini.core.constraint.InvocationConstraint} object given
 *     to its constructor; then {@link java.rmi.MarshalledObject#get()} method
 *     returns a new copy of the original object, as deserialized from the
 *     contained byte stream,
 *   - the obtained object is compared with the original one using == operator.
 *
 * </pre>
 */
public class SerializationTest extends QATest {
    QAConfig config;

    /**
     * Objects to test.
     */
    public static final InvocationConstraint constraints[] = {
            ClientAuthentication.NO,
            ClientAuthentication.YES,
            Confidentiality.NO,
            Confidentiality.YES,
            Delegation.NO,
            Delegation.YES,
            Integrity.NO,
            Integrity.YES,
            ServerAuthentication.NO,
            ServerAuthentication.YES
    };

    /**
     * This method performs all actions mentioned in class description.
     */
    public void run() throws Exception {
        config = getConfig();

        for (int i = 0; i < constraints.length; i++) {
            logger.log(Level.FINE, "\n\t+++++ Test Case #" + (i + (int) 1));

            if (!checker(constraints[i])) {
                throw new TestException(
                        "" + " test failed");
            }
        }
        return;
    }

    /**
     * This method checks that serialization for the specified
     * {@link net.jini.core.constraint.InvocationConstraint} is guaranteed to
     * produce instances that are comparable with ==. I.e. serialization and
     * subsequent deserialization for the specified object produce the object
     * that is equivalent (==) to the specified object.
     *
     * @return true if the specified object after serialization and subsequent
     *         deserialization is equivalent (==) to the specified object or
     *         false otherwise
     */
    public boolean checker(InvocationConstraint ic) {
        logger.log(Level.FINE,
                "Invocation Constraint before serialization:: " + ic);
        logger.log(Level.FINE, "serialization ...");
        logger.log(Level.FINE, "deserialization ...");

        /*
         * Creates a new MarshalledObject that contains a byte stream with the
         * serialized representation of an InvocationConstraint object given
         * to its constructor. Then get() method returns a new copy
         * of the original object, as deserialized from the contained byte
         * stream.
         */
        try {
            MarshalledObject mObj = new MarshalledObject(ic);
            InvocationConstraint dic = (InvocationConstraint) mObj.get();
            logger.log(Level.FINE,
                    "Invocation Constraint after deserialization:: " + dic);
            return (dic == ic);
        } catch (Exception e) {
            logger.log(Level.FINE, e + "has been thrown while serialization or"
                    + "subsequent deserialization of " + ic);
            return false;
        }
    }
}
