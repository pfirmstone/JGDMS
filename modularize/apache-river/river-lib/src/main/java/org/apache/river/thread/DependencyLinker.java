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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RunnableFuture;

/**
 *
 * @author peter
 */
public class DependencyLinker implements FutureObserver {
    private final ExecutorService executor;
    private final List<ObservableFuture> tasks;
    private final RunnableFuture dependant;

    public DependencyLinker(ExecutorService ex, List<ObservableFuture> tasks, RunnableFuture dep) {
        executor = ex;
        this.tasks = new ArrayList<ObservableFuture>(tasks);
        dependant = dep;
    }

    public synchronized void register() {
        // Iterator causes ConcurrentModificationException
        for (int i = 0, l = tasks.size(); i < l; i++){
            ObservableFuture f = null;
            try {
                f = tasks.get(i);
            } catch (IndexOutOfBoundsException e){
                continue;
            }
            if (f != null) f.addObserver(this);
        }
    }

    @Override
    public synchronized void futureCompleted(Future e) {
        tasks.remove(e);
        if (tasks.isEmpty()) {
            executor.submit(dependant);
        }
    }
    
}
