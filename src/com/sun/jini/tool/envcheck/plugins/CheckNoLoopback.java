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
package com.sun.jini.tool.envcheck.plugins;

import com.sun.jini.tool.envcheck.AbstractPlugin;
import com.sun.jini.tool.envcheck.Plugin;
import com.sun.jini.tool.envcheck.EnvCheck;
import com.sun.jini.tool.envcheck.Reporter;
import com.sun.jini.tool.envcheck.Reporter.Message;
import java.net.InetAddress;

/**
 * Checks that the return from <code>InetAddress.getLocalHost()</code>
 * is not a loopback address.
 */
public class CheckNoLoopback extends AbstractPlugin {

    /**
     * Get the address of the local host and check for loopback.
     */
    public void run(EnvCheck envCheck) {
	Message message;
	try {
	    InetAddress localAddress = InetAddress.getLocalHost();
	    if (localAddress.isLoopbackAddress()) {
		message = new Message(Reporter.WARNING,
				      getString("isALoopback",
						localAddress.toString()),
				      getString("explanationString"));
	    } else {
		message = new Message(Reporter.INFO,
				      getString("notALoopback"),
				      getString("explanationString"));
	    }
	} catch (Exception e) {
	    message = new Message(Reporter.ERROR,
				  getString("cantResolve"),
				  e,
				  getString("explanationString"));
	    System.err.println(getString("cantResolve"));
	}
	Reporter.print(message);
    }
}
	

