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
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;

/**
 * A <code>HarnessRequest</code> to start the <code>SlaveTest</code>.
 */
@AtomicSerial
class SlaveTestRequest implements HarnessRequest {

    private static final long serialVersionUID = 1L;

    public static SerialForm[] serialForm() {
	return new SerialForm[] {
	    new SerialForm("config", QAConfig.class)
	};
    }

    public static void serialize(PutArg arg, SlaveTestRequest o) throws IOException {
	arg.put("config", o.config);
	arg.writeArgs();
    }

    /** the <code>QAConfig</code> object to supply to the slave test */
    private QAConfig config;

    /** 
     * Construct the request.
     *
     * @param config   the <code>QAConfig</code> object to pass to 
     *                 the <code>SlaveTest</code>
     */
    SlaveTestRequest(QAConfig config) {
	this.config = config;
    }
    
    SlaveTestRequest(GetArg arg) throws IOException, ClassNotFoundException{
	this(arg.get("config", null, QAConfig.class));
    }

    /**
     * Called by the <code>SlaveHarness</code> after unmarshalling
     * this object. The <code>SlaveHarness.startSlaveTest</code>
     * method is called.
     *
     * @param harness a reference to the slave harness
     */
    public void doHarnessRequest(SlaveHarness harness) throws Exception {
	harness.startSlaveTest(config);
    }
}
