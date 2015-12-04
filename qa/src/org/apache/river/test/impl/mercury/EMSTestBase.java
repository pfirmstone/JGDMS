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

import java.util.ArrayList;
import java.util.Collection;
import java.util.logging.Level;

import java.rmi.RemoteException;

import org.apache.river.constants.TimeConstants;

import net.jini.core.event.RemoteEvent;
import net.jini.event.EventMailbox;
import net.jini.event.MailboxRegistration;
import net.jini.event.MailboxPullRegistration;
import net.jini.core.lease.Lease;
import net.jini.core.event.RemoteEventListener;
import net.jini.space.JavaSpace;

import org.apache.river.qa.harness.TestException;


public abstract class EMSTestBase extends MailboxTestBase {

    public void assertCount(TestListener tl, long desired) throws TestException {
	long evCnt = 0;
	try {
	    evCnt = tl.getEventCount();
	} catch (Exception e) {
	    throw new TestException ("Unexpected exception while "
				   + "getting event count", e);
	}
	if (evCnt != desired) {
	    throw new TestException ("Unexpected event count of " + evCnt +
		 " when expecting count of " + desired);
	}
    }
    
    public void assertCount(TestPullListener tpl, long desired) 
        throws TestException 
    {
	long evCnt = 0;
	try {
	    evCnt = tpl.getCollectedRemoteEventsSize();
	} catch (Exception e) {
	    throw new TestException ("Unexpected exception while "
				   + "getting event count", e);
	}
	if (evCnt != desired) {
	    throw new TestException ("Unexpected event count of " + evCnt +
		 " when expecting count of " + desired);
	}
    }

    public void getCollectedRemoteEvents(
        TestPullListener tpl, MailboxPullRegistration mr, 
        int desired, long maxDelay) 
	throws Exception
    {
        long start = System.currentTimeMillis();
        boolean done = false;
	// Collect new events, if any
	Collection events = tpl.getRemoteEvents(mr);
	while (!done) {
            if (tpl.getCollectedRemoteEventsSize() >= desired) {
                done = true;
	    } else if (getTime() - start >= maxDelay) {
	        done = true;
	    } else {
	        try {
		    // Sleep one second between checks
		    Thread.sleep(1000);
// 	            Thread.yield(); //Give someone else a chance
	        } catch (InterruptedException ie) { /* ignore */ 
                    Thread.currentThread().interrupt();
	    }
	    }
	    events = tpl.getRemoteEvents(mr);
        }
	/**
	 * Would like to check event source for the proper "type". 
	 * Our test generator can be tested for "instanceof TestGenerator", but
	 * the JavaSpace source is of type "OutriggerServer" and "JavaSpace".
	 * So, rather than introduce "private" classes, we'll defer the 
	 * source checking to verifyEvents.
	 **/
    }


    public void waitForEvents(TestListener tl, int desired, long maxDelay) 
	throws RemoteException
    {
        long start = System.currentTimeMillis();
        boolean done = false;
        while (!done) {
	    try {
		// Sleep one second between checks
		Thread.sleep(1000);
	    } catch (InterruptedException ie) { /* ignore */
                Thread.currentThread().interrupt();
            }

            long received = tl.getEventCount();
            if (received >= desired)
                done = true;
	    else if (getTime() - start >= maxDelay)
	        done = true;
	    else if ((received % ((desired / 10) + 1)) == 0) {
                // output log msg for every 1/10th of desired events
                logger.log(Level.FINE, Thread.currentThread().getName() 
                    + " has received " + received + " events.");
            }

//	    Thread.yield(); //Give someone else a chance
        }
    }

    protected void assertEvents(TestListener tl, RemoteEvent[] events) 
	throws RemoteException, TestException
    {
	if (tl.verifyEvents(events) == false) {
	    throw new TestException ("Failed to verify event set");
	}
    }
    protected void assertEvent(TestListener tl, RemoteEvent event) 
	throws RemoteException, TestException
    {
	if (tl.verifyEvent(event) == false) {
	    throw new TestException ("Failed to verify event");
	}
    }
    protected void assertEvents(TestPullListener tpl, RemoteEvent[] events) 
	throws RemoteException, TestException
    {
	if (tpl.verifyEvents(events) == false) {
	    throw new TestException ("Failed to verify event set");
	}
    }
    protected void assertEvent(TestPullListener tpl, RemoteEvent event) 
	throws RemoteException, TestException
    {
	if (tpl.verifyEvent(event) == false) {
	    throw new TestException ("Failed to verify event");
	}
    }
    
    protected void assertEvents(Collection src, Collection tgt) 
	throws RemoteException, TestException
    {
        int i = 0;
        RemoteEvent[] sent = (RemoteEvent[]) src.toArray(new RemoteEvent[0]);
        ArrayList srcList = new ArrayList(sent.length);
        for (i=0; i < sent.length; i++) {
            srcList.add(new RemoteEventHandle(sent[i]));
        }
        
        RemoteEvent[] rcvd = (RemoteEvent[]) tgt.toArray(new RemoteEvent[0]);
        ArrayList tgtList = new ArrayList(rcvd.length);
        for (i=0; i < rcvd.length; i++) {
            tgtList.add(new RemoteEventHandle(rcvd[i]));
        }
        if (!srcList.containsAll(tgtList)) {
            throw new TestException("Expected events not received.");
        }
    }
    
    protected void assertEvent(RemoteEvent src, RemoteEvent tgt) 
	throws RemoteException, TestException
    {
        if (!new RemoteEventHandle(src).equals(new RemoteEventHandle(tgt))) {
            throw new TestException("Expected event doesn't match");
        }
    }

    protected long getTime() {
        return System.currentTimeMillis();
    }
    
    protected static java.util.ArrayList getClassLoaderTree(ClassLoader classloader) {
	java.util.ArrayList loaderList = new java.util.ArrayList();
	while(classloader != null) {
            loaderList.add(classloader);
	    final ClassLoader myClassLoader = classloader;
            classloader = (ClassLoader)
	      java.security.AccessController.doPrivileged(new java.security.PrivilegedAction() {
               public Object run() {
                  return myClassLoader.getParent();
               }
            });
	}//end loop
	loaderList.add(null); //Append boot classloader
	java.util.Collections.reverse(loaderList);
	return loaderList;
    }//end getClassLoaderTree

}
