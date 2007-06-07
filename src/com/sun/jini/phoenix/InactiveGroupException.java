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

package com.sun.jini.phoenix;

import java.rmi.activation.ActivationException;

/**
 * Thrown if a local or remote call is made on a group implementation
 * instance that is inactive.
 *
 * @author Sun Microsystems, Inc.
 * 
 * @since 2.0
 */
public class InactiveGroupException extends ActivationException {
    private static final long serialVersionUID = -4596896675720356592L;

    /**
     * Constructs an instance with the specified detail message.
     *
     * @param s the detail message
     */
    public InactiveGroupException(String s) {
	super(s);
    }
}
