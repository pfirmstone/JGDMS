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

package net.jini.url.httpmd;

import java.io.IOException;

/**
 * Thrown when the message digest for data retrieved from an HTTPMD URL does
 * not match the value specified in the URL.
 *
 * @author Sun Microsystems, Inc.
 * @see Handler
 * @since 2.0
 */
public class WrongMessageDigestException extends IOException {

    private static final long serialVersionUID = -5982551285440426248L;

    /**
     * Creates an instance with the specified detail message.
     *
     * @param s the detail message
     */
    public WrongMessageDigestException(String s) {
	super(s);
    }
}
