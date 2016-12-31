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
package net.jini.discovery;

import java.io.DataInputStream;
import java.io.InputStream;
import java.io.IOException;

/**
 * Encapsulate the details of unmarshaling an incoming unicast
 * request.  This class has no public methods because the request
 * portion of the current version of the unicast discovery protocol
 * does nothing other than indicate its version.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see OutgoingUnicastRequest
 */
public class IncomingUnicastRequest {
    /**
     * The current version of the unicast discovery protocol.
     */
    protected int protoVersion = 1;

    /**
     * Construct a new object, initialized by unmarshaling the
     * contents of an input stream.
     *
     * @param str the stream from which to unmarshal a request
     * @exception IOException the request could not be unmarshaled
     */
    public IncomingUnicastRequest(InputStream str) throws IOException {
	DataInputStream dstr = new DataInputStream(str);
	int proto = dstr.readInt();
	if (proto != protoVersion)
	    throw new IOException("unsupported protocol version: " + proto);
    }
}
