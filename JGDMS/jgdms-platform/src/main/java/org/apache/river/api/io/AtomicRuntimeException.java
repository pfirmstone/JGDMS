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
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;

/**
 * Although most Throwable classes are serialized over AtomicMarshalOutputStream,
 * only Throwable's fields are transferred.  If an Exception needs to serialize
 * additional state, then AtomicException provides an abstract superclass,
 * that serializes Throwable's fields for convenience.
 * 
 * @author Peter Firmstone
 * @since 3.1.1
 */
@AtomicSerial
public abstract class AtomicRuntimeException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    
    /**
     * In earlier versions the extra fields will duplicate those of Throwable,
     * so only ref id's will be sent, so the objects these fields refer to will
     * only be sent once.
     */
    private static final ObjectStreamField[] serialPersistentFields = 
	serialForm(); 
    
    public static SerialForm[] serialForm(){
        return new SerialForm[]{
            new SerialForm("message", String.class),
	    new SerialForm("cause", Throwable.class),
	    new SerialForm("stack", StackTraceElement[].class),
	    new SerialForm("suppressed", Throwable[].class)
        };
    }
    
    public static void serialize(PutArg arg, AtomicRuntimeException e) throws IOException{
        putFields(arg, e);
        arg.writeArgs();
    }
    
    private static void putFields(ObjectOutputStream.PutField pf, AtomicRuntimeException e){
        pf.put("message", e.getMessage());
	Throwable cause = e.getCause();
	if (cause == e) cause = null;
	pf.put("cause", cause);
	pf.put("stack", e.getStackTrace());
	pf.put("suppressed", e.getSuppressed());
    }

    protected AtomicRuntimeException(String message, Throwable cause,
                        boolean enableSuppression,
                        boolean writableStackTrace) 
    {
        super(message, cause, enableSuppression, writableStackTrace);
    }
    
    protected AtomicRuntimeException(String message, Throwable cause){
	super(message, cause);
    }
    
    protected AtomicRuntimeException(String message){
	super(message);
    }
    
    protected AtomicRuntimeException(){
	super();
    }
    
    protected AtomicRuntimeException(Throwable cause){
	super(cause);
    }
    
    protected AtomicRuntimeException(GetArg arg) throws IOException, ClassNotFoundException{
	this(arg.get("message", null, String.class),
	     arg.get("cause", null, Throwable.class),
	     arg.get("stack", null, StackTraceElement[].class),
	     arg.get("suppressed", null, Throwable[].class)
	);
    }
    
    private AtomicRuntimeException(String message, 
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
	putFields(out.putFields(), this);
	out.writeFields();
    }
    
    private void readObject(ObjectInputStream in) 
	throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();
    }
}
