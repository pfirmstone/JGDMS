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
import com.sun.jini.qa.harness.QATest;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QAConfig;
import java.util.logging.Logger;
import java.util.logging.Level;
import net.jini.config.AbstractConfiguration.Primitive;

/**
 * <pre>
 * Purpose:
 *   This test verifies the behavior of the constructor of
 *   AbstractConfiguration.Primitive class.
 *
 * Actions:
 *   Test performs the following steps:
 *     1) For all primitive types construct some associated
 *       wrapper class object;
 *       for each case construct AbstractConfiguration.Primitive class
 *       object passing wrapper object as a parameter;  
 *       assert the object is constructed and no exceptions are thrown;
 *     2) ConstructAbstractConfiguration.Primitive class
 *       object passing null as a parameter;  
 *       assert that IllegalArgumentException is thrown;
 *     3) Construct several objects that is not associated
 *       wrapper class object;
 *       for each case construct AbstractConfiguration.Primitive class
 *       object passing constructed object as a parameter;  
 *       assert that IllegalArgumentException is thrown;
 * </pre>
 */
public class Constructor_Test extends QATest {

    /**
     * This method performs all actions mentioned in class description.
     */
    public void run() throws Exception {
        // 1 primitive types
        new Primitive(new Boolean(true));
        new Primitive(new Byte((byte) 5));
        new Primitive(new Character('f'));
        new Primitive(new Short((short) 11222));
        new Primitive(new Integer(1222333));
        new Primitive(new Long(111222333444L));
        new Primitive(new Float(1.5f));
        new Primitive(new Double(2.5d));

        // 2 null
        try{
            new Primitive(null);
            throw new TestException(
                    "IllegalArgumentException should be thrown");
        } catch (IllegalArgumentException ignore) {
        }

        // 3 not associated wrapper class object
        try{
            new Primitive("Some string");
            throw new TestException(
                    "IllegalArgumentException should be thrown");
        } catch (IllegalArgumentException ignore) {
        }
    }
}
