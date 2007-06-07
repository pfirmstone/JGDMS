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
package com.sun.jini.mercury;

import java.io.IOException;

/**
 * Superclass for all <tt>LogStream</tt> types. 
 *
 * @author Sun Microsystems, Inc.
 *
 * @since 1.1
 */
 
interface LogStream {
    /**
     * Returns the associated key for this <tt>LogStream</tt>. 
     * This key is intended to be used as the key in a 
     * <tt>java.util.Collection</tt>.
     */
    Object getKey();

    /**
     * Closes this <tt>LogStream</tt> object and releases any 
     * associated resources.
     *
     * @exception IOException if an I/O error occured while attempting
     *                to close the <tt>LogStream</tt>.
     */
    void close() throws IOException;
}
