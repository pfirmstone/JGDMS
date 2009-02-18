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

package com.sun.jini.qa.harness;

import java.io.Serializable;

/**
 * A <code>SlaveTestRequest</code> which registers an override provider
 * on the slave.
 */
class AddOverrideProviderRequest implements SlaveRequest {

    /** the override provider */
    private OverrideProvider provider;

    /**
     * Construct the request.
     *
     * @param provider the override provider to register
     */
    public AddOverrideProviderRequest(OverrideProvider provider) {
	this.provider = provider;
    }

    /**
     * Called by the <code>SlaveTest</code> to register the override provider
     * for the test.
     *
     * @param slaveTest a reference to the <code>SlaveTest</code>.
     * @return <code>null</code>
     * @throws Exception never
     */
    public Object doSlaveRequest(SlaveTest slaveTest) throws Exception {
	QAConfig config = slaveTest.getConfig();
	config.addOverrideProvider(provider);
	return null;
    }
}
