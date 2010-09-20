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

import java.util.Random;

/**
 * An <code>OverrideProvider</code> which supplies the value for
 * the <code>com.sun.jini.outrigger.store</code> configuration entry.
 */
public class StoreOverrideProvider implements OverrideProvider {

    /**
     * Return a constructor for snaplogstore
     *
     * @param config the test config object
     * @param serviceName the service name, or <code>null</code> for a test 
                          override
     * @param index the instance count for the service
     *
     * @return the array of override strings, which must not be 
     *         <code>null</code>, but may be of length 0
     * @throws TestException if a fatal error occurs
     */
    public String[] getOverrides(QAConfig config, String serviceName, int index)
	throws TestException
    {
	if ("net.jini.space.JavaSpace".equals(serviceName)) {
	    String snapStore = "com.sun.jini.outrigger.snaplogstore.LogStore";
	    String[] ret = new String[2];
	    ret[0] = "com.sun.jini.outrigger.store";
	    ret[1] = "new " + snapStore + "(this)";
	    return ret;
	}
	return new String[0];
    }
}
