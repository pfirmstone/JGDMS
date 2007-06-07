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

package com.sun.jini.admin;

import java.rmi.RemoteException;
import java.io.IOException;

/**
 * Admin interface for controlling the location of a service's persistent
 * storage.  Administrable services are encouraged to have their admin
 * object implement this interface.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see net.jini.admin.Administrable#getAdmin
 */
public interface StorageLocationAdmin {
    /**
     * Returns the location of the service's persistent storage.
     * Typically returns a directory pathname unless the service
     * specifies otherwise.
     */
    String getStorageLocation() throws RemoteException;

    /**
     * Sets the location of the service's persistent storage,
     * moving all current persistent storage from the current
     * location to the specified new location.
     *
     * @exception IOException if moving the persistent storage fails
     */
    void setStorageLocation(String location)
	throws IOException, RemoteException;
}
