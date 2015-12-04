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
 * An exception generated when a lease request or renewal is denied. 
 *
 * @author Sun Microsystems, Inc.
 *
 * @since 1.0
 */
public class LeaseDeniedException extends LeaseException {

    private static final long serialVersionUID = 5704943735577343495L;

    /**
     * Constructs an LeaseDeniedException with no detail message.
     */
    public LeaseDeniedException() {
	super();
    }

    /**
     * Constructs an LeaseDeniedException with the specified detail message.
     * 
     * @param reason a <tt>String</tt> containing the reason why the lease
     *                 was denied
     */
    public LeaseDeniedException(String reason) {
	super(reason);
    }
}
