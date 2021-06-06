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

import java.io.*;
import java.net.*;
import java.rmi.*;
import net.jini.activation.*;

import org.apache.river.start.*;
import org.apache.river.start.ActivateWrapper.*;
import org.apache.river.start.ServiceStarter.*;
import org.apache.river.qa.harness.TestException;

import net.jini.event.EventMailbox;

/**
 * This test verifies that ActivateWrapper.register() throws the 
 * appropriate exception when passed an invalid export codebase.
 * That is, an export codebase that does not contain
 * the implementation's stub class.
 */

public class ActivateWrapperRegisterBadStubCodebase extends AbstractStartBaseTest {
    public void run() throws Exception {
    	logger.log(Level.INFO, "run()");
	try { 
            net.jini.event.EventMailbox mailbox =
		    (net.jini.event.EventMailbox)
	            getManager().startService("net.jini.event.EventMailbox");
            throw new TestException( "ActivateWrapper.register()"
				     + " did not throw expected exception" );
	} catch (Exception ue) {
            if (verifyClassNotFoundException(ue)) {
                logger.log(Level.INFO, "Expected Exception thrown: " + ue);
	    } else {
                throw new TestException("Unexpected Exception", ue);
	    }
	}
    }
}
