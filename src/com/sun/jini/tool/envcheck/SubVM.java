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
package com.sun.jini.tool.envcheck;

import java.io.ObjectOutputStream;
import java.io.PrintStream;

/**
 * A container for a subtask which runs in a separate and returns a
 * single object by writing that object to <code>System.out</code>.
 */
public class SubVM {

    private static PrintStream origOut;

    /**
     * Entry point for the subtask. The first element of <code>args</code> must
     * name a <code>SubVMTask</code> to run; the remaining elements comprise the
     * arguments for the subtask.  That task is instantiated, and its
     * <code>run(String[] args)</code> method called passing an array containing
     * only the subtask arguments. The object returned by the <code>run</code>
     * method is written to <code>System.out</code>. If the <code>run</code>
     * method throws an exception, then that exception is written to
     * <code>System.out</code> instead.
     *
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        origOut = System.out;
        System.setOut(System.err);
	try {
	    if (args.length == 0) {
		String msg = "Missing SubVMTask to instantiate in arg 0";
		writeResponse(new IllegalStateException(msg));
		System.exit(1);
	    }
	    Class subVMTaskClass = Class.forName(args[0]);
	    SubVMTask task = (SubVMTask) subVMTaskClass.newInstance();
	    String[] newArgs = new String[args.length - 1];
	    System.arraycopy(args, 1, newArgs, 0, newArgs.length);
	    writeResponse(task.run(newArgs));
	    System.exit(0);
	} catch (Throwable t) {
	    try {
		writeResponse(t);
		
	    } catch (Throwable fatal) {
		fatal.printStackTrace();
	    }
	    System.exit(1);
	}
    }

    /**
     * Write the serialized form of the given object to <code>System.out</code>.
     *
     * @param obj the object to write
     */
    private static void writeResponse(Object obj) throws Throwable {
	ObjectOutputStream os = new ObjectOutputStream(origOut);
	os.writeObject(obj);
	os.close();
    }
}
