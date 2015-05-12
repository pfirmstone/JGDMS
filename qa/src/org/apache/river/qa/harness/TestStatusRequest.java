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

package org.apache.river.qa.harness;

import java.io.Serializable;

/**
 * The messages which can be sent to a <code>SlaveTest.</code>
 */
public class TestStatusRequest implements OutboundAutotRequest {

    private String msg;
    private boolean updateSuspended = false;
    private boolean suspended;

    TestStatusRequest(String msg) {
	this.msg = msg;
    }

    TestStatusRequest(String msg, boolean suspended) {
	this.msg = msg;
	this.suspended = suspended;
	updateSuspended = true;
    }

    public Object doRequest(AutotHost host) throws Exception {
	host.setTestState(msg);
	if (updateSuspended) {
	    host.setSuspendedState(suspended);
	}
	return null;
    }
}
