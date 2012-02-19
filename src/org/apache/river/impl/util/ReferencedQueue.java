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

package org.apache.river.impl.util;

import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.util.Queue;

/**
 *
 * @param <T> 
 * @author Peter Firmstone.
 */
public class ReferencedQueue<T> extends ReferenceCollection<T> implements Queue<T> {
    private static final long serialVersionUID = 1L;
    private final Queue<Referrer<T>> queue;
    
    public ReferencedQueue( Queue<Referrer<T>> queue, Ref type, boolean gcThreads){
        super(queue, type, gcThreads);
        this.queue = queue;
    }
    
    private void readObject(ObjectInputStream stream) 
            throws InvalidObjectException{
        throw new InvalidObjectException("Builder required");
    }
    
    public boolean offer(T e) {
        processQueue();
        Referrer<T> r = wrapObj(e, true, false);
        return queue.offer(r);
    }

    public T remove() {
        processQueue();
        Referrer<T> t = queue.remove();
        if ( t != null ) return t.get();
        return null;
    }

    public T poll() {
        processQueue();
        Referrer<T> t = queue.poll();
        if ( t != null ) return t.get();
        return null;
    }

    public T element() {
        processQueue();
        Referrer<T> t = queue.element();
        if ( t != null ) return t.get();
        return null;
    }

    public T peek() {
        processQueue();
        Referrer<T> t = queue.peek();
        if ( t != null ) return t.get();
        return null;
    }
    
}
