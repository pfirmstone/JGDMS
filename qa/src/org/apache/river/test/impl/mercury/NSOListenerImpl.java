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
package org.apache.river.test.impl.mercury;

import org.apache.river.start.lifecycle.LifeCycle;

import net.jini.core.event.RemoteEvent;
import net.jini.core.event.UnknownEventException;

import java.rmi.RemoteException;
import java.rmi.NoSuchObjectException;

public class NSOListenerImpl extends TestListenerImpl {

    private static final boolean DEBUG = true;
    
    private NSOListenerImpl(String[] configArgs, LifeCycle lc) throws Exception {
        super(configArgs, lc);
    }

    //
    // Override base class implementation to throw
    // NoSuchObjectException after logging the event.
    //
    public void notify(RemoteEvent theEvent)
	throws UnknownEventException, RemoteException
    {
        super.notify(theEvent);

        throw new MyUnknownEventException("Bad test listener exception");
    }
    
}


