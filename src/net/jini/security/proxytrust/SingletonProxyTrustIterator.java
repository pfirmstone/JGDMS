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

package net.jini.security.proxytrust;

import java.rmi.RemoteException;
import java.util.NoSuchElementException;

/**
 * A simple <code>ProxyTrustIterator</code> that produces a single object
 * as the only element of the iteration.
 *
 * @author Sun Microsystems, Inc.
 * 
 * @since 2.0
 */
public class SingletonProxyTrustIterator implements ProxyTrustIterator {
    /** The object to produce */
    private Object obj;
    /** True if setException can be called */
    private boolean settable = false;

    /**
     * Creates an instance with the specified object to use as the only
     * element of the iteration.
     *
     * @param obj the object to use as the element of the iteration
     * @throws NullPointerException if the argument is <code>null</code>
     */
    public SingletonProxyTrustIterator(Object obj) {
	if (obj == null) {
	    throw new NullPointerException("cannot be null");
	}
	this.obj = obj;
    }

    public synchronized boolean hasNext() {
	settable = false;
	return obj != null;
    }

    /**
     * @throws NoSuchElementException {@inheritDoc}
     */
    public synchronized Object next() {
	if (obj == null) {
	    throw new NoSuchElementException();
	}
	settable = true;
	Object res = obj;
	obj = null;
	return res;
    }

    /**
     * @throws NullPointerException {@inheritDoc}
     * @throws IllegalStateException {@inheritDoc}
     */
    public synchronized void setException(RemoteException e) {
	if (e == null) {
	    throw new NullPointerException("exception cannot be null");
	} else if (!settable) {
	    throw new IllegalStateException();
	}
	settable = false;
    }
}
