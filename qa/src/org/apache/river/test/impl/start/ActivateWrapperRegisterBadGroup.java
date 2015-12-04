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
import java.rmi.activation.*;
import java.util.*;

import org.apache.river.start.*;
import org.apache.river.start.ActivateWrapper.*;
import org.apache.river.start.ServiceStarter.*;
import org.apache.river.qa.harness.TestException;

/**
 * This test verifies that ActivateWrapper.register() throws the 
 * appropriate exception when passed an invalid activation group ID.
 * That is, an activation group ID that is not (currently) registered with the
 * activation system.
 */

public class ActivateWrapperRegisterBadGroup extends AbstractStartBaseTest {

    public void run() throws Exception {
        logger.log(Level.INFO, "run()");
        ActivateDesc adesc = 
            ActivateWrapperTestUtil.getServiceActivateDesc(
                    "net.jini.event.EventMailbox", getConfig());
        logger.log(Level.INFO, "EventMailbox ActivateDesc = " + adesc);

        logger.log(Level.INFO, "Creating activation group");
	ActivationGroupDesc actDesc = 
		new ActivationGroupDesc(new Properties(), null);
        logger.log(Level.INFO, "Received activation group ID");
	ActivationGroupID actID =
		ActivationGroup.getSystem().registerGroup(actDesc);
	    
        logger.log(Level.INFO, "Destroying activation group");
	ActivationGroup.getSystem().unregisterGroup(actID);
    
        logger.log(Level.INFO, "Attempting to register with a bogus activation"
		+ " group ID");
	try { 
            ActivationID aid = 
		    ActivateWrapper.register(actID, 
					     adesc, 
					     false, 
					     ActivationGroup.getSystem());
            throw new TestException( "ActivateWrapper.register()"
				     + " did not throw UnknownGroupException" );
        } catch (UnknownGroupException ae) {
                logger.log(Level.INFO, "Expected exception thrown: " + ae);
	}
    }
}
