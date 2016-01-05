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

import java.util.EventListener;
import java.util.concurrent.Future;

/**
 *
 * @author peter
 */
public interface FutureObserver<T> extends EventListener {
    
    public void futureCompleted(Future<T> e);
    
    public interface ObservableFuture<T> extends Future<T> {
        /**
         * Adds FutureObserver's to this ObservableFuture.
         * 
         * @param observer to observe this.
         * @return true if observer was added, false otherwise.
         */
        public boolean addObserver(FutureObserver<T> observer);
    }  
}
