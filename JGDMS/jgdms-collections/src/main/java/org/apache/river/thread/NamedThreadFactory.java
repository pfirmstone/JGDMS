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

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A utility to help readability of pool threads
 * @author peter
 */
public class NamedThreadFactory implements ThreadFactory {
    private final AtomicInteger threadCount;
    private final String name;
    private final boolean daemon;
    
    public NamedThreadFactory(String name, boolean daemon){
        threadCount = new AtomicInteger();
        this.name = name;
        this.daemon = daemon;
    }

    @Override
    public Thread newThread(Runnable r) {
        StringBuilder sb = new StringBuilder(name);
        sb.append('_');
        sb.append("thread-");
        sb.append(threadCount.getAndIncrement());
        Thread t = new Thread(r, sb.toString());
        t.setDaemon(daemon);
        return t;
    }
    
}
