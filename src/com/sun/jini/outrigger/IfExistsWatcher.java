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

/**
 * Mix-in interface for <code>QueryWatcher</code>s associated
 * with ifExists queries.
 */
interface IfExistsWatcher {
    /**
     * If the query has been resolved by the locked entry set
     * becoming empty returns <code>true</code>, otherwise
     * return <code>false</code>.
     * @return <code>true</code> if locked entry set
     * associated with this query is empty.
     * @throws IllegalStateException if the query has not 
     * be resolved.
     */
    public boolean isLockedEntrySetEmpty();

    /**
     * Called after the last transition that was recored 
     * during the initial search has been processed. This
     * means that any entry that replaced an entry that 
     * was removed before the initial search had gotten to
     * it has been seen by the watcher. The query should now be
     * resolved with a <code>null</code> if the locked
     * entry set goes empty.
     */
    public void caughtUp();
}
