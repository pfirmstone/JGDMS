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
package com.sun.jini.test.spec.url.httpmd.util;

// java.util
import java.util.logging.Level;

// davis packages
import net.jini.url.httpmd.Handler;

// java.net
import java.net.URL;
import java.net.URLConnection;

// java.io
import java.io.IOException;


/**
 * This class extends {@link Handler}
 * class overriding all methods.
 */
public class TestHandler extends Handler {

    /**
     * No-args constructor
     */
    public TestHandler() {
        super();
    }

    /**
     * Returns the default port for a URL parsed by this handler.
     *
     * @return the default port for a URL parsed by this handler
     */
    public int getDefaultPort() {
        return super.getDefaultPort();
    }

    /**
     * Computes the hash code for the specified URL.
     *
     * @param u URL object
     * @return an int suitable for hash table indexing
     */
    public int hashCode(URL u) {
        return super.hashCode(u);
    }

    /**
     * Creates a HTTP URL connection for an HTTPMD URL.
     *
     * @param u the URL that this connects to
     * @throws IOException if an I/O error occurs while opening the connection
     * @return a URLConnection object for the URL
     */
    public URLConnection openConnection(URL u) throws IOException {
        return super.openConnection(u);
    }

    /**
     * Parses the string representation of an HTTPMD URL object.
     *
     * @param url the URL to receive the result of parsing the spec.
     * @param spec the String representing the URL that must be parsed.
     * @param start the character index at which to begin parsing.
     * @param limit the character position to stop parsing at.
     */
    public void parseURL(URL url, String spec, int start, int limit) {
        super.parseURL(url, spec, start, limit);
    }

    /**
     * Compares two HTTPMD URLs to see if they refer to the same file.
     *
     * @param u1 a URL object
     * @param u2 a URL object
     * @return true if u1 and u2 refer to the same file
     */
    public boolean sameFile(URL u1, URL u2) {
        return super.sameFile(u1, u2);
    }
}
