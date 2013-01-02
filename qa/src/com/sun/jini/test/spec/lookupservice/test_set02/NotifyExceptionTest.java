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
package com.sun.jini.test.spec.lookupservice.test_set02;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;

import java.util.logging.Level;
import com.sun.jini.qa.harness.TestException;

import com.sun.jini.test.spec.lookupservice.QATestRegistrar;
import net.jini.core.event.RemoteEvent;
import net.jini.core.event.RemoteEventListener;
import java.rmi.RemoteException;
import java.rmi.NoSuchObjectException;

/** This class is used as a superclass for testing notify exceptions. */
public abstract class NotifyExceptionTest extends QATestRegistrar {

    protected RemoteEventListener listener;

    /** Do-nothing remote event listener */
    public class Listener extends BasicListener
                          implements RemoteEventListener
    {
        public Listener() throws RemoteException {
            super();
        }

	public void notify(RemoteEvent ev) {
	}
    }

    /**
     * Performs actions necessary to prepare for execution of the 
     * current QA test.
     *
     * Creates the lookup service and a do-nothing remote event listener.
     */
    public Test construct(QAConfig sysConfig) throws Exception {
	super.construct(sysConfig);
	listener = new Listener();
        return this;
    }

    /** Performs cleanup actions necessary to achieve a graceful exit of 
     *  the current QA test.
     *
     *  Unexports the listener and then performs any remaining standard
     *  cleanup duties.
     */
    public void tearDown() {
	try {
	    unexportListener(listener, true);
	} finally {
	    super.tearDown();
	}
    }
}
