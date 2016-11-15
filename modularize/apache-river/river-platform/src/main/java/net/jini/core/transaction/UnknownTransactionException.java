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
package net.jini.core.transaction;


/**
 * Exception thrown when a transaction is not recognized as a valid
 * or known transaction.  Common reasons for this include: the transaction
 * has previously been explicitly aborted; the transaction has previously
 * aborted because the transaction manager crashed; the transaction has
 * previously been explicitly committed.
 *
 * @author Sun Microsystems, Inc.
 *
 * @since 1.0
 */
public class UnknownTransactionException extends TransactionException {
    static final long serialVersionUID = 443798629936327009L;

    /**
     * Constructs an instance with a detail message.
     *
     * @param desc the detail message
     */
    public UnknownTransactionException(String desc) {
	super(desc);
    }

    /** Constructs an instance with no detail message. */
    public UnknownTransactionException() {
	super();
    }
    
    public UnknownTransactionException(String desc, Throwable cause){
        super(desc, cause);
    }
}
