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

package com.sun.jini.discovery;

import java.util.Arrays;
import net.jini.core.lookup.ServiceRegistrar;

/**
 * Class representing the values obtained as the result of unicast discovery.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
public class UnicastResponse {

    /** The lookup service host. */
    protected String host;
    /** The lookup service listen port. */
    protected int port;
    /** The lookup service member groups. */
    protected String[] groups;
    /** The lookup service proxy. */
    protected ServiceRegistrar registrar;

    /**
     * Creates new <code>UnicastResponse</code> instance containing the given
     * values.  The <code>groups</code> array is copied; a <code>null</code>
     * value is considered equivalent to an empty array.
     *
     * @param host the lookup service host
     * @param port the lookup service listen port
     * @param groups the lookup service member groups
     * @param registrar the lookup service proxy
     * @throws NullPointerException if <code>host</code> or
     * <code>registrar</code> is <code>null</code>, or if <code>groups</code>
     * contains <code>null</code>
     * @throws IllegalArgumentException if <code>port</code> is out of range
     */
    public UnicastResponse(String host,
			   int port,
			   String[] groups,
			   ServiceRegistrar registrar)
    {
	groups = (groups != null) ? (String[]) groups.clone() : new String[0];
	if (host == null ||
	    registrar == null ||
	    Arrays.asList(groups).contains(null))
	{
	    throw new NullPointerException();
	}
	if (port < 0 || port > 0xFFFF) {
	    throw new IllegalArgumentException("port out of range: " + port);
	}
	this.host = host;
	this.port = port;
	this.groups = groups;
	this.registrar = registrar;
    }

    /**
     * Returns the lookup service host name.
     *
     * @return the lookup service host name
     */
    public String getHost() {
	return host;
    }

    /**
     * Returns the lookup service listen port.
     *
     * @return the lookup service listen port
     */
    public int getPort() {
	return port;
    }

    /**
     * Returns the member groups of the lookup service.
     *
     * @return the member groups of the lookup service
     */
    public String[] getGroups() {
	return (String[]) groups.clone();
    }

    /**
     * Returns the lookup service proxy.
     *
     * @return the lookup service proxy
     */
    public ServiceRegistrar getRegistrar() {
	return registrar;
    }

    /**
     * Returns a string representation of this response.
     *
     * @return a string representation of this response
     */
    public String toString() {
	return "UnicastResponse[" + host + ":" + port + ", " +
	       Arrays.asList(groups) + ", " + registrar + "]";
    }
}
