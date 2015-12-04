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
package org.apache.river.thread;

/**
 * Common Thread subclass to handle potential loss of
 * interrupted status.
 *  
 * @author Sun Microsystems, Inc.
 * @since 2.1
 */
public class InterruptedStatusThread extends Thread {
    
    /**
     * A Runnable task can implement this to be interrupted if something
     * special needs to be done to cause the Runnable to notice it's been
     * interrupted.
     * 
     * @since 3.0.0
     */
    public interface Interruptable {
        public void interrupt();
    }
    
    /** true if thread has been interrupted */
    private boolean interrupted = false;

    private final Interruptable task;
    /**
     * Constructs a new <code>InterruptedStatusThread</code> object.
     */
    public InterruptedStatusThread() {
	super();
        task = null;
    }

    /**
     * Constructs a new <code>InterruptedStatusThread</code> object.
     * @param target the object whose <code>run</code> method is called
     */
    public InterruptedStatusThread(Runnable target) {
	super(target);
        if (target instanceof Interruptable) task = (Interruptable) target;
        else task = null;
    }

    /**
     * Constructs a new <code>InterruptedStatusThread</code> object.
     * @param target the object whose <code>run</code> method is called
     * @param name the name of the new thread
     */
    public InterruptedStatusThread(Runnable target, String name) {
	super(target, name);
        if (target instanceof Interruptable) task = (Interruptable) target;
        else task = null;
    }

    /**
     * Constructs a new <code>InterruptedStatusThread</code> object.
     * @param name the name of the new thread
     */
    public InterruptedStatusThread(String name) {
	super(name);
        task = null;
    }

    /**
     * Constructs a new <code>InterruptedStatusThread</code> object.
     * @param group the thread group
     * @param target the object whose <code>run</code> method is called
     */
    public InterruptedStatusThread(ThreadGroup group, Runnable target) {
	super(group, target);
        if (target instanceof Interruptable) task = (Interruptable) target;
        else task = null;
    }

    /**
     * Constructs a new <code>InterruptedStatusThread</code> object.
     * @param group the thread group
     * @param target the object whose <code>run</code> method is called
     * @param name the name of the new thread
     */
    public InterruptedStatusThread(ThreadGroup group, 
				   Runnable target, 
				   String name) {
	super(group, target, name);
        if (target instanceof Interruptable) task = (Interruptable) target;
        else task = null;
    }

    /**
     * Constructs a new <code>InterruptedStatusThread</code> object.
     * @param group the thread group
     * @param target the object whose <code>run</code> method is called
     * @param name the name of the new thread
     * @param stackSize the desired stack size for the new thread, or zero to
     * indicate that this parameter is to be ignored
     */
    public InterruptedStatusThread(ThreadGroup group, 
				   Runnable target, 
				   String name,
				   long stackSize) {
	super(group, target, name, stackSize);
        if (target instanceof Interruptable) task = (Interruptable) target;
        else task = null;
    }

    /**
     * Constructs a new <code>InterruptedStatusThread</code> object.
     * @param group the thread group
     * @param name the name of the new thread
     */
    public InterruptedStatusThread(ThreadGroup group, String name) {
	super(group, name);
        task = null;
    }

    // inherit javadoc
    public synchronized void interrupt() {
	interrupted = true;
        if (task != null) task.interrupt();
	super.interrupt();
    }

    /**
     * Method used to determine if <code>interrupt</code> has been called
     * on this thread.
     * 
     * @return returns true if <code>interrupt</code> has been called on
     * this thread, false otherwise.
     */
    public synchronized boolean hasBeenInterrupted() {
	return interrupted;
    }

}
