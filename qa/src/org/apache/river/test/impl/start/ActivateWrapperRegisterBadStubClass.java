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
package org.apache.river.test.impl.start;

import java.util.logging.Level;

import java.rmi.*;
import net.jini.activation.*;

import org.apache.river.start.*;
import org.apache.river.start.ActivateWrapper.*;
import org.apache.river.start.ServiceStarter.*;
import org.apache.river.qa.harness.TestException;

/**
 * This test verifies that ActivateWrapper.register() throws the 
 * appropriate exception when passed an invalid implementation stub
 * class name. That is, a class that cannot be obtained from the
 * export location.
 */

public class ActivateWrapperRegisterBadStubClass extends AbstractStartBaseTest {
//TODO - no stub check only works for JRMP exported service, so need to ensure
// JRMP exporter for this service
    public void run() throws Exception {
        logger.log(Level.INFO, "run()");
	try {
	    net.jini.event.EventMailbox mailbox =
		    (net.jini.event.EventMailbox)
		    getManager().startService(
		    "org.apache.river.test.impl.start.NoStubProbe");
            logger.log(Level.INFO, "Created a stub-less service");
            throw new TestException( "ActivateWrapper.register()"
		    + " did not throw expected exception" );
	} catch (Exception ae) {
	    if (verifyClassNotFoundException(ae)) {
                logger.log(Level.INFO, "Expected Exception thrown: " + ae);
	    } else {
                throw new TestException("Unexpected Exception", ae);
	    }
	}
    }
}
