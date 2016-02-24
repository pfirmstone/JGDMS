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

package org.apache.river.api.io;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamField;
import org.apache.river.api.io.AtomicSerial.GetArg;

/**
 * Although most Throwable classes are serialized over AtomicMarshalOutputStream,
 * only Throwable's fields are transferred.  If an Exception needs to serialize
 * additional state, then AtomicException provides an abstract superclass,
 * that serializes Throwable's fields for convenience.
 * 
 * @author peter
 */
@AtomicSerial
public abstract class AtomicException extends Exception {
    private static final long serialVersionUID = 1L;
    
    /**
     * In earlier versions the extra fields will duplicate those of Throwable,
     * so only ref id's will be sent, so the objects these fields refer to will
     * only be sent once.
     */
    private static final ObjectStreamField[] serialPersistentFields = 
	{
	    new ObjectStreamField("message", String.class),
	    new ObjectStreamField("cause", Throwable.class),
	    new ObjectStreamField("stack", StackTraceElement[].class),
	    new ObjectStreamField("suppressed", Throwable[].class)
	}; 

    protected AtomicException(String message, Throwable cause,
                        boolean enableSuppression,
                        boolean writableStackTrace) 
    {
        super(message, cause, enableSuppression, writableStackTrace);
    }
    
    protected AtomicException(String message, Throwable cause){
	super(message, cause);
    }
    
    protected AtomicException(String message){
	super(message);
    }
    
    protected AtomicException(){
	super();
    }
    
    protected AtomicException(Throwable cause){
	super(cause);
    }
    
    protected AtomicException(GetArg arg) throws IOException{
	this(arg.get("message", null, String.class),
	     arg.get("cause", null, Throwable.class),
	     arg.get("stack", null, StackTraceElement[].class),
	     arg.get("suppressed", null, Throwable[].class)
	);
    }
    
    private AtomicException(String message, 
			      Throwable cause,
			      StackTraceElement[] stack,
			      Throwable[] suppressed) 
    {
        super(message, cause, true, true);
	if (stack != null) this.setStackTrace(stack); // defensively copied.2
	if (suppressed != null){
	    for (int i = 0, l = suppressed.length; i < l; i++){
		this.addSuppressed(suppressed[i]);
	    }
	}
    }
    
    private void writeObject(ObjectOutputStream out) throws IOException {
	ObjectOutputStream.PutField pf = out.putFields();
	pf.put("message", super.getMessage());
	pf.put("cause", super.getCause());
	pf.put("stack", super.getStackTrace());
	pf.put("suppressed", super.getSuppressed());
	out.writeFields();
    }
    
    private void readObject(ObjectInputStream in) 
	throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();
    }
}
