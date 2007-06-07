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
package com.sun.jini.mercury;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.rmi.RemoteException;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;

import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.event.RemoteEvent;
import net.jini.event.InvalidIteratorException;
import net.jini.event.RemoteEventIterator;
import net.jini.id.ReferentUuid;
import net.jini.id.ReferentUuids;
import net.jini.id.Uuid;
import net.jini.security.proxytrust.ProxyTrustIterator;
import net.jini.security.proxytrust.SingletonProxyTrustIterator;

import com.sun.jini.proxy.ThrowThis;

/**
 * 
 *
 * @author Sun Microsystems, Inc.
 *
 * @since 1.1
 */ 
class RemoteEventIteratorImpl implements RemoteEventIterator
{
    /** Unique identifier for this registration */
    final Uuid registrationID;

    /** Unique identifier for this registration */
    final Uuid iteratorID;

    /** Reference to service implementation */
    final MailboxBackEnd mailbox;

    /** Event iterator **/
    private Iterator iter = null;
    
    /** Last remote event cookie */
    private Object lastEventCookie = null;

    /** lock object protecting <code>invalidated</code> flag. */
    private Object invalidatedLock = new Object();
    
    /** 
     * Boolean flag indicating the (in)validity of this object. 
     * If true, the object is invalid and all public method invocations
     * should throw InvalidIteratorException. 
     */
    private boolean invalidated = false;

    /** Convenience constructor */
    RemoteEventIteratorImpl(Uuid id, Uuid regId, MailboxBackEnd srv, 
	Collection evts) 
    {
        if (id == null || regId == null || srv == null || evts == null)
            throw new IllegalArgumentException("Cannot accept null arguments");
        registrationID = regId;
        iteratorID = id;
        mailbox = srv;
	iter = evts.iterator();
    }

    // inherit javadoc from supertype
    public RemoteEvent next(long timeout) 
        throws RemoteException, InvalidIteratorException 
    {
        //TODO - implement timeout
        //TODO - handle ClassNotFoundException for getRemoteEvent() call
        checkState();
        
        if (timeout < 0) {
            throw new 
                IllegalArgumentException("Timeout value must non-negative");
        }
        
	RemoteEvent re = null;	
        LocalRemoteEventData lred = getNextValidLocalRemoteEventData(iter);
        if (lred != null) {
            re = lred.re;
            lastEventCookie = lred.cookie;
        } else { // get next batch of events, if any
            try {
                Collection events = 
                    mailbox.getNextBatch(
                        registrationID, iteratorID, timeout,
                        lastEventCookie);
                iter = events.iterator();
                lred = getNextValidLocalRemoteEventData(iter);
                if (lred != null) {
                    re = lred.re;
                    lastEventCookie = lred.cookie;
                }   
            } catch (InvalidIteratorException iie) {
                invalidate();
                throw iie;
            } catch (ThrowThis tt) { 
                tt.throwRemoteException();
            }                           
        }
	return re;
    }
    
    private static class LocalRemoteEventData {
        RemoteEvent re = null;
        Object cookie = null;
        LocalRemoteEventData(RemoteEvent re, Object cookie) {
            this.re = re;
            this.cookie = cookie;
        }
    }
    
    private LocalRemoteEventData getNextValidLocalRemoteEventData(Iterator i) {
      RemoteEventData rd = null;
      LocalRemoteEventData lrd = null;
      try {
	  if (i!= null && i.hasNext()) {
	      rd = (RemoteEventData)i.next();
	      lrd = new LocalRemoteEventData(
                 rd.getRemoteEvent(), rd.getCookie());
	  }
      } catch (ClassNotFoundException cnfe) {
	  lrd = getNextValidLocalRemoteEventData(i);
      }

      return lrd;
    }
    
    // inherit javadoc from supertype
    public void close() throws InvalidIteratorException {
        checkState();
        invalidate();
    }

    /**
     * Utility method that checks the validity of this object
     * and throws an exception if it's invalid.
     */
    private void checkState() throws InvalidIteratorException {
        synchronized (invalidatedLock) {
            if (invalidated) {
                throw new InvalidIteratorException();
            }
        }
    }
    
    /**
     * Utility method that marks this object as invalid.
     */
    private void invalidate() {
        synchronized (invalidatedLock) {
            invalidated = true;
        }
    }
}
