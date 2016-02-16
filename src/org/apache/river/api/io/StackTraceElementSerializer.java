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
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.io.ObjectStreamField;
import java.io.Serializable;
import java.util.Objects;
import org.apache.river.api.io.AtomicSerial.GetArg;

/**
 *
 * @author peter
 */
@Serializer(replaceObType = StackTraceElement.class)
@AtomicSerial
class StackTraceElementSerializer implements Serializable {
    private static final long serialVersionUID = 1L;
    
    /**
     * By defining serial persistent fields, we don't need to use transient fields.
     * All fields can be final and this object becomes immutable.
     */
    private static final ObjectStreamField[] serialPersistentFields = 
	{
	    new ObjectStreamField("declaringClass", String.class),
	    new ObjectStreamField("methodName", String.class),
	    new ObjectStreamField("fileName", String.class),
	    new ObjectStreamField("lineNumber", Integer.TYPE)
	};
    
    private final /*transient*/ StackTraceElement stackTraceElement;
    private final String declaringClass;
    private final String methodName;
    private final String fileName;
    private final int lineNumber;
    
    StackTraceElementSerializer(StackTraceElement e){
	this.stackTraceElement = e;
	declaringClass = e.getClassName();
	methodName = e.getMethodName();
	fileName = e.getFileName();
	lineNumber = e.getLineNumber();
    }
    
    StackTraceElementSerializer(String declaringClass,
				String methodName, 
				String fileName, 
				int lineNumber)
    {
	this(new StackTraceElement(declaringClass, methodName, fileName, lineNumber));
    }
    
    /**
     * Public modifier here to remind us that this is the method the public 
     * can effectively call.
     * @param arg
     * @throws IOException 
     */
    public StackTraceElementSerializer(GetArg arg) throws IOException{
	this(Valid.notNull(arg.get("declaringClass", null, String.class),
		"declaring class cannot be null"),
	    Valid.notNull(arg.get("methodName", null, String.class),
		    "method name cannot be null"),
	    arg.get("fileName", null, String.class),
	    arg.get("lineNumber", 0)
	);
    }
    
    @Override
    public boolean equals (Object obj){
	if (this == obj) return true;
	if (!(obj instanceof StackTraceElementSerializer)) return false;
	StackTraceElementSerializer that = (StackTraceElementSerializer) obj;
	if (!(Objects.equals(declaringClass, that.declaringClass))) return false;
	if (!(Objects.equals(methodName, that.methodName))) return false;
	if (!(Objects.equals(fileName, that.fileName))) return false;
	return lineNumber == that.lineNumber;
    }

    @Override
    public int hashCode() {
	int hash = 7;
	hash = 97 * hash + Objects.hashCode(this.declaringClass);
	hash = 97 * hash + Objects.hashCode(this.methodName);
	hash = 97 * hash + Objects.hashCode(this.fileName);
	hash = 97 * hash + this.lineNumber;
	return hash;
    }
    
    Object readResolve() throws ObjectStreamException {
	if (stackTraceElement != null) return stackTraceElement;
	return new StackTraceElement(declaringClass, methodName, fileName, lineNumber);
    }
    
    /**
     * @serialData 
     * @param out
     * @throws IOException 
     */
    private void writeObject(ObjectOutputStream out) throws IOException {
	ObjectOutputStream.PutField pf = out.putFields();
	pf.put("declaringClass", declaringClass);
	pf.put("methodName", methodName);
	pf.put("fileName", fileName);
	pf.put("lineNumber", lineNumber);
	out.writeFields();
    }
    
}
