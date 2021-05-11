/*
 * Copyright 2018 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;

/**
 *
 * @author peter
 */
@AtomicSerial
public class ArrayClassNotFoundException extends ClassNotFoundException {
    private static final long serialVersionUID = 1L;
    
    private static final String MESSAGE = "message";
    private static final String ARR = "arr";
    private static final String EXCEPTION_INDEXES = "exceptionIndexes";
    private static final String EXCEPTIONS = "exceptions";
    
    public static SerialForm [] serialForm(){
        return new SerialForm []{
            new SerialForm(MESSAGE, String.class),
            new SerialForm(ARR, Object[].class),
            new SerialForm(EXCEPTION_INDEXES, int[].class),
            new SerialForm(EXCEPTIONS, ClassNotFoundException[].class)
        };
    }
    
    public static void serialize(AtomicSerial.PutArg arg, ArrayClassNotFoundException e) throws IOException{
        putArgs(arg, e);
        arg.writeArgs();
    }
    
    public static void putArgs(ObjectOutputStream.PutField pf, ArrayClassNotFoundException e){
        pf.put(MESSAGE, e.message());
	pf.put(ARR, e.arr);
        pf.put(EXCEPTION_INDEXES, e.exceptionIndexes);
        pf.put(EXCEPTIONS, e.exceptions);
    }
    
    private final Object[] arr;
    private final int[] exceptionIndexes;
    private final ClassNotFoundException[] exceptions;
    
    ArrayClassNotFoundException(String message,
				Object[] arr,
				int [] exceptionIndexes,
				ClassNotFoundException [] exceptions)
    {
	super(message);
	this.arr = arr;
	this.exceptionIndexes = exceptionIndexes;
	this.exceptions = exceptions;
    }
    
    private static int[] intArray(List<Integer> intList){
	int [] exceptionIndexes = new int[intList.size()];
	Iterator<Integer> it = intList.iterator();
	for (int i = 0; it.hasNext(); i++){
	    exceptionIndexes[i] = it.next().intValue();
	}
	return exceptionIndexes;
    }
    
    ArrayClassNotFoundException(String message,
				Object[] arr,
				List<Integer> exceptionIndexes,
				List<ClassNotFoundException> exceptions)
    {
	this(message,
	     arr,
	     intArray(exceptionIndexes),
	     exceptions.toArray(new ClassNotFoundException[exceptions.size()])
	);
    }
    
    ArrayClassNotFoundException(GetArg arg) throws IOException, ClassNotFoundException{
	this(
	    arg.get("message", null, String.class),
	    arg.get("arr", new Object[0], Object[].class).clone(),
	    arg.get("exceptionIndexes", new int[0], int[].class).clone(),
	    arg.get("exceptions", new ClassNotFoundException[0], ClassNotFoundException[].class)
	);
    }
    
    public Object [] getArray(){
	return arr.clone();
    }
    
    public int[] exceptionIndexes(){
	return exceptionIndexes.clone();
    }
    
    public ClassNotFoundException [] getExceptions(){
	return exceptions.clone();
    }
    
    private String message(){
        return super.getMessage();
    }
    
    @Override
    public String getMessage(){
	StringBuilder sb = new StringBuilder(256);
	sb.append(super.getMessage());
	sb.append(Arrays.toString(exceptions));
	return sb.toString();
    }
}
