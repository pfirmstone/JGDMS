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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Arrays;
import org.apache.river.api.io.AtomicException;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;

/**
 * To enable compatibility with AtomicMarshalInputStream
 */
@AtomicSerial
public class PongException extends AtomicException {
    
    PongException(){
        super();
    }
    
    PongException(GetArg arg) throws IOException, ClassNotFoundException{
        super(check(arg));
    }
    
    private static GetArg check(GetArg arg) throws IOException, ClassNotFoundException {
        AtomicException sup = new AtomicException(arg){};
        StackTraceElement [] trace = sup.getStackTrace();
        if (trace.length > 0) {
	    throw new RuntimeException(
		"TEST FAILED: exception contained non-empty stack trace: " +
		Arrays.asList(trace));
	}
        return arg;
    } 
    
    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();

	/*
	 * Verify right at unmarshalling time that this exception instance
	 * contains no stack trace data from the server (regardless of whether
	 * or not it would be apparent at the RMI client application level).
	 */
	StackTraceElement[] trace = getStackTrace();
	if (trace.length > 0) {
	    throw new RuntimeException(
		"TEST FAILED: exception contained non-empty stack trace: " +
		Arrays.asList(trace));
	}
    }
}
