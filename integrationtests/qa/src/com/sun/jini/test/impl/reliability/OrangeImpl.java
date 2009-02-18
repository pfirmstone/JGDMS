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
 * The OrangeImpl class implements the behavior of the remote "orange"
 * objects exported by the appplication.
 */
public class OrangeImpl implements Orange {

    private static final Logger logger = Logger.getLogger("com.sun.jini.qa.harness");
    private final String name;
    private Orange stub;

    public OrangeImpl(String name) throws RemoteException {
	this.name = name;
    }

    /**
     * Exports this instance.
     */
    public Orange export() throws Exception {
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
	stub = (Orange) exporter.export(this);
	return stub;
    }

    /**
     * Return inverted message data, call through supplied OrangeEcho
     * object if not at recursion level zero.
     */
    public int[] recurse(OrangeEcho echo, int[] message, int level)
	throws RemoteException
    {
	String threadName = Thread.currentThread().getName();
	logger.log(Level.FINEST,
	    threadName + ": " + toString() + ".recurse(message[" 
	    + message.length + "], " + level + "): BEGIN");

	int[] response;
	if (level > 0) {
	    response = echo.recurse(stub, message, level);
	} else {
	    for (int i = 0; i < message.length; i++) {
		message[i] = ~message[i];
	    }
	    response = message;
	}

	logger.log(Level.FINEST,
	    threadName + ": " + toString() + ".recurse(message[" 
	    + message.length + "], " + level + "): END");

	return response;
    }

    public String toString() {
	return name;
    }
}
