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
package com.sun.jini.outrigger.snaplogstore;

import com.sun.jini.outrigger.StorableResource;
import com.sun.jini.outrigger.StoredResource;
import net.jini.id.Uuid;

import java.io.IOException;
import java.util.Arrays;

/**
 * Wrapper for outrigger objects that are leased resources.
 * This class records renews so that the
 * stored resource can be updated while the target is serialized.
 * When the stored resource is deserialized the (potentially)
 * updated expiration is set in the resource before it is returned.
 */
class Resource extends BaseObject implements StoredResource {
    static final long serialVersionUID = -4248052947306243840L;

    private byte[]	cookie;
    private long	expiration;

    Resource(StorableResource resource) {
	super(resource);
	final Uuid uuid = resource.getCookie();
	cookie = ByteArrayWrapper.toByteArray(uuid);
	expiration = resource.getExpiration();
    }

    ByteArrayWrapper getCookieAsWrapper() {
	return new ByteArrayWrapper(cookie);
    }

    void setExpiration(long newExpiration) {
	expiration = newExpiration;
    }

    public void restore(StorableResource obj)
	throws IOException, ClassNotFoundException 
    {
	super.restore(obj);

	// Set the objects expiration to be the current one (it may still
	// be the original expiration)
	//
	obj.setExpiration(expiration);
    }

    public int hashCode() {
        return ByteArrayWrapper.hashFor(cookie);
    }
 
    public boolean equals(Object o) {
        if (o == null)
            return false;
 
        if (!(o instanceof Resource))
            return false;
 
        // Same object if same cookie.
	return Arrays.equals(cookie, ((Resource)o).cookie);
    }
}
