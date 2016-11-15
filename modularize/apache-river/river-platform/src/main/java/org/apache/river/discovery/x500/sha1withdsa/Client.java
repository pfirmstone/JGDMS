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

package org.apache.river.discovery.x500.sha1withdsa;

import org.apache.river.discovery.internal.MulticastClient;
import org.apache.river.discovery.internal.X500Client;

/**
 * Implements the client side of the
 * <code>net.jini.discovery.x500.SHA1withDSA</code> format.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
public class Client extends MulticastClient
{

    /**
     * Constructs a new instance.
     */
    public Client() {
	super(new ClientImpl());
    }

    private static final class ClientImpl extends X500Client {
	ClientImpl() {
	    super(Constants.FORMAT_NAME,
		  Constants.SIGNATURE_ALGORITHM,
		  Constants.MAX_SIGNATURE_LEN,
		  Constants.KEY_ALGORITHM,
		  Constants.KEY_ALGORITHM_OID);
	}
    }
}
