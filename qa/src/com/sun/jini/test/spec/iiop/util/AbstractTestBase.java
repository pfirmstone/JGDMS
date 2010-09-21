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
package com.sun.jini.test.spec.iiop.util;

import java.util.logging.Level;

// java.rmi
import java.rmi.Remote;

// java.util
import java.util.logging.Level;

// com.sun.jini
import com.sun.jini.qa.harness.QATest;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QAConfig;
import net.jini.iiop.IiopExporter;

// org.omg
import org.omg.CORBA.ORB;

// javax.rmi
import javax.rmi.CORBA.Stub;


/**
 * Abstract Test base for all iiop spec tests.
 */
public abstract class AbstractTestBase extends QATest {

    /** Constant for no-arg constructor type */
    public static final int NOARG_FACTORY = 0;

    /**
     * Constant for IiopExporter(ORB orb) constructor type
     */
    public static final int ORB_FACTORY = 1;

    /** Type of constructor being used for constructing JrmpExporter. */
    protected int cType;

    /** Will orb parameter be null in IiopExporter's constructor or not */
    protected boolean useNullOrb;

    /** ORB used for IiopExporter's construction */
    protected ORB orb;

    /**
     * Sets up the testing environment.
     *
     * @param sysConfig Configuration for setup.
     */
    public void setup(QAConfig sysConfig) throws Exception {
        // mandatory call to parent
        super.setup(sysConfig);

        // obtain type of constructor being used
        cType = sysConfig.getIntConfigVal("iiop.util.constructorType",
                NOARG_FACTORY);

        if ((cType != NOARG_FACTORY) && (cType != ORB_FACTORY)) {
            throw new TestException("Invalid constructorType: " + cType
                    + ". Should be " + NOARG_FACTORY + " or " + ORB_FACTORY);
        }

        if (cType == ORB_FACTORY) {
            // will orb parameter be null in IiopExporter's constructor or not
            useNullOrb = sysConfig.getBooleanConfigVal("iiop.util.useNullOrb",
                    false);

            if (!useNullOrb) {
                orb = ORB.init(new String[0], null);
            } else {
                orb = null;
            }
        }
    }

    /**
     * Constructs IiopExporter using appropriate constructor.
     *
     * @return Constructed IiopExporter
     */
    public IiopExporter createIiopExporter() {
        logger.log(Level.FINE,
                "We will use the following constructor for constructing "
                + "IiopExporter:");

        switch (cType) {
        case NOARG_FACTORY:
            logger.log(Level.FINE, "        IiopExporter()");
            return new IiopExporter();
        case ORB_FACTORY:
            logger.log(Level.FINE, "        IiopExporter(" + orb + ")");
            return new IiopExporter(orb);
        default:
            throw new java.lang.AssertionError(
                    "ERROR: unknown type of constructor " + cType
                    + " for constructing IiopExporter in createIiopExporter"
                    + " method.");
        }
    }

    /**
     * Connects stub manually when we use no-arg IiopConstructor or when
     * orb parameter specified in constructor is null.
     *
     * @param stub stub for manual connection to the orb
     */
    public void connectStub(Remote stub) throws TestException {
        try {
            ORB o = ORB.init(new String[0], null);
            ((Stub) stub).connect(o);
        } catch (Exception ex) {
            throw new TestException (
                "Unable to manually connect stub to the ORB.", ex);
        }
    }

    /**
     * Performs cleaning operations after test's completion.
     */
    public void tearDown() {
        try {
            // destroy ORB if any
            if (orb != null) {
                orb.destroy();
            }

            // call to parent
            super.tearDown();
        } catch (Exception ex) {
            logger.log(Level.FINE,
                    "Exception has been caught in tearDown method: " + ex);
        }
    }
}
