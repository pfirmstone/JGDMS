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

package com.sun.jini.jeri.internal.runtime;

import com.sun.jini.jeri.internal.runtime.ImplRefManager.ImplRef;
import com.sun.jini.thread.NewThreadAction;
import java.rmi.Remote;
import java.rmi.server.ExportException;
import java.rmi.server.Unreferenced;
import java.security.AccessController;
import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.id.Uuid;
import net.jini.jeri.RequestDispatcher;
import net.jini.security.Security;
import net.jini.security.SecurityContext;

/**
 * A table of exported remote objects.
 *
 * @author Sun Microsystems, Inc.
 **/
final class ObjectTable {

    private static final Logger logger =
	Logger.getLogger("net.jini.jeri.BasicJeriExporter");

    /**
     * lock to serialize request dispatcher reservation per export, so
     * that a partial export will not cause another export to fail
     * unnecessarily
     **/
    private final Object requestDispatchersLock;

    /** table of references to impls exported with DGC */
    private final ImplRefManager implRefManager;

    /** number of objects exported with keepAlive == true */
    private final JvmLifeSupport keepAliveCount;

    /** maps client ID to Lease (lock guards leaseChecker too) */
    private final ConcurrentMap<Uuid,Lease> leaseTable;

    /** thread to check for expired leases */
    private Thread leaseChecker;
    
    /** thread guard */
    private Boolean running;

    ObjectTable() { 
        requestDispatchersLock = new Object();
        implRefManager = new ImplRefManager();
        keepAliveCount = new JvmLifeSupport();
        leaseTable = new ConcurrentHashMap<Uuid,Lease>(256);//Plenty of capacity to reduce resizing.
        leaseChecker = null;
        running = Boolean.FALSE;
    }

    RequestDispatcher createRequestDispatcher(Unreferenced unrefCallback) {
	return new DgcRequestDispatcher(unrefCallback, this);
    }

    boolean isReferenced(RequestDispatcher requestDispatcher) {
	return getRD(requestDispatcher).isReferenced();
    }
    
    DgcServer getDgcServer(DgcRequestDispatcher dgdRD){
        return new DgcServerImpl(dgdRD);
    }

    Target export(Remote impl,
		  RequestDispatcher[] requestDispatchers,
		  boolean allowDGC,
		  boolean keepAlive,
		  Uuid id)
        throws ExportException
    {
	DgcRequestDispatcher[] rds = new DgcRequestDispatcher[requestDispatchers.length];
	for (int i = 0; i < requestDispatchers.length; i++) {
	    rds[i] = getRD(requestDispatchers[i]);
	}
        SecurityContext securityContext = Security.getContext();
        ClassLoader ccl = Thread.currentThread().getContextClassLoader();
        Target t = null;
        t = new Target(id, rds, allowDGC, keepAlive,this,
                securityContext, ccl, keepAliveCount);
        synchronized (requestDispatchersLock){
            t.procRequestDispatchers();
        }
        ImplRef implRef = implRefManager.getImplRef(impl, t);
        t.setImplRef(implRef);
        t.setExported();
	return t;
    }

    private DgcRequestDispatcher getRD(RequestDispatcher requestDispatcher) {
	/*
	 * The following cast will throw a ClassCastException if we were
	 * passed a RequestDispatcher that was not returned by this class's
	 * createRequestDispatcher method:
	 */
	DgcRequestDispatcher rd = (DgcRequestDispatcher) requestDispatcher;
	if (!rd.forTable(this)) {
	    throw new IllegalArgumentException(
		"request dispatcher for different object table");
	}
	return rd;
    }

    void registerTarget(Target target, Uuid clientID) {
        Lease lease = leaseTable.get(clientID);
        if (lease == null) {
            target.leaseExpired(clientID);
        } else {
            boolean added = lease.add(target);
            if ( added == false){
                // lease has been locked because it has expired
                // prior to removal
                target.leaseExpired(clientID);
            }
        }
    }

    void unregisterTarget(Target target, Uuid clientID) {
        Lease lease = leaseTable.get(clientID);
        if (lease != null) {
            lease.remove(target);
        }
    }

    

    private class DgcServerImpl implements DgcServer {
        private final DgcRequestDispatcher dgcRequestDispatcher;
        
        DgcServerImpl(DgcRequestDispatcher dgcRequestDispatcher){
            this.dgcRequestDispatcher = dgcRequestDispatcher;
        }

        public long dirty(Uuid clientID,
                          long sequenceNum,
                          Uuid[] ids)
        {
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST,
                    "clientID={0}, sequenceNum={1}, ids={2}",
                    new Object[] {
                        clientID, new Long(sequenceNum), Arrays.asList(ids)
                    });
            }

            long duration = Jeri.leaseValue;

            Lease lease = leaseTable.get(clientID);
            if (lease == null) {
                lease = new Lease(clientID, duration);
                Lease existed = leaseTable.putIfAbsent(clientID,lease);
                if (existed != null){
                    assert clientID.equals(existed.getClientID());
                    boolean renewed = existed.renew(duration);
                    if (!renewed){
                        /* The probability of getting here is low,
                         * it indicates a lease with an extremely short 
                         * expiry and a very small lease table.
                         */               
                        if (logger.isLoggable(Level.WARNING)) {
                            logger.log(Level.WARNING,
                                "Problem with lease table, try a longer " +
                                "lease duration clientID={0}, " +
                                "sequenceNum={1}, ids={2}",
                                new Object[] {
                                    clientID, new Long(sequenceNum), Arrays.asList(ids)
                                });
                        }                          
                    }
                }
            } else {
                assert clientID.equals(lease.getClientID());
                boolean renewed = lease.renew(duration);
                if (!renewed){
                    // Indicates an expired lease in the table.  A lease
                    // always becomes expired prior to removal, it is 
                    // never removed prior to expiry, in case it is
                    // renewed by another thread, which would risk a renewed
                    // lease being removed from the table.  
                    // An expired lease must be replaced.
                    leaseTable.remove(clientID, lease); // Another thread could remove it first.
                    lease = new Lease(clientID, duration);
                    Lease existed = leaseTable.putIfAbsent(clientID, lease);
                    if (existed != null){
                        lease = existed;
                        assert clientID.equals(lease.getClientID());
                        renewed = lease.renew(duration);
                        if (!renewed){
                            /* The probability of getting here is low,
                             * it indicates a lease of extremely short 
                             * duration and a very small lease table.
                             */               
                            if (logger.isLoggable(Level.WARNING)) {
                                logger.log(Level.WARNING,
                                    "Problem with lease table, try a longer " +
                                    "lease duration clientID={0}, " +
                                    "sequenceNum={1}, ids={2}",
                                    new Object[] {
                                        clientID, new Long(sequenceNum), Arrays.asList(ids)
                                    });
                            }                          
                        }  
                    }
                }
            }
            /* FIXED: River-142: 
             * In the server-side DGC implementation's thread that check's for
             * lease expirations 
             * (com.sun.jini.jeri.internal.runtime.ObjectTable.LeaseChecker.run),
             * it checks for them while synchronized on the overall lease table,
             * but it delays notifying the expired leases' individual registered
             * Targets about the expirations until after it has released the
             * lease table lock. This approach was taken from the 
             * JRMP implementation, which is that way because of the fix 
             * for 4118056 (a previous deadlock bug-- but now, I'm thinking 
             * that the JRMP implementation has this bug too).
             *
             * The problem seems to be that after releasing the lease table 
             * lock, it is possible for another lease renewal/request to 
             * come in (from the same DGC client and for the same remote object)
             * that would then be invalidated by the subsequent Target 
             * notification made by the lease expiration check thread-- and 
             * thus the client's lease renewal (for that remote object) will 
             * be forgotten. It would appear that the synchronization approach 
             * here needs to be reconsidered.
             * 
             * ( Comments note: )
             * In addition to the basic problem of the expired-then-renewed 
             * client being removed from the referenced set, there is also 
             * the problem of the sequence table entry being forgotten-- which 
             * prevents detection of a "late clean call".
             * Normally, late clean calls are not a problem because sequence 
             * numbers are retained while the client is in the referenced set 
             * (and there is no such thing as a "strong dirty"). 
             * But in this case, with the following order of events on 
             * the server side:
             *
             *   1. dirty, seqNo=2
             *   2. (lease expiration)
             *   3. clean, seqNo=1
             *
             * The primary bug here is that the first two events will leave 
             * the client missing from the referenced set. But the secondary 
             * bug is that even if that's fixed, with the sequence number 
             * forgotten, the third event (the "late clean call") will still 
             * cause the client to be removed from the referenced set.
             * 
             * FIX:
             * This issue has been fixed by making the Lease responsible for
             * it's own state, which is protected internally by synchronization.
             * The lease checker passes the time to the Lease, which checks 
             * itself and notifies the Targets in the event of expiry.
             * 
             * Because the notification is not delayed, the client id and
             * sequence number will not be not be removed by the LeaseChecker
             * thread after being updated by the second dirty call (when
             * the lock was cleared as described).
             * 
             * The client id and sequence number are added to the Target sequence
             * table by the second dirty call, after the Lease removes
             * them immediately upon expiry being invoked by the LeaseChecker.
             * 
             * Then because the late clean call sequence number is less than the 
             * second dirty call and exists, it is correctly recognised.
             */
            synchronized (running){
                if (!running) {
                    leaseChecker =
                        (Thread) AccessController.doPrivileged(
                            new NewThreadAction(new LeaseChecker(),
                                "DGC Lease Checker", true));
                    leaseChecker.start();
                }
            }
            for (int i = 0; i < ids.length; i++) {
                Target target = dgcRequestDispatcher.get(ids[i]);
                if (target != null) {
                    target.referenced(clientID, sequenceNum);
                }
            }
            return duration;
        }

        public void clean(Uuid clientID,
                          long sequenceNum,
                          Uuid[] ids,
                          boolean strong)
        {
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST,
                    "clientID={0}, sequenceNum={1}, ids={2}, strong={3}",
                    new Object[] {
                        clientID, new Long(sequenceNum),
                        Arrays.asList(ids), Boolean.valueOf(strong)
                    });
            }

            for (int i = 0; i < ids.length; i++) {
                Target target = dgcRequestDispatcher.get(ids[i]);
                if (target != null) {
                    target.unreferenced(clientID, sequenceNum, strong);
                }
            }
        }
    }

    private class LeaseChecker implements Runnable {

	public void run() {
	    boolean done = false;
            try {
                do {
                    Thread.sleep(Jeri.leaseCheckInterval);
                    long now = System.currentTimeMillis();		
                    for (Iterator i = leaseTable.values().iterator();
                         i.hasNext();)
                    {
                        Lease lease = (Lease) i.next();
                        boolean expired = lease.notifyIfExpired(now);
                        if (expired) {			    
                            i.remove();
                        }
                    }
                    if (leaseTable.isEmpty()) {
                        done = true;
                    }		
                } while (!done);
            } catch (InterruptedException e) {
                // REMIND: shouldn't happen, OK to ignore?
                // No, restore the interrupted status
                 Thread.currentThread().interrupt();
            } finally {
                // This is always executed and returns the lease checker
                // to the non running state, such that if the application
                // has not exited, another thread will be started eventually.
                synchronized (running){
                    leaseChecker = null;
                    running = Boolean.FALSE;               
                }
            }
	}
    }

    static class NoSuchObject extends Exception { }
}
