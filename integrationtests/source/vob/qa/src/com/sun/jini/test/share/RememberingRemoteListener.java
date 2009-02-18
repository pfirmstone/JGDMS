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


package com.sun.jini.test.share;

// java.util
import java.util.ArrayList;
import java.util.logging.Level;

// java.io
import java.io.PrintWriter;
import java.io.ObjectStreamException;

// java.rmi
import java.rmi.RemoteException;

// net.jini
import net.jini.core.event.RemoteEvent;
import net.jini.core.event.UnknownEventException;

import net.jini.export.Exporter;

/**
 * RememberingRemoteListener keeps track of each RemoteEvent it
 * receives and the time each event was received. The time is stored
 * in milliseconds from Jan. 1, 1970.
 *
 * @author Steven Harris - SMI Software Development 
 */
public class RememberingRemoteListener extends RemoteListener {
    
    /**
     * holds the events and their arrival times
     */
    private ArrayList events = new ArrayList();
    private ArrayList arrivalTimes = new ArrayList();

    /**
     * Constructor requiring an exporter to export the listener.
     * 
     * @param exporter the exporter to use to export this listener
     * 
     * @exception RemoteException
     *          Remote initialization problem. 
     */ 
    public RememberingRemoteListener(Exporter exporter) throws RemoteException {
	super(exporter);
    }
    
    // inherit javadoc from parent class
    public synchronized void notify(RemoteEvent theEvent) 
            throws UnknownEventException, RemoteException {
	logger.log(Level.FINE, "In notify() method.");
	arrivalTimes.add(new Long(System.currentTimeMillis()));
	events.add(theEvent);
    }

    /**
     * Clear all remembered events.
     */
    public synchronized void clear() {
	arrivalTimes.clear();
	events.clear();
    }

    /**
     * Return an array of all events received as of the time of method call.
     * 
     * @return an array of RemoteEvents received thus far.
     * 
     */
    public synchronized RemoteEvent[] getEvents() {
	Object[] objArr = events.toArray();
	RemoteEvent[] RemoteEventArr = new RemoteEvent[objArr.length]; 
	System.arraycopy(objArr, 0, RemoteEventArr, 0, objArr.length);
	return RemoteEventArr;
    }

    /**
     * Return an array of all arrival times received as of the time of
     * method call.  Note: arrival time an index 0 corresponds with
     * the event at index 0 in the array returned by the
     * <CODE>getEvents</CODE> method.
     * 
     * @return an array of RemoteEvents received thus far.
     */
    public synchronized Long[] getArrivalTimes() {
	Object[] objArr = arrivalTimes.toArray();
	Long[] longArr = new Long[objArr.length]; 
	System.arraycopy(objArr, 0, longArr, 0, objArr.length);
	return longArr;
    }
} // RememberingRemoteListener
