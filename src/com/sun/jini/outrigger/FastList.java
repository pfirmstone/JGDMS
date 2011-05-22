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

package com.sun.jini.outrigger;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simplified list for rapid append, removal, and scanning from multiple
 * threads. It is intended as a substitute for the previous FastList, but uses
 * an Iterator rather than limiting a thread to a single scan at a time.
 * 
 * This version is completely rewritten, based on
 * java.util.concurrent.ConcurrentLinkedQueue.
 * 
 * It provides two features in addition to those of ConcurrentLinkedQueue:
 * 
 * 1. Fast logical removal of a Node from the middle of the list. The node is
 * merely marked as removed. It will be physically removed during a reap, or any
 * Iterator scan that reaches it after it is marked.
 * 
 * 2. Guaranteed finite scans. If a node is added strictly after construction of
 * an Iterator, it will not be shown by the Iterator.
 * 
 * Concurrency: A FastList object can be freely accessed by multiple threads
 * without synchronization. Within a thread, the implementation synchronizes
 * on at most one node at a time. Conventionally, a caller who synchronizes on 
 * more than one node must do so in order of appearance in the list. While
 * synchronized on the FastList object, a caller must not synchronize on 
 * any FastList node or call any FastList method.
 * 
 * The Iterator returned by iterator() is not multi-thread safe. Callers
 * must ensure it is accessed by at most one thread at a time, and that
 * all actions on it in one thread happen-before any actions in a later
 * thread.
 * 
 * @param <T>
 *            Node type, required to extend FastList.Node so that the FastList
 *            can keep working data for each Node without using mapping or extra
 *            data structures.
 */
class FastList<T extends FastList.Node> implements Iterable<T> {

    /**
     * The type parameter for the FastList, T, must extend this type, and all
     * nodes added to the list are of type T.
     * 
     * A node can be added to a list at most once. Any attempt to add it again
     * will result in an IllegalStateException.
     * 
     */
    static class Node {
        /**
         * True if this node has been removed from its list. Protected by
         * synchronization on the Node when an exact answer is needed, but often
         * checked without synchronization to skip work of the Node is reported
         * as removed. Transitions only from false to true.
         */
        private volatile boolean removed;
        /**
         * This node does not need to be shown to scans with index greater than
         * or equal to this index.
         */
        private volatile long index;

        /**
         * null until the node is added to a list, then a reference to the list.
         * Once added to a list, it cannot be added to another. It can only be
         * removed from the list to which it was added. Protected by
         * synchronization on the node.
         */
        private FastList<?> list;

        /**
         * Remove this node from its list.
         * 
         * @return true if this node has never previously been removed, false if
         *         it has already been removed.
         */
        private synchronized boolean remove() {
            if (removed) {
                return false;
            }
            removed = true;
            return true;
        }

        synchronized void markOnList(FastList<?> list) {
            this.list = list;
        }

        /**
         * Report whether the node has been removed. If the result is true the
         * node has definitely been removed. If it is false, the node may still
         * have been removed. To get a fully reliable result, synchronize on the
         * node.
         */
        public boolean removed() {
            return removed;
        }
    }

    private class FastListIteratorImpl implements Iterator<T> {
        /* The last node returned as a next() result, null
         * if there is none, or it has already been removed.
         */
        private T removable;
        /* The node to be returned by the next call to next().*/
        private T next;
        /* Index of the first node added after this iterator's construction. */
        private final long index;
        /* Iterator over the underlying ConcurrentLinkedQueue.*/
        private final Iterator<T> baseIterator;

        private FastListIteratorImpl() {
            index = nextIndex.get();
            baseIterator = baseQueue.iterator();
            next = getNext();
        }

        public boolean hasNext() {
            return next != null;
        }

        public T next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }

            removable = next;
            next = getNext();
            return removable;
        }

        public void remove() {
            if (removable != null) {
                removable.remove();
                removable = null;
            } else {
                throw new IllegalStateException();
            }
        }

        /**
         * Find the next eligible node, null if there is none. skip over removed
         * nodes, and stop on reaching a Node that was added after this scan
         * started.
         * 
         * @return The next eligible node, null if there is none.
         */
        private T getNext() {
            T result = null;
            while (baseIterator.hasNext()) {
                T node = baseIterator.next();
                if (node.index >= index) {
                    /* Finished, no appropriate nodes.*/
                    break;
                }
                if (node.removed()) {
                    /* Tell the base list to drop it */
                    baseIterator.remove();
                } else {
                    /* Found a node to return. */
                    result = node;
                    break;
                }
            }
            return result;
        }
    }

    /**
     * The next index, a modified form of timestamp. Each Node is assigned a
     * strictly increasing index on being added to a FastList. Each Iterator
     * scan stops (hasNext() false) when it reaches a Node that has an index at
     * least as high as the value of nextIndex when the Iterator was created.
     * This ensures finite scan lengths, even if nodes are being added
     * continuously during the scan.
     */
    private final AtomicLong nextIndex = new AtomicLong(0);

    /**
     * The underlying queue.
     */
    private final Queue<T> baseQueue = new ConcurrentLinkedQueue<T>();

    /**
     * Add a node to the tail of the list.
     * 
     * @param node
     *            Each node can only be added once, regardless of removal.
     * @throws IllegalArgumentException
     *             The node has been added to a list previously.
     */
    public void add(T node) {
        synchronized (node) {
            if (node.list == null) {
                node.list = this;
            } else {
                throw new IllegalArgumentException("Attempt to reuse node "
                        + node);
            }
        }
        node.index = nextIndex.getAndIncrement();
        baseQueue.add(node);
    }

    /**
     * Remove the node.
     * 
     * @param node
     * @return true if this is the first remove call for this node, false if the
     *         node has already been removed.
     * @throws IllegalArgumentException
     *             The node has not been added to this FastList.
     */
    public boolean remove(T node) {
        synchronized (node) {
            if (node.list != this) {
                throw new IllegalArgumentException(
                        "Cannot remove a node from a list it is not on");
            }
            return node.remove();
        }
    }

    /**
     * Scan the list, physically removing nodes that have already been logically
     * removed.
     */
    public void reap() {
        long stopIndex = nextIndex.get();
        Iterator<T> it = baseQueue.iterator();
        while (it.hasNext()) {
            T node = it.next();
            if (node.index >= stopIndex) {
                // Done enough
                return;
            }
            if (node.removed()) {
                it.remove();
            }
        }
    }

    /*
     * The returned Iterator returns all elements that were
     * added to the FastList, and not removed, before the iterator() call.
     * It will return no elements that were added after the iterator() call
     * returned. Elements that were added in parallel with the iterator()
     * call may be returned, depending on exact timing.
     * 
     * It makes reasonable efforts to avoid returning removed elements, but
     * only a synchronized check can guaranteed up-to-date removal information.
     * (non-Javadoc)
     * @see java.lang.Iterable#iterator()
     */
    public Iterator<T> iterator() {
        return new FastListIteratorImpl();
    }
    
    /**
     * Iterator that includes all physically present items,
     * regardless of when they were added or whether they have
     * been logically removed. This method is intended for
     * testing. For example, it can be used to verify
     * that a reap() call does in fact physically remove
     * the items it should remove.
     * 
     * @return
     */
    Iterator<T> rawIterator() {
        return baseQueue.iterator();
    }

}
