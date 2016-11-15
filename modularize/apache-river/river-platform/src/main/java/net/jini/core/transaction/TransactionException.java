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

import java.io.IOException;
import org.apache.river.api.io.AtomicException;
import org.apache.river.api.io.AtomicSerial.GetArg;


/**
 * Base class for exceptions thrown during a transaction. 
 *
 * @author Sun Microsystems, Inc.
 *
 * @since 1.0
 */
public class TransactionException extends AtomicException {
    static final long serialVersionUID = -5009935764793203986L;

    /**
     * Constructs an instance with a detail message.
     *
     * @param desc the detail message
     */
    public TransactionException(String desc) {
	super(desc);
    }

    /** Constructs an instance with no detail message. */
    public TransactionException() {
	super();
    }
    
    public TransactionException(String desc, Throwable cause){
        super(desc, cause);
    }
    
    public TransactionException(GetArg arg) throws IOException{
	super(arg);
    }
}
