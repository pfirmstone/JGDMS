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

package com.sun.jini.discovery;

import java.io.IOException;

/**
 * Exception indicating a discovery protocol error, such as failure to
 * interpret packet data, or multicast request/announcement data that cannot
 * fit within a given packet length.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
public class DiscoveryProtocolException extends IOException {

    private static final long serialVersionUID = 5142921342798824511L;

    /**
     * Constructs a new <code>DiscoveryProtocolException</code> with the
     * specified detail message.
     */
    public DiscoveryProtocolException(String message) {
	super(message);
    }

    /**
     * Constructs a new <code>DiscoveryProtocolException</code> with the
     * specified detail message and cause.
     */
    public DiscoveryProtocolException(String message, Throwable cause) {
	super(message);
	initCause(cause);
    }
}
