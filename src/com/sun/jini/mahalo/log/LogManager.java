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
package com.sun.jini.mahalo.log;

import net.jini.admin.Administrable;

/**
 * Represents the functions performed by a log manager.
 *
 * @author Sun Microsystems, Inc.
 *
 */
public interface LogManager extends Administrable {

    /**
     *  Factory method which returns a Log interface which
     *  can be used to create persistent records which
     *  collectively represent a system's state.
     *
     * @param cookie identifier representing the information
     *               being logged.
     */
    ClientLog logFor(long cookie) throws LogException;


    /**
     *  Consumes the log file and re-constructs a system's
     *  state.
     */
    void recover() throws LogException;

    /**
     * Returns an object that implements whatever administration interfaces
     * are appropriate for the <code>LogManager</code>. This method
     * overrides what is defined in the supertype.
     *
     * @return an object that implements whatever administration interfaces
     *         are appropriate for the particular service.
     *
     * @see net.jini.admin.Administrable
     */
    Object getAdmin();
}
