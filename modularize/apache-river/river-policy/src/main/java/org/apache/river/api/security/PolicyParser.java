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

package org.apache.river.api.security;

import java.net.URL;
import java.util.Collection;
import java.util.Properties;

/**
 * Parser of policy syntax.
 * 
 * @see PermissionGrant
 * @see URL
 * @see Properties
 * @see ConcurrentPolicyFile
 * 
 * @author Peter Firmstone
 * @since 3.0.0
 */
public interface PolicyParser {

    /**
     * Parses a given location, making use of system properties as necessary and
     * returns a collection of <code>PermissionGrant</code>'s
     *
     * @param location an URL of a policy file to be loaded
     * @param system system properties, used for property expansion
     * @return a collection of PermissionGrant objects, may be empty
     * @throws Exception IO error while reading location or file syntax error
     */
    Collection<PermissionGrant> parse(URL location, Properties system) throws Exception;

}
