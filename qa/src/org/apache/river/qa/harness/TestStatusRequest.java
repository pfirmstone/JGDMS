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

import java.io.IOException;
import java.io.Serializable;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;

/**
 * The messages which can be sent to a <code>SlaveTest.</code>
 */
@AtomicSerial
public class TestStatusRequest implements OutboundAutotRequest {

    private static final long serialVersionUID = 1L;

    public static SerialForm[] serialForm() {
	return new SerialForm[] {
	    new SerialForm("msg", String.class),
	    new SerialForm("updateSuspended", boolean.class),
	    new SerialForm("suspended", boolean.class)
	};
    }

    public static void serialize(PutArg arg, TestStatusRequest o) throws IOException {
	arg.put("msg", o.msg);
	arg.put("updateSuspended", o.updateSuspended);
	arg.put("suspended", o.suspended);
	arg.writeArgs();
    }

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

    /** {@code @AtomicSerial} deserialization constructor. */
    TestStatusRequest(GetArg arg) throws IOException, ClassNotFoundException {
	this.msg = arg.get("msg", null, String.class);
	this.updateSuspended = arg.get("updateSuspended", false);
	this.suspended = arg.get("suspended", false);
    }

    public Object doRequest(AutotHost host) throws Exception {
	host.setTestState(msg);
	if (updateSuspended) {
	    host.setSuspendedState(suspended);
	}
	return null;
    }
}
