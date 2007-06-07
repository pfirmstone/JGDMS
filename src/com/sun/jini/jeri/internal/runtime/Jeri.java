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

package com.sun.jini.jeri.internal.runtime;

import com.sun.jini.action.GetLongAction;
import java.security.AccessController;
import net.jini.id.Uuid;
import net.jini.id.UuidFactory;

/**
 * Provides constants for this package.
 *
 * @author Sun Microsystems, Inc.
 **/
final class Jeri {

    /** duration of DGC leases granted by this runtime */
    static final long leaseValue =			// default 10 minutes
	((Long) AccessController.doPrivileged(new GetLongAction(
	    "com.sun.jini.jeri.dgc.leaseValue", 600000)))
	    .longValue();

    /** period of checking for DGC lease expiration */
    static final long leaseCheckInterval =		// default 5 minutes
	((Long) AccessController.doPrivileged(new GetLongAction(
	    "com.sun.jini.jeri.dgc.checkInterval", leaseValue / 2)))
	    .longValue();

    static final int NO_SUCH_OBJECT	= 0x00;
    static final int OBJECT_HERE	= 0x01;

    static final Uuid DGC_ID =
	UuidFactory.create("d32cd1bc-273c-11b2-8841-080020c9e4a1");

    /** Prevents instantiation. */
    private Jeri() { throw new AssertionError(); }
}
