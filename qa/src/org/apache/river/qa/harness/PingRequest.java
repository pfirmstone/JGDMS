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
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.Stateless;

/**
 * A <code>SlaveRequest</code> which probes for liveness of a
 * <code>SlaveTest</code>.
 */
@AtomicSerial
@Stateless
class PingRequest implements SlaveRequest {

    private static final long serialVersionUID = 1L;

    /** Normal construction. */
    PingRequest() { }

    /** {@code @AtomicSerial} deserialization constructor (stateless: no fields). */
    PingRequest(GetArg arg) throws IOException, ClassNotFoundException { }

    /**
     * Perform no action, and return null.
     *
     * @param slaveTest a reference to the calling <code>SlaveTest</code>
     *               SlaveTest
     */
    public Object doSlaveRequest(SlaveTest slaveTest) throws Exception {
	return null;
    }
}
