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
/*  */

import org.apache.river.api.io.AtomicRuntimeException;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;


/**
 * Simple class to wrap test failure exceptions in a RuntimeException.
 * Provides a detail exception and a message.
 */
@AtomicSerial
public class TestFailedException extends AtomicRuntimeException {

    public TestFailedException() {
        super();
    }

    public TestFailedException(String s) {
	super(s);
    }

    public TestFailedException(String s, Throwable ex) {
	super(s, ex);
    }
    
    public TestFailedException(GetArg arg){
        super(arg);
    }

    public String getMessage() {
	if (super.getCause() == null)
	    return super.getMessage();
	else
	    return super.getMessage() +
		"; nested exception is: \n\t" +
		super.getCause().toString();
    }

    public void printStackTrace(java.io.PrintStream ps)
    {
	if (super.getCause() == null) {
	    super.printStackTrace(ps);
	} else {
	    synchronized(ps) {
		ps.println(this);
		super.getCause().printStackTrace(ps);
	    }
	}
    }

    public void printStackTrace()
    {
	printStackTrace(System.err);
    }

    public void printStackTrace(java.io.PrintWriter pw)
    {
	if (super.getCause() == null) {
	    super.printStackTrace(pw);
	} else {
	    synchronized(pw) {
		pw.println(this);
		super.getCause().printStackTrace(pw);
	    }
	}
    }
}
