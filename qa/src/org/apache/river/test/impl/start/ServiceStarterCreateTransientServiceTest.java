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

import org.apache.river.qa.harness.Test;
import java.util.logging.Level;

import org.apache.river.qa.harness.TestException;
import org.apache.river.start.ServiceStarter;
import org.apache.river.start.NonActivatableServiceDescriptor;

import java.io.File;
import java.util.Arrays;

import net.jini.event.EventMailbox;
import net.jini.config.EmptyConfiguration;

public class ServiceStarterCreateTransientServiceTest extends StarterBase implements Test {
    private static String[] serviceNames = {
	"net.jini.core.lookup.ServiceRegistrar",
	"net.jini.discovery.LookupDiscoveryService",
	"net.jini.lease.LeaseRenewalService",
	"net.jini.space.JavaSpace",
	"net.jini.event.EventMailbox",
	"net.jini.core.transaction.server.TransactionManager"
    };
    public void run() throws Exception {
        Object service = null;
	for (int i=0; i < serviceNames.length; i++) {
            service = getManager().startService(serviceNames[i]);
            logger.log(Level.INFO, "Created service:" + service );
	}
        return;
    }
}

