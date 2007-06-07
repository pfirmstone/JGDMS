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

package com.sun.jini.thread;

/**
 * Executor is an abstraction for a thread factory or thread pool for
 * executing actions asynchronously.
 *
 * @author	Sun Microsystems, Inc.
 * 
 */
public interface Executor {

    /**
     * Executes the given Runnable action asynchronously in some thread.
     *
     * The implementation may create a new thread to execute the action,
     * or it may execute the action in an existing thread.
     *
     * The execution of a given action must not be delayed indefinitely
     * in order to complete execution of a different action passed to a
     * different invocation of this method.  In other words, the
     * implementation must assume that there may be arbitrary dependencies
     * between actions passed to this method, so it needs to be careful
     * to avoid potential deadlock by delaying execution of one action
     * indefinitely until another completes.
     *
     * Also, this method itself must not block, because it may be invoked
     * by code that is serially processing data to produce multiple such
     * arbitrarily-dependent actions that need to be executed.
     *
     * @param	runnable the Runnable action to execute
     *
     * @param	name string to include in the name of the thread used
     * to execute the action
     */
    void execute(Runnable runnable, String name);
}
