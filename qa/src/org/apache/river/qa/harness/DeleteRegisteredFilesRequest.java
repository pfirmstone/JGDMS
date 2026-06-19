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
import org.apache.river.api.io.AtomicSerial.Stateless;

/**
 * A <code>HarnessRequest</code> to delete all files which have been
 * registered for deletion with <code>QAConfig</code>.
 */
@AtomicSerial
@Stateless
class DeleteRegisteredFilesRequest implements HarnessRequest {

    private static final long serialVersionUID = 1L;

    /** Normal construction. */
    DeleteRegisteredFilesRequest() { }

    /** {@code @AtomicSerial} deserialization constructor (stateless: no fields). */
    DeleteRegisteredFilesRequest(GetArg arg) throws IOException, ClassNotFoundException { }

    /**
     * Called by the slave test to delete registered files.
     *
     * @param harness the <code>SlaveHarness</code> making the request
     */
    public void doHarnessRequest(SlaveHarness harness) throws Exception {
	QAConfig.getConfig().deleteRegisteredFiles();
    }
}
