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
import java.io.ObjectOutputStream;
import java.io.ObjectStreamField;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;


/**
 * Exception thrown when a transaction timeout has expired.
 *
 * @author Sun Microsystems, Inc.
 *
 * @since 1.0
 */
@AtomicSerial
public class TimeoutExpiredException extends TransactionException {
    static final long serialVersionUID = 3918773760682958000L;
    
    /**
     * By defining serial persistent fields, we don't need to use transient fields.
     * All fields can be final and this object becomes immutable.
     * 
     * In earlier versions the extra fields will duplicate those of Throwable,
     * so only ref id's will be sent, the objects these fields refer to will
     * only be sent once.
     */
    private static final ObjectStreamField[] serialPersistentFields = 
	{
	    new ObjectStreamField("committed", Boolean.TYPE)
	}; 

    /**
     * True if the transaction committed before the timeout.
     *
     * @serial
     */
    public final boolean committed;

    /**
     * Constructs an instance with no detail message.
     *
     * @param committed whether the transaction committed before the timeout.
     */
    public TimeoutExpiredException(boolean committed) {
        this.committed = committed;
    }

    /**
     * Constructs an instance with a detail message.
     *
     * @param desc the detail message
     * @param committed whether the transaction committed before the timeout.
     */
    public TimeoutExpiredException(String desc, boolean committed) {
        super(desc);
        this.committed = committed;
    }
    
     /**
     * AtomicSerial constructor
     * @param arg
     * @throws IOException 
     */
    public TimeoutExpiredException(GetArg arg) throws IOException{
	super(check(arg));
	committed = arg.get("committed", false);
    }
    
    private static GetArg check(GetArg arg) throws IOException{
	arg.get("committed", false); // Check committed field exists
	return arg;
    }
    
    private void writeObject(ObjectOutputStream out) throws IOException {
	ObjectOutputStream.PutField pf = out.putFields();
	pf.put("committed", committed);
	out.writeFields();
    }
}
