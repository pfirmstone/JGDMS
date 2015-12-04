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

package net.jini.core.lease;

/**
 * An exception used to indicate that a lease is not known to the grantor
 * of the lease.  This can occur when a lease expires or has been cancelled.
 *
 * @author Sun Microsystems, Inc.
 *
 * @since 1.0
 */
public class UnknownLeaseException extends LeaseException {

    private static final long serialVersionUID = -2921099330511429288L;

    /**
     * Constructs an UnknownLeaseException with no detail message.
     */
    public UnknownLeaseException() {
	super();
    }

    /**
     * Constructs an UnknownLeaseException with the specified detail message.
     *
     * @param reason  a detail message
     */
    public UnknownLeaseException(String reason) {
	super(reason);
    }
}
