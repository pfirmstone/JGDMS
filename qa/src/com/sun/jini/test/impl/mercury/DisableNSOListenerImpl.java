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
package com.sun.jini.test.impl.mercury;

import com.sun.jini.start.LifeCycle;

import net.jini.core.event.RemoteEvent;
import net.jini.core.event.UnknownEventException;
import net.jini.event.MailboxRegistration;

import java.rmi.RemoteException;
import java.rmi.NoSuchObjectException;

public class DisableNSOListenerImpl extends TestListenerImpl 
    implements DisableListener
{

    private static final boolean DEBUG = true;

    private MailboxRegistration mbr = null;
    
    private DisableNSOListenerImpl(String[] configArgs, LifeCycle lc) 
    throws Exception {
        super(configArgs, lc);
    }

    public void setMailboxRegistration (MailboxRegistration mbr) {
        if (mbr == null) {
	    throw new NullPointerException(
	        "MailboxRegistration argument cannot be null");
	}
	this.mbr = mbr;
    }

    //
    // Override base class implementation to throw
    // NoSuchObjectException after logging the event.
    //
    public void notify(RemoteEvent theEvent)
	throws UnknownEventException, RemoteException
    {
	// log the event
        super.notify(theEvent);

        // callback to disable registration
	mbr.disableDelivery();

	throw new MyUnknownEventException("Bad test listener exception");
    }
    
}


