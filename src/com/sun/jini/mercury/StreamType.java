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

/**
 * Class that serves the purpose of an enumeration type. It's used 
 * in conjunction with <tt>StreamKey</tt> in order to distinguish among 
 * different <tt>LogStream</tt> objects that can potentially act upon the
 * same underlying log file.
 *
 * @author Sun Microsystems, Inc.
 *
 * @since 1.1
 */
class StreamType {

    /**
     * Simple constructor. Private protection is used in order to prevent
     * external instantiation of this class type.
     */
    private StreamType() {;}

    /** Event data output stream enumeration */
    static final StreamType OUTPUT = new StreamType();

    /** Event data input stream enumeration */
    static final StreamType INPUT = new StreamType();

    /** 
     * Control data stream enumeration. Control data will not use
     * separate input/output data streams and therefore no distinction
     * is made.
     */
    static final StreamType CONTROL = new StreamType();
}
