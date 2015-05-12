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
 * A provider of configuration override strings to be passed in the
 * argument list when a service is started. An
 * <code>OverrideProvider</code> can be established by a test by
 * calling the <code>addOverrideProvider</code> method on the
 * <code>QAConfig</code> object. Admins call the corresponding
 * <code>getOverrideProvider</code> method during construction of the
 * argument list. 
 */
public interface OverrideProvider extends Serializable {

    /**
     * Provide the overrides to pass when obtaining a <code>Configuration</code>
     * from a configuration provider. The <code>serviceName</code> and instance
     * count are passed when the override is to apply to a service; for a test
     * override, <code>serviceName</code> must be <code>null</code> and the
     * instance count is ignored. The overrides are provided as pairs in the
     * returned string array. The first element of the pair is the fully
     * qualified entry name, and the second is the entry value. The '='
     * character is omitted.
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
	throws TestException;
}
