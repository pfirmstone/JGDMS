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
package com.sun.jini.test.spec.export.exportpermission;

// com.sun.jini.qa
import com.sun.jini.qa.harness.QATestEnvironment;
import com.sun.jini.qa.harness.QAConfig;

// com.sun.jini.qa.harness
import com.sun.jini.qa.harness.QAConfig; // base class for QAConfig

// java.util
import com.sun.jini.qa.harness.Test;
import java.util.logging.Level;

// davis packages
import net.jini.export.ExportPermission;


/**
 * Abstract class for all {@link com.sun.jini.test.spec.export.exportpermission}
 * tests.
 */
public abstract class ExportPermission_AbstractTest extends QATestEnvironment implements Test {
    QAConfig config;

    /**
     * Target names of {@link net.jini.export.ExportPermission} objects.
     */
    String targetNames[] = {
              "exportRemoteInterface.com.sun.jini.test.spec.export.util.FakeInterface", 
              "exportRemoteInterface.com.sun.jini.test.spec.export.util.*",
              "exportRemoteInterface.*", "*" 
    };

    /**
     * An auxiliary class that describes a Test Case.
     */
    public class TestCase {
        
        /**
         * The 1-st {@link net.jini.export.ExportPermission} object.
         */
        private ExportPermission perm1;
        
        /**
         * The 2-nd {@link net.jini.export.ExportPermission} object.
         */
        private ExportPermission perm2;
        
        /**
         * Expected result of the Test Case.
         */
        private boolean expResult;
        
        /**
         * Constructor.
         *
         * @param p1  the 1-st {@link net.jini.export.ExportPermission} object
         * @param p2  the 2-nd {@link net.jini.export.ExportPermission} object
         * @param exp the expected result of the Test Case
         */
        public TestCase(ExportPermission p1, ExportPermission p2, boolean exp) {
            perm1 = p1;
            perm2 = p2;
            expResult = exp;
        }
        
        /**
         * Get the 1-st {@link net.jini.export.ExportPermission} object of this
         * {@link com.sun.jini.test.spec.export.exportpermission.ExportPermission_AbstractTest.TestCase}
         * object.
         *
         * @return the 1-st {@link net.jini.export.ExportPermission} object of
         *         this
         *         {@link com.sun.jini.test.spec.export.exportpermission.ExportPermission_AbstractTest.TestCase}
         *         object
         */
        public ExportPermission getPermission1() {
            return perm1;
        }
        
        /**
         * Get the 2-nd {@link net.jini.export.ExportPermission} object of this
         * {@link com.sun.jini.test.spec.export.exportpermission.ExportPermission_AbstractTest.TestCase}
         * object.
         *
         * @return the 2-nd {@link net.jini.export.ExportPermission} object of
         *         this
         *         {@link com.sun.jini.test.spec.export.exportpermission.ExportPermission_AbstractTest.TestCase}
         *         object
         */
        public ExportPermission getPermission2() {
            return perm2;
        }
        
        /**
         * Get the expected result of the Test Case.
         *
         * @return the expected result of the Test Case
         */
        public boolean getExpected() {
            return expResult;
        }
    }
}
