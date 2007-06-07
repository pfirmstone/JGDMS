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

package com.sun.jini.jeri.internal.http;

/**
 * Interface implemented by connections which can be timed out.
 *
 * @author Sun Microsystems, Inc.
 * 
 */
public interface TimedConnection {
    
    /**
     * Attempts to shut down connection, returning true if connection is
     * closed.  If force is true, connection is always shut down; if force is
     * false, connection is only shut down if idle.
     */
    boolean shutdown(boolean force);
}
