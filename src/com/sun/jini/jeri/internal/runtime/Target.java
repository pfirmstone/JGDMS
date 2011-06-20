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
import com.sun.jini.jeri.internal.runtime.ObjectTable.NoSuchObject;
import java.io.IOException;
import java.io.OutputStream;
import java.rmi.Remote;
import java.rmi.server.ExportException;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.export.ServerContext;
import net.jini.id.Uuid;
import net.jini.jeri.InboundRequest;
import net.jini.jeri.InvocationDispatcher;
import net.jini.security.SecurityContext;

/**
 * A Target represents a remote object, exported by BasicJeriExporter.
 * 
 * Based on original ObjectTable.Target, modified to support forced interrupted
 * unexport.
 *
 * @since 2.2.0
 * @author Peter Firmstone
 */
final class Target {
    private static final Logger logger =
	Logger.getLogger("net.jini.jeri.BasicJeriExporter");

    private volatile ImplRef implRef;
    private final Uuid id;
    private final DgcRequestDispatcher[] requestDispatchers;
    private final boolean allowDGC;
    private final boolean keepAlive;
    private final SecurityContext securityContext;
    private final ClassLoader ccl;
    private final Lock lock = new ReentrantLock();
    private volatile InvocationDispatcher invocationDispatcher;
    private volatile boolean exported = false;
    private volatile boolean unexported = false;
    private volatile boolean success = false;
    private volatile boolean interrupted = false;
    private final Set<Uuid> referencedSet;
    private final Map<Uuid, SequenceEntry> sequenceTable;
    private final JvmLifeSupport keepAliveCount;
    private volatile boolean decrementedKeepAlive = false;
    private final ObjectTable objTable;
    private final Collection<Thread> calls = new ArrayList<Thread>();

    /**
     * Construction must be directly followed by three calls.
     * 
     * procRequestDispatchers()
     * setImplRef(ImplRef implRef)
     * export()
     * 
     * @param id
     * @param requestDispatchers
     * @param allowDGC
     * @param keepAlive
     * @param table
     * @param sc
     * @param contextCl
     * @param counter
     * @throws java.rmi.server.ExportException
     */           
    Target(Uuid id, DgcRequestDispatcher[] requestDispatchers, boolean allowDGC, boolean keepAlive,
            ObjectTable table, SecurityContext sc, ClassLoader contextCl,
            JvmLifeSupport counter) throws ExportException {
        super();
        this.objTable = table;
        this.id = id;
        this.requestDispatchers = requestDispatchers;
        this.allowDGC = allowDGC;
        this.keepAlive = keepAlive;
        this.keepAliveCount = counter;

        securityContext = sc;
        ccl = contextCl;
        if (allowDGC) {
            referencedSet = new HashSet<Uuid>(3);
            sequenceTable = new HashMap<Uuid, SequenceEntry>(3);
        } else {
            referencedSet = null;
            sequenceTable = null;
        }
    }
    
    /**
     * Must be synchronized externally by the object table.
     * Synchronization cannot be performed by a class lock, there may
     * be more than one object table.
     * 
     * Unsynchronized method.
     */
    void procRequestDispatchers() throws ExportException{
        if (exported) throw new ExportException("Target already exported");
        int i = 0;
        try {
            for (i = 0; i < requestDispatchers.length; i++) {
                requestDispatchers[i].put(this);
            }
            success = true;
        } finally {
            if (!success) {
                for (int j = 0; j < i; j++) {
                    requestDispatchers[i].remove(this, false);
                }
            }
        }
    }
    
    /**
     * Set the ImplRef.
     * 
     * Unsynchronized method, with volatile internal visibility, set
     * after construction, prior to setExport.
     * 
     * @param implRef
     */
    void setImplRef(ImplRef implRef) throws ExportException{
        if (exported) throw new ExportException("Target already exported");
        this.implRef = implRef;
    }
    
    void setInvocationDispatcher(InvocationDispatcher id) {
        assert id != null;
        lock.lock();
        try {
            assert invocationDispatcher == null;
            invocationDispatcher = id;
        } finally {
            lock.unlock();
        }
    }

    
    /**
     * This method is called after construction, processing RequestDispatchers,
     * creating and setting an ImplRef.
     * 
     * It should not be called if the object has been unexported.
     * 
     * Unsynchronized method.
     */
    void setExported() throws ExportException{
        if (exported) throw new ExportException("Target already exported");
        if (unexported == true) throw new ExportException("Target cannot be re-exported");
        if (implRef == null) throw new ExportException("ImplRef cannot be null");
        if (success == false) throw new ExportException("RequestDispatchers unsuccessful");
        exported = true;
        if (keepAlive){
            keepAliveCount.incrementKeepAliveCount();
        }
    }
    
    private void decrementKeepAliveCount(){
        if (keepAlive){
            if (decrementedKeepAlive) return; // Ensure only once per target.
            decrementedKeepAlive = true;
            keepAliveCount.decrementKeepAliveCount();
        }
    }

    /**
     * To quote the Exporter interface:
     * 
     * If the remote object is unexported as a result of this method, 
     * then the implementation may (and should, if possible) prevent 
     * remote calls in progress from being able to communicate their 
     * results successfully. 
     * 
     * To comply with the above, dispatch call interruption has been added.
     * 
     * @param force - if true forcibly unexported
     * @return true - if unexport successful
     */
    boolean unexport(boolean force) {
        if (!exported) return true;
        lock.lock();
        try {
            if (!force && !calls.isEmpty()) {
                return false;
            }
            unexported = true;
            exported = false;
            if ( force && !calls.isEmpty()){
                interrupted = true;
                Iterator<Thread> i = calls.iterator();
                while (i.hasNext()){
                    i.next().interrupt();
                    i.remove();
                }
            } 
            if (calls.isEmpty()) {
                decrementKeepAliveCount();
            }
            if (allowDGC) {
                if (!referencedSet.isEmpty()) {
                    for (Iterator i = referencedSet.iterator(); i.hasNext();) {
                        Uuid clientID = (Uuid) i.next();
                        objTable.unregisterTarget(this, clientID);
                    }
                    referencedSet.clear();
                }
                sequenceTable.clear();
            }
        } finally {
            lock.unlock();
        }
        implRef.release(this);
        for (int i = 0; i < requestDispatchers.length; i++) {
            requestDispatchers[i].remove(this, false);
        }
        return true;
    }

    void collect() {
        if (!exported) return;
        lock.lock();
        try {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "garbage collection of object with id {0}", id);
            }
            unexported = true;
            exported = false;

            if (calls.isEmpty()) {
                decrementKeepAliveCount();
            }

            if (allowDGC) {
                assert referencedSet.isEmpty();
                sequenceTable.clear();
            }
        } finally {
            lock.unlock();
        }

        for (int i = 0; i < requestDispatchers.length; i++) {
            requestDispatchers[i].remove(this, true);
        }
    }

    Uuid getObjectIdentifier() {
        return id;
    }

    // used by ImplRef for invoking Unreferenced.unreferenced
    boolean getEnableDGC() {
        return allowDGC;
    }

    SecurityContext getSecurityContext() {
        return securityContext;
    }

    ClassLoader getContextClassLoader() {
        return ccl;
    }

    void referenced(Uuid clientID, long sequenceNum) {
        if (!allowDGC) return;
        if (!exported) return;
        lock.lock();
        try {
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "this={0}, clientID={1}, sequenceNum={2}", new Object[]{this, clientID, new Long(sequenceNum)});
            }
            SequenceEntry entry = (SequenceEntry) sequenceTable.get(clientID);
            if (entry == null) {
                entry = new SequenceEntry(sequenceNum);
                sequenceTable.put(clientID, entry);
            } else if (!entry.update(sequenceNum, false)) {
                /* entry will be updated if sequenceNum is greater
                 * return if entry was not updated.
                 */
                return;
            }
            if (!referencedSet.contains(clientID)) {
                if (referencedSet.isEmpty()) {
                    Remote impl = implRef.getImpl();
                    if (impl == null) {
                        return;
                    }
                    implRef.pin(this);
                }
                referencedSet.add(clientID);

                objTable.registerTarget(this, clientID);
            }
        } finally {
            lock.unlock();
        }
    }

    void unreferenced(Uuid clientID, long sequenceNum, boolean strong) {
        if (!allowDGC) return;
        if (!exported) return;
        lock.lock();
        try {
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "this={0}, clientID={1}, sequenceNum={2}, strong={3}", new Object[]{this, clientID, new Long(sequenceNum), Boolean.valueOf(strong)});
            }
            SequenceEntry entry = sequenceTable.get(clientID);
            if (entry == null) {
                if (strong) {
                    entry = new SequenceEntry(sequenceNum, strong);
                    sequenceTable.put(clientID, entry);
                }
            } else if (!entry.update(sequenceNum, strong)) {
                return;
            } else if (!entry.keep()) {
                sequenceTable.remove(clientID);
            }

            objTable.unregisterTarget(this, clientID);

            if (referencedSet.remove(clientID) && referencedSet.isEmpty()) {
                implRef.unpin(this);
            }
        } finally {
            lock.unlock();
        }
    }

    void leaseExpired(Uuid clientID) {
        assert allowDGC;
        if (!exported) return;
        lock.lock();
        try {
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "this={0}, clientID={1}", new Object[]{this, clientID});
            }
            SequenceEntry entry = sequenceTable.get(clientID);
            if (entry != null && !entry.keep()) {
                /*
                 * REMIND: We could be removing the sequence number
                 * for a more recent lease, thus allowing a "late
                 * clean call" to be inappropriately processed?
                 * (See 4848840 Comments.) See River-142
                 * 
                 * FIXED see ObjectTable
                 */
                sequenceTable.remove(clientID);
            }

            if (referencedSet.remove(clientID) && referencedSet.isEmpty()) {
                implRef.unpin(this);
            }
        } finally {
            lock.unlock();
        }
    }
    
    private void interrupted(Thread currentThread) throws InterruptedException {
        if (currentThread.interrupted()) { // clears the interrupt status.
            throw new InterruptedException("Target interrupted during dispatch, unexported: " + unexported);
        }
    }

    void dispatch(InboundRequest request) throws IOException, NoSuchObject {
        if (!exported){ // optimisation to avoid locking.
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "this={0}, not exported", this);
            }
            throw new NoSuchObject();
        }
        Thread current = Thread.currentThread();
        boolean exitNormally = true;
        boolean callerAdded = false;
        try {
            InvocationDispatcher id = null;
            lock.lockInterruptibly();
            try {
                callerAdded = calls.add(current);
                if (!exported || invocationDispatcher == null) { // check again now we've got the lock.
                    if (logger.isLoggable(Level.FINEST)) {
                        logger.log(Level.FINEST, "this={0}, not exported", this);
                    }
                    throw new NoSuchObject();
                }
                id = invocationDispatcher;
            } finally {
                lock.unlock();
            }
            Remote impl = implRef.getImpl();
            if (impl == null) {
                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST, "this={0}, garbage collected", this);
                }
                throw new NoSuchObject();
            }
            interrupted(current);
            dispatch(request, id, impl, current);
            interrupted(current);
        } catch (InterruptedException ex) {
            exitNormally = false;
            request.abort();
            if (!interrupted){
                // Not interrupted by unexport, reset interrupted status.
                current.interrupt();
            }// else interrupt is swallowed.
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "this={0}, interrupted" , this);
            }
        }finally {
            // Either exit normally with clean up, or clean up if caller was added and unexport didn't interrupt.
            if ( exitNormally || (callerAdded && !interrupted)) { 
                lock.lock();
                try {
                    calls.remove(current);
                    if (!exported && calls.isEmpty()) { // In case Target was collected while call in progress.
                        decrementKeepAliveCount();
                    }
                }finally {
                    lock.unlock();
                }
            } // else exit without cleanup.
        }
    }

    private void dispatch(final InboundRequest request, final InvocationDispatcher id,
            final Remote impl, Thread t) throws IOException, NoSuchObject {
        ClassLoader savedCcl = t.getContextClassLoader();
        try {
            if (ccl != savedCcl) {
                t.setContextClassLoader(ccl);
            }
            AccessController.doPrivileged(securityContext.wrap(new PrivilegedExceptionAction() {

                public Object run() throws IOException, InterruptedException {
                    dispatch(request, id, impl);
                    return null;
                }
            }), securityContext.getAccessControlContext());
        } catch (PrivilegedActionException e) {
            throw (IOException) e.getException();
        } finally {
            if (ccl != savedCcl || savedCcl != t.getContextClassLoader()) {
                t.setContextClassLoader(savedCcl);
            }
        }
    }

    private void dispatch(final InboundRequest request, final InvocationDispatcher id, final Remote impl) throws IOException, InterruptedException {
        request.checkPermissions();
        interrupted(Thread.currentThread());
        OutputStream out = request.getResponseOutputStream();
        out.write(Jeri.OBJECT_HERE);
        final Collection context = new ArrayList(5);
        request.populateContext(context);
        ServerContext.doWithServerContext(new Runnable() {

            public void run() {
                id.dispatch(impl, request, context);
            }
        }, Collections.unmodifiableCollection(context));
    }

    public String toString() {
        // for logging
        return "Target@" + Integer.toHexString(hashCode()) + "[" + id + "]";
    }
}
