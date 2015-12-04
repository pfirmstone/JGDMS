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
package net.jini.core.transaction.server;

/**
 * Constants common to transaction managers and participants. 
 *
 * @author Sun Microsystems, Inc.
 *
 * @since 1.0
 */
public interface TransactionConstants {
    /** Transaction is currently active. */
    final int ACTIVE = 1;
    /** Transaction is determining if it can be committed. */
    final int VOTING = 2;
    /** Transaction has been prepared but not yet committed. */
    final int PREPARED = 3;
    /** Transaction has been prepared with nothing to commit. */
    final int NOTCHANGED = 4;
    /** Transaction has been committed. */
    final int COMMITTED = 5;
    /** Transaction has been aborted. */
    final int ABORTED = 6;
}
