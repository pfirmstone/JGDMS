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
package com.sun.jini.norm.lookup;

import java.io.File;
import java.io.IOException;
import net.jini.config.ConfigurationException;

/**
 * Interface components must meet if they implement their own persistent store.
 *
 * @author Sun Microsystems, Inc.
 */
public interface SubStore {
    /**
     * If this components wants its own sub-directory, it should return
     * a non-<code>null</code> string that will be its sub-directory's name.
     * If it does not need its own sub-directory this method should return
     * <code>null</code>.
     */
    public String subDirectory();

    /**
     * Gives the <code>SubStore</code> a piece of the file system to 
     * use for its store.
     * @param dir the directory to use
     * @throws IOException if there is a problem initializing its store
     *         or recovering its state
     * @throws ConfigurationException if this is a problem configuring this
     *	       object
     */
    public void setDirectory(File dir)
	throws ConfigurationException, IOException;

    /**
     * Informs the <code>SubStore</code> that the service is being destroyed
     * and it should perform any necessary cleanup (closing files for example).
     * The store does not need to delete it's data.
     */
    public void prepareDestroy();
}
