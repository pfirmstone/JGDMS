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
package com.sun.jini.test.impl.reliability;

import java.rmi.RemoteException;
import java.util.logging.Logger;
import java.util.logging.Level;

import net.jini.config.Configuration;
import net.jini.export.Exporter;

import com.sun.jini.qa.harness.QAConfig;

/**
 * The OrangeEchoImpl class implements the behavior of the remote "orange
 * echo" objects exported by the server.  The purpose of these objects
 * is simply to recursively call back to their caller.
 */
public class OrangeEchoImpl implements OrangeEcho {

    private static final Logger logger = 
	Logger.getLogger("com.sun.jini.qa.harness");
    private final String name;
    private OrangeEcho stub;

    public OrangeEchoImpl(String name) throws RemoteException {
	this.name = name;
    }

    /**
     * Exports this instance.
     */
    public OrangeEcho export() throws Exception {
        Exporter exporter = null;
		Configuration c = QAConfig.getConfig().getConfiguration();
	// check for none cofiguration
	if (!(c instanceof com.sun.jini.qa.harness.QAConfiguration)) {
            exporter = QAConfig.getDefaultExporter();
	} else {
            exporter = (Exporter) c.getEntry("test",
                                             "reliabilityExporter",
                                             Exporter.class);
	}
	stub = (OrangeEcho) exporter.export(this);
	return stub;
    }

    /**
     * Call back on supplied "orange" object (presumably the caller)
     * with the same message data and a decremented recursion level.
     */
    public int[] recurse(Orange orange, int[] message, int level)
	throws RemoteException
    {
	String threadName = Thread.currentThread().getName();

	logger.log(Level.FINEST,
	    threadName + ": " + toString() + ".recurse(message[" 
	    + message.length + "], " + level + "): BEGIN");

	int[] response = orange.recurse(stub, message, level - 1);

	logger.log(Level.FINEST,
	    threadName + ": " + toString() + ".recurse(message[" 
	    + message.length + "], " + level + "): END");

	return response;
    }

    public String toString() {
	return name;
    }
}
