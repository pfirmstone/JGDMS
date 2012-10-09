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

package com.sun.jini.example.hello;

import net.jini.security.AccessPermission;

/**
 * Represents permissions used to express the access control policy for the
 * Server class. The name specifies the names of the method which you have
 * permission to call using the matching rules provided by AccessPermission.
 *
 * 
 * 
 */
public class ServerPermission extends AccessPermission {

    private static final long serialVersionUID = 2L;

    /**
     * Creates an instance with the specified target name.
     *
     * @param name the target name
     */
    public ServerPermission(String name) {
	super(name);
    }
}
