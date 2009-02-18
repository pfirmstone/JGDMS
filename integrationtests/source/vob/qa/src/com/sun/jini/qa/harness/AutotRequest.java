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

import java.io.EOFException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * The messages which can be sent to a <code>SlaveTest.</code>
 */
public class AutotRequest {

    private static Logger logger = Logger.getLogger("com.sun.jini.qa.harness");

    public static Object callTestHost(OutboundAutotRequest request) {
	Socket s = null;
	logger.log(Level.FINE, 
		   "Sending request to autot test host: " + request);
	try {
	    s = new Socket("localhost", OutboundAutotRequest.PORT);
	    ObjectOutputStream oos =
		new ObjectOutputStream(s.getOutputStream());
	    oos.writeObject(request);
	    oos.flush();
	    //	    oos.close();
	} catch (Exception e) {
	    // fatal, so don't worry about closing sockets/streams
	    logger.log(Level.SEVERE, "Unexpected exception sending " 
				    + "request to test host ",
				     e);
	    return null;
	}
	ObjectInputStream ois = null;
	try {
	    ois = new ObjectInputStream(s.getInputStream());
	    Object o = ois.readObject();
	    return o;
	} catch (EOFException e) {
	} catch (Exception e) {
	    logger.log(Level.SEVERE, 
		       "exception receiving response from test host",e);
	} finally {
	    try {
		ois.close();
		s.close(); // redundant, I think
	    } catch (Exception ignore) {
	    }
	}
	return null;
    }

    public static Object callTest(InboundAutotRequest request) {
	Socket s = null;
	logger.log(Level.FINE, 
		   "Sending request to test: " + request);
	try {
	    s = new Socket("localhost", InboundAutotRequest.PORT);
	    ObjectOutputStream oos =
		new ObjectOutputStream(s.getOutputStream());
	    oos.writeObject(request);
	    oos.flush();
	    //	    oos.close();
	} catch (Exception e) {
	    // fatal, so don't worry about closing sockets/streams
	    logger.log(Level.SEVERE, 
		       "Unexpected exception sending request to test",e);
	    return null;
	}
	ObjectInputStream ois = null;
	try {
	    ois = new ObjectInputStream(s.getInputStream());
	    Object o = ois.readObject();
	    return o;
	} catch (EOFException e) {
	} catch (Exception e) {
	    logger.log(Level.SEVERE,
		       "exception receiving response from test ",e);
	} finally {
	    try {
		ois.close();
		s.close(); // redundant, I think
	    } catch (Exception ignore) {
	    }
	}
	return null;
    }
}
