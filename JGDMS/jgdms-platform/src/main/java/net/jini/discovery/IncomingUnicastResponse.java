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

import java.io.InputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.rmi.MarshalledObject;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.io.MarshalledInstance;

/**
 * This class encapsulates the details of unmarshaling an incoming
 * unicast response.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see IncomingUnicastRequest
 */
public class IncomingUnicastResponse {
    /**
     * The registrar we have discovered.
     */
    protected ServiceRegistrar registrar;
    /**
     * The groups the lookup service was a member of, at the time
     * discovery was performed.  This may be out of date.
     */
    protected String[] groups;
    
    /**
     * Construct a new object, initialized by unmarshaling the
     * contents of an input stream.
     *
     * @param str the stream from which to unmarshal the response
     * @exception IOException an error occurred while unmarshaling the
     * response
     * @exception ClassNotFoundException some of the lookup service
     * classes could not be found or downloaded
     */
    public IncomingUnicastResponse(InputStream str)
	throws IOException, ClassNotFoundException
    {
	ObjectInputStream istr = new ObjectInputStream(str);
	registrar =
	    (ServiceRegistrar) new MarshalledInstance(
                    (MarshalledObject)istr.readObject()).get(false);
	int grpCount = istr.readInt();
	groups = new String[grpCount];
	for (int i = 0; i < groups.length; i++) {
	    groups[i] = istr.readUTF();
	}
    }

    /**
     * Return the lookup service registrar we have discovered.
     *
     * @return the lookup service registrar we have discovered
     */
    public ServiceRegistrar getRegistrar() {
	return registrar;
    }

    /**
     * Return the set of groups of which the lookup service we
     * discovered was a member when we discovered it.  This set may be
     * out of date.
     *
     * @return the set of groups to which the lookup service
     *         was a member when we discovered it
     *
     * @see net.jini.core.lookup.ServiceRegistrar#getGroups
     */
    public String[] getGroups() {
	return groups;
    }

    public int hashCode() {
	return registrar.hashCode();
    }

    /**
     * Two responses are equal if they have the same registrar.
     */
    public boolean equals(Object o) {
	return o instanceof IncomingUnicastResponse &&
	    ((IncomingUnicastResponse) o).registrar.equals(registrar);
    }
}
