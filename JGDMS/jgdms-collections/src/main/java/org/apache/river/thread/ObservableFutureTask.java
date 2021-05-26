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

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import org.apache.river.thread.FutureObserver.ObservableFuture;

/**
 *
 * @author peter
 */
public class ObservableFutureTask<T> extends FutureTask<T> implements ObservableFuture<T>{
    public final List<FutureObserver<T>> listeners;
    public volatile boolean done;

    public ObservableFutureTask(Callable<T> callable) {
        super(callable);
        listeners = new LinkedList<FutureObserver<T>>();
    }
    
    public ObservableFutureTask(Runnable r, T result){
        super(r,result);
        listeners = new LinkedList<FutureObserver<T>>();
    }
    
    @Override
    protected void done() {
        done = true;
        synchronized (listeners){
            Iterator<FutureObserver<T>> it = listeners.iterator();
            while (it.hasNext()){
                it.next().futureCompleted(this);
            }
        }
    }

    @Override
    public boolean addObserver(FutureObserver<T> l) {
        if (l == null) throw new NullPointerException("Null Listener");
        if (done){ 
            l.futureCompleted(this);
            return false;
        }
        synchronized (listeners){
            return listeners.add(l);
        }
    }
    
}
