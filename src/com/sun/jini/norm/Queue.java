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
package com.sun.jini.norm;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Simple thread-safe FIFO sequence.
 *
 * @author Sun Microsystems, Inc.
 */
class Queue {
    /** List we actually store stuff in */
    final private List lst = Collections.synchronizedList(new LinkedList());

    /**
     * Enqueue an item.
     */
    synchronized void enqueue(Object o) {
	lst.add(o);
	notifyAll();
    }

    /**
     * Block until the queue is non-empty and return the first item.
     *
     * @throws InterruptedException if the current thread is interrupted
     *         while the queue is waiting for an item to appear
     */
    synchronized Object dequeue() throws InterruptedException {
	while (lst.isEmpty()) {
	    wait();
	}

	return lst.remove(0);
    }
}
