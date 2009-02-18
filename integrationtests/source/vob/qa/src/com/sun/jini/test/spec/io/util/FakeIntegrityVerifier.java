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
package com.sun.jini.test.spec.io.util;

import net.jini.security.IntegrityVerifier;

import java.net.URL;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * This classes provides an integrity verifier whose behavior can
 * be controlled by system properties.
 */
public class FakeIntegrityVerifier implements IntegrityVerifier {

    public FakeIntegrityVerifier() {
    }

    /**
     * This method throws SecurityException if the system property
     * com.sun.jini.test.spec.io.util.FakeIntegrityVerifier.throwException
     * is <code>true</code>.  Otherwise, this method returns 
     * <code>true</code> or <code>false</code> depending on the boolean
     * value of the system property 
     * com.sun.jini.test.spec.io.util.FakeIntegrityVerifier.providesIntegrity.
     */
    public boolean providesIntegrity(URL url) {
        Logger logger = Logger.getLogger("com.sun.jini.qa.harness.test");
        logger.entering(getClass().getName(),"providesIntegrity",url);

        boolean throwException = Boolean.getBoolean(
            "com.sun.jini.test.spec.io.util.FakeIntegrityVerifier."
            + "throwException");
        if (throwException) {
            logger.log(Level.FINE,"FakeIntegrityVerifier.providesIntegrity() "
                + "throwing SecurityException");
            throw new SecurityException();
        }

        boolean returnVal = Boolean.getBoolean(
            "com.sun.jini.test.spec.io.util.FakeIntegrityVerifier."
            + "providesIntegrity");
        logger.log(Level.FINE,"FakeIntegrityVerifier.providesIntegrity() "
            + "returning " + returnVal);
        return returnVal;
    }
}
