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
public class InboundCallsEnabledRequest implements OutboundAutotRequest {

    private static final long serialVersionUID = 1L;

    public static SerialForm[] serialForm() {
	return new SerialForm[] {
	    new SerialForm("enabled", boolean.class)
	};
    }

    public static void serialize(PutArg arg, InboundCallsEnabledRequest o) throws IOException {
	arg.put("enabled", o.enabled);
	arg.writeArgs();
    }

    boolean enabled;

    InboundCallsEnabledRequest(boolean enabled) {
	this.enabled = enabled;
    }

    /** {@code @AtomicSerial} deserialization constructor. */
    InboundCallsEnabledRequest(GetArg arg) throws IOException, ClassNotFoundException {
	this(arg.get("enabled", false));
    }

    public Object doRequest(AutotHost host) throws Exception {
	host.setTestCallsEnabled(enabled);
	return null;
    }
}
