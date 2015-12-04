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
package org.apache.river.test.spec.url.httpmd.wrongmdexc;

import java.util.logging.Level;

// org.apache.river.qa
import org.apache.river.qa.harness.QATestEnvironment;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;
// org.apache.river.qa.harness
import org.apache.river.qa.harness.QAConfig; // base class for QAConfig
import org.apache.river.qa.harness.TestException;

// java.util
import java.util.logging.Level;

// davis packages
import net.jini.url.httpmd.WrongMessageDigestException;


/**
 * <pre>
 *
 * Purpose:
 *   This test verifies the behavior of
 *   {@link WrongMessageDigestException#WrongMessageDigestException(String)} constructor.
 *   {@link WrongMessageDigestException#WrongMessageDigestException(String)}
 *   constructor should create an instance of
 *   {@link WrongMessageDigestException}
 *   with the specified detail message.
 *
 * Test Cases:
 *   This test tries to create
 *   {@link WrongMessageDigestException} object
 *   with the specified detail message using the
 *   {@link WrongMessageDigestException#WrongMessageDigestException(String)}
 *   constructor.
 *
 * Infrastructure:
 *   This test requires the following infrastructure:
 *     - Constructor
 *       performs actions
 *
 * Actions:
 *   Test performs the following steps:
 *     1) constructing a
 *        {@link WrongMessageDigestException} object
 *        with originalMsg message,
 *     2) getting the detailed message string of this
 *        {@link WrongMessageDigestException} object
 *        (gotMsg),
 *     3) compare originalMsg with gotMsg.
 *
 * </pre>
 */
public class Constructor extends QATestEnvironment implements Test {
    QAConfig config;

    /**
     * The original detailed message of
     * {@link WrongMessageDigestException}.
     * The value is specified by Constructor.Msg test property.
     */
    protected String originalMsg;

    /**
     * The detailed message of
     * {@link WrongMessageDigestException}.
     */
    protected String gotMsg;

    /**
     * <pre>
     * This method performs all preparations.
     * Test parameters:
     *    Constructor.Msg - the original detail message of
     *                      {@link WrongMessageDigestException}
     * </pre>
     */
    public Test construct(QAConfig config) throws Exception {
        super.construct(config);
        this.config = (QAConfig) config; // or this.config = getConfig();

        /* Getting test parameter */
        originalMsg = config.getStringConfigVal("Constructor.Msg",
                "My Message");
        return this;
    }

    /**
     * This method performs all actions mentioned in class description.
     */
    public void run() throws Exception {

        /*
         * Creating new instance of
         * {@link WrongMessageDigestException}
         */
        WrongMessageDigestException e = new
                WrongMessageDigestException(originalMsg);
        logger.log(Level.FINE, "\nException: " + e);

        /*
         * Getting the detailed message string of the
         * {@link WrongMessageDigestException}
         */
        gotMsg = e.getMessage();

        /* Comparing the messages */
        if (!originalMsg.equals(gotMsg)) {
            throw new TestException(
                    "The detailed message of this " + "WrongMessageDigestException object \""
                    + gotMsg + "\" isn't equal to the one specified while "
                    + "creating this object with "
                    + "WrongMessageDigestException(\"" + originalMsg
                    + "\") constructor.");
        }
        return;
    }
}
