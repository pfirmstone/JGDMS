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


package org.apache.river.test.spec.renewalservice;

import java.util.logging.Level;

// java.rmi
import java.rmi.ConnectIOException;
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.UnmarshalException;

/**
 * This class performs the same assertions as RenewalFailureEventTest.
 * The difference is that this class uses only indefinite exceptions.
 *
 */
public class RenewalFailureEventIndefiniteTest 
        extends RenewalFailureEventTest {
    
    // purposefully inherit javadoc from parent class
    protected Throwable[] createExceptionArray() {

       Throwable[] throwArray = new Throwable[4];
       throwArray[3] = 
	   new UnmarshalException("Second UnmarshalException");
       throwArray[2] = 
	   new ConnectIOException("ConnectIOException");
       throwArray[1] = 
	   new RemoteException("RemoteException");
       throwArray[0] = 
	   new UnmarshalException("First UnmarshalException");

       return throwArray;
    }

    // purposefully inherit javadoc from parent class
    protected Throwable getExpectedException() {
       return failingOwner.getLastThrowable(); // last one thrown
    }
    
} // RenewalFailureEventIndefiniteTest




