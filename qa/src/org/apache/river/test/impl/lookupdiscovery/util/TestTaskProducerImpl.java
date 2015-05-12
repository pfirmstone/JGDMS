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
package org.apache.river.test.impl.lookupdiscovery.util;
import org.apache.river.qa.harness.TestException;
import org.apache.river.test.impl.lookupdiscovery.RestoreContextForTasks;
import org.apache.river.thread.TaskManager;
import org.apache.river.thread.WakeupManager;
import net.jini.config.Configuration;
import net.jini.discovery.DiscoveryEvent;
import net.jini.discovery.DiscoveryGroupManagement;
import net.jini.discovery.DiscoveryListener;
import net.jini.discovery.LookupDiscovery;
public class TestTaskProducerImpl implements 
    RestoreContextForTasks.TestTaskProducer
{
    private Configuration config;
    private static final String LDCOMP = "net.jini.discovery.LookupDiscovery";
    public LookupDiscovery setup(Configuration config) throws Exception {
	this.config = config;
	return new LookupDiscovery(DiscoveryGroupManagement.NO_GROUPS, config);
    }
    
    public void run() throws Exception {
	RestoreContextForTasks.DangerousTask t =
		new RestoreContextForTasks.DangerousTask();
	// Ok now get the task manager
	TaskManager tm = (TaskManager) config.getEntry(
	    LDCOMP,
	    "taskManager", TaskManager.class);
	tm.add(t);
	synchronized (t) {
	    while (!t.done) {
		t.wait();
	    }
	}
	if (!t.securityException) {
	    throw new TestException("No securityException");
	}
	RestoreContextForTasks.DangerousRunnable r =
		new RestoreContextForTasks.DangerousRunnable();
	// Next try the Wakeup manager
	WakeupManager wm = (WakeupManager) config.getEntry(
	    LDCOMP,
	    "wakeupManager", WakeupManager.class);
	wm.schedule(System.currentTimeMillis(), r);
	synchronized (r) {
	    while (!r.done) {
		r.wait();
	    }
	}
	if (!r.securityException) {
	    throw new TestException("No securityException");
	}
    }
}
