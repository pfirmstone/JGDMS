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
package net.jini.core.transaction.server;

import net.jini.core.transaction.*;


/**
 * Exception thrown when a transaction cannot be joined because the
 * participant's current crash count is different from the crash
 * count the manager received in a previous join by that participant.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see TransactionManager#join
 * @see ServerTransaction#join
 *
 * @since 1.0
 */
public class CrashCountException extends TransactionException {
    static final long serialVersionUID = 4299226125245015671L;

    /**
     * Constructs an instance with a detail message.
     *
     * @param reason the detail message
     */
    public CrashCountException (String reason) {
	super(reason);
    }

    /** Constructs an instance with no detail message. */
    public CrashCountException() {
	super();
    }
}
