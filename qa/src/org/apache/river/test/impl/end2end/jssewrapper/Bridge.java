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

package org.apache.river.test.impl.end2end.jssewrapper;

/**
 * This provides the communications bridge between the wrapper and
 * test code using the wrapper. Tests install callback objects in
 * ThreadLocal class attributes provided by this class.
 */

public class Bridge {

    /*
     * per-thread reference to the callback to be invoked
     * after the SecureInboundRequest is acquired and before
     * it is actually used by the JSSE provider
     */
    public static ThreadLocal readCallbackLocal = new ThreadLocal() ;

    /*
     * per-thread reference to the callback to be invoked
     * after the OutboundRequest is acquired and before
     * it is actually used by JSSE provider.
     */
    public static ThreadLocal writeCallbackLocal = new ThreadLocal() ;
}
