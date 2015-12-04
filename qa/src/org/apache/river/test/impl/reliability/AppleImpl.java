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
package org.apache.river.test.impl.reliability;

import java.rmi.RemoteException;
import java.util.logging.Logger;
import java.util.logging.Level;

import net.jini.config.Configuration;
import net.jini.export.Exporter;

import org.apache.river.qa.harness.QAConfig;


/**
 * The AppleImpl class implements the behavior of the remote "apple"
 * objects exported by the application.
 */
public class AppleImpl implements Apple {

    private static final Logger logger = Logger.getLogger("org.apache.river.qa.harness");
    private final String name;
    private Apple stub;
    
    public AppleImpl(String name) throws RemoteException {
	this.name = name;
    }

    /**
     * Exports this instance.
     */
    public Apple export() throws Exception {
	Exporter exporter = null;
	Configuration c = QAConfig.getConfig().getConfiguration();
	// check for none cofiguration
	if (!(c instanceof org.apache.river.qa.harness.QAConfiguration)) {
	    exporter = QAConfig.getDefaultExporter();
	} else {
            exporter = (Exporter) c.getEntry("test",
                                             "reliabilityExporter",
                                             Exporter.class);
	}
	stub = (Apple) exporter.export(this);
	return stub;
    }

    /**
     * Receive an array of AppleEvent objects.
     */
    public void notify(AppleEvent[] events) {
	String threadName = Thread.currentThread().getName();
	logger.log(Level.FINEST,
	    threadName + ": " + toString() + ".notify: BEGIN");

	for (int i = 0; i < events.length; i++) {
	    logger.log(Level.FINEST,
		threadName + ": " + toString() + ".notify(): events[" 
		+ i + "] = " + events[i].toString());
	}

	logger.log(Level.FINEST,
	    threadName + ": " + toString() + ".notify(): END");
    }

    /**
     * Return a newly created and exported orange implementation.
     */
    public Orange newOrange(String name) throws Exception {
	String threadName = Thread.currentThread().getName();
	logger.log(Level.FINEST,
	    threadName + ": " + toString() + ".newOrange(" + name + "): BEGIN");

	Orange orangeProxy = (new OrangeImpl(name)).export();
	    
	logger.log(Level.FINEST,
	    threadName + ": " + toString() + ".newOrange(" + name + "): END");

	return orangeProxy;
    }

    public String toString() {
	return name;
    }
}
