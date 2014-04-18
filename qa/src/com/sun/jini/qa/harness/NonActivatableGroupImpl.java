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
package com.sun.jini.qa.harness;


import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.rmi.MarshalledObject;
import java.util.logging.Logger;

/**
 * A container for nonactivatable services. This class is the
 * main class exec'd in a separate VM. The main method creates
 * an instance of a <code>NonActivatableGroup</code> and returns
 * it's proxy to the parent process by writing the object
 * to <code>System.err</code>. There is no <code>Configuration</code>
 * associated with this service.
 */
class NonActivatableGroupImpl {

    /** the logger */
    private static Logger logger = 
	Logger.getLogger("com.sun.jini.qa.harness");

    /** the <code>System.err</code> stream created for the VM */
    private static PrintStream origErr;

    /** the reference to the group, save to ensure it won't be GC'd */
    private static NonActivatableGroup nonActGroup;

    /**
     * Set up the VM to act as a NonActivatableGroup and
     * return the group proxy to the parent process. The
     * initial System.err is saved, and then System.err is
     * set System.out. This prevents any error or logging
     * performed from being written to the original System.err.
     * An instance of GroupImpl is created and a reference 
     * saved to ensure it is not GC'd. The proxy is written to
     * the original System.err. 
     *
     * @param args the command line arguments, which are unused
     */
    public static void main(String[] args) {
	origErr = System.err;
	System.setErr(System.out);
	GroupImpl group = new GroupImpl();
	group.export();
        nonActGroup = group;
	try {
	    ObjectOutputStream os = new ObjectOutputStream(origErr);
	    os.writeObject(new MarshalledObject(group.getProxy()));
	    os.flush();
	} catch (IOException e) {
	    throw new RuntimeException("WriteObject failed", e);
	} catch (Throwable e){
            e.printStackTrace();
            System.exit(1);
        }
    }
}
