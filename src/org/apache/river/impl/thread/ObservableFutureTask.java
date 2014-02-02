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

package org.apache.river.impl.thread;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import org.apache.river.api.util.FutureObserver;
import org.apache.river.api.util.FutureObserver.Subscribeable;
import org.apache.river.api.util.FutureObserver.ObservableFuture;
import org.apache.river.api.util.FutureObserver.Subscriber;

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
        if (callable instanceof Subscribeable){
            ((Subscribeable<T>) callable).subscribe(new FutureSubscriber(listeners));
        }
    }
    
    public ObservableFutureTask(Runnable r, T result){
        super(r,result);
        listeners = new LinkedList<FutureObserver<T>>();
        if (r instanceof Subscribeable){
            ((Subscribeable<T>) r).subscribe(new FutureSubscriber(listeners));
        }
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
    
    private static final class FutureSubscriber<T> implements Subscriber<T> {
        /* Shared list of subscribers */
        public final List<FutureObserver<T>> listeners;
        
        FutureSubscriber(List<FutureObserver<T>> listeners){
            this.listeners = listeners;
        }
        @Override
        public void reccommendedViewing(ObservableFuture<T> e) {
            synchronized (listeners){
                Iterator<FutureObserver<T>> it = listeners.iterator();
                while (it.hasNext()){
                    FutureObserver<T> future = it.next();
                    if (future instanceof Subscriber){
                        Subscriber<T> subscriber = (Subscriber<T>) future;
                        subscriber.reccommendedViewing(e);
                    }
                }
            }
        }
    }
}
