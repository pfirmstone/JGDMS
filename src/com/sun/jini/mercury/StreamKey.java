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

import java.io.File;

/**
 * Class used as the key value for an associated <tt>LogStream</tt> object in 
 * a collection of type <tt>java.util.Map</tt>.
 *
 * @author Sun Microsystems, Inc.
 *
 * @since 1.1
 */
class StreamKey {

    /** Holds the <tt>File</tt> attribute for the associated stream. */
    private /*final*/ File file;

    /** Holds the "enumeration" type for the associated stream. */
    private /*final*/ StreamType type;

    /** Holds the cached value of the <tt>file</tt> field's hashCode. */
    private /*final*/ int hash;

    /** 
     * Simple constructor that accepts <tt>File</tt> and <tt>StreamType</tt>
     * arguments and then assigns them to the appropriate internal fields.
     * Neither argument can be <code>null</code>. The <tt>File</tt> 
     * argument must represent an absolute pathname.
     *
     * @exception IllegalArgumentException thrown if either of the arguments are
     *                                     null or if the <tt>file</tt> 
     *                                     argument does not represent an
     *                                     absolute pathname.
     */

    StreamKey(File file, StreamType type) {
        if (file == null || type == null)
            throw new IllegalArgumentException("Cannot use <null> for " + 
                "path or type arguments.");

        // Note: relative and absolute paths to the same file
        // are not considered equal. Therefore, we ensure that all 
        // file arguments are absolute so that <tt>equals</tt> behaves
        // as expected.
        if (!file.isAbsolute())
            throw new IllegalArgumentException("Cannot use a relative path " + 
                                               "for the <file> argument");
        this.file = file;
        this.type = type;
        this.hash = file.hashCode(); //cache it since it won't change
    }

    /* Inherit javadoc from supertype */
    public int hashCode() {
	return hash;
    }

    /* Inherit javadoc from supertype */
    public boolean equals(Object o) {
        if (this == o)
            return true;

        if (o == null)
            return false;

        if (o.getClass() != getClass())
            return false;

	StreamKey sk = (StreamKey)o;
	return ((type == sk.type) && (file.equals(sk.file)));
    }
}
