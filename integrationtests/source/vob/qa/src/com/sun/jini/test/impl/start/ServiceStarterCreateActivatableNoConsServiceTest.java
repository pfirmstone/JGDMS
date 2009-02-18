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

import com.sun.jini.qa.harness.TestException;

import java.io.File;

import net.jini.event.EventMailbox;

public class ServiceStarterCreateActivatableNoConsServiceTest extends StarterBase {
    private static String[] serviceNames = {
        "com.sun.jini.test.impl.start.TestServiceNoConsImpl"
    };

    public void run() throws Exception {
        Object service = null;
	for (int i=0; i < serviceNames.length; i++) {
	    try {
		service = manager.startService(serviceNames[i]);
                throw new TestException(
                        "Unexpected success in creating bad service: "
		        + serviceNames[i]);
	    } catch (Exception e) {
		logger.log(Level.INFO, "Expected exception caught");
                e.printStackTrace();
	    }
	}
        return;
    }
}

