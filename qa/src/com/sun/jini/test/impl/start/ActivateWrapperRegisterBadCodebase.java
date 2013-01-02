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
package com.sun.jini.test.impl.start;

import java.util.logging.Level;

import java.io.*;
import java.net.*;
import java.rmi.*;
import java.rmi.activation.*;

import com.sun.jini.start.*;
import com.sun.jini.start.ActivateWrapper.*;
import com.sun.jini.start.ServiceStarter.*;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.OverrideProvider;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.ActivatableServiceStarterAdmin;


/**
 * This test verifies that ActivateWrapper.register() throws the 
 * appropriate exception when passed an invalid import
 * codebase location. That is, an import codebase that does not contain
 * the implementation class.
 *
 * Notes: This test assumes
 * <UL>
 * <LI> File based URLs for the import codebase location
 * <LI> The implementation class is not in the application's classpath and
 *      must be loaded from the provided codebase argument.
 * </UL>
 */

public class ActivateWrapperRegisterBadCodebase extends AbstractStartBaseTest {

    public void run() throws Exception {
        logger.log(Level.INFO, "run()");
        try {
	    net.jini.event.EventMailbox mailbox = 
                (net.jini.event.EventMailbox) 
	            getManager().startService("net.jini.event.EventMailbox");
            logger.log(Level.INFO, "Created service with bogus codebase");
            throw new TestException( "ActivateWrapper.register()"
	           + " did not throw expected exception" );
        } catch (Exception e) {
	    if (verifyClassNotFoundException(e)) {
                logger.log(Level.FINE, "Received Expected ClassNotFound Exception");
            } else {
                throw new TestException("Unexpected Exception", e);
	    }
	}
    }
}
