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
import java.rmi.activation.*;

import org.apache.river.start.*;
import org.apache.river.start.ActivateWrapper.*;
import org.apache.river.start.ServiceStarter.*;
import org.apache.river.qa.harness.TestException;

/**
 * This test verifies that ActivateWrapper.register() throws the 
 * appropriate exception when passed an invalid implementation.
 * That is, a class that does not support a shared
 * activation constructor.
 */

public class ActivateWrapperRegisterUnsharedImpl extends AbstractStartBaseTest {

    public void run() throws Exception {
        logger.log(Level.INFO, "run()");
	try { 
	    Object service =
		    getManager().startService(
                    "org.apache.river.test.impl.start.UnsharedProbe");
            logger.log(Level.INFO, "Activated unshared impl object");
            throw new TestException( "ActivateWrapper.register()"
				     + " did not throw expected exception" );
	} catch (Exception ae) {
	    if(verifyNoSuchMethodException(ae)) {
                logger.log(Level.INFO, "Expected Exception: " + ae);
	    } else {
                throw new TestException("Unexpected Exception", ae);
	    }
	}
    }
}
