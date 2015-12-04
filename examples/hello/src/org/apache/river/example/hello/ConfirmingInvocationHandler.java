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

package org.apache.river.example.hello;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;
import java.util.Collection;
import javax.swing.JOptionPane;
import net.jini.core.constraint.MethodConstraints;
import net.jini.jeri.BasicInvocationHandler;
import net.jini.jeri.ObjectEndpoint;

/** Defines an invocation handler that confirms calls. */
public class ConfirmingInvocationHandler extends BasicInvocationHandler {

    private static final long serialVersionUID = 2L;

    /**
     * Create a confirming invocation handler from a basic handler and
     * constraints.
     */
    public ConfirmingInvocationHandler(ConfirmingInvocationHandler other,
				MethodConstraints clientConstraints) {
	super(other, clientConstraints);
    }

    /**
     * Create a confirming invocation handler for the object endpoint and
     * server constraints.
     */
    public ConfirmingInvocationHandler(ObjectEndpoint oe,
                                MethodConstraints serverConstraints) {
	super(oe, serverConstraints);
    }

    /**
     * Asks whether the call should be made, then writes a call identifier
     * before the arguments.
     */
    protected void marshalArguments(Object proxy,
				    Method method,
				    Object[] args,
				    ObjectOutputStream out,
				    Collection context)
	throws IOException
    {
	long callId = System.currentTimeMillis();
	int result = JOptionPane.showConfirmDialog(
	    null,
	    "Make remote call?" +
	    "\n  Object: " + proxy +
	    "\n  Method: " + method.getName() +
	    "\n  Call id: " + callId,
	    "Make remote call?",
	    JOptionPane.OK_CANCEL_OPTION);
	if (result != JOptionPane.OK_OPTION) {
	    throw new RuntimeException("Client cancelled call");
	}
	out.writeLong(callId);
	super.marshalArguments(proxy, method, args, out, context);
    }
}
