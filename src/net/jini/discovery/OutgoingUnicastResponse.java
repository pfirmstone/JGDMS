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

import java.io.ObjectOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.rmi.MarshalledObject;
import net.jini.core.lookup.PortableServiceRegistrar;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.io.MarshalledInstance;
import net.jini.io.MiToMoOutputStream;

/**
 * Encapsulate the details of marshaling a unicast response.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see IncomingUnicastResponse
 */
public class OutgoingUnicastResponse {
    /**
     * The current version of the unicast discovery protocol.
     */
    protected int protoVersion = 1;

    /**
     * Marshal a unicast response to the given output stream.  The
     * stream is flushed afterwards.
     * 
     * Older Versions of Apache River prior to 2.2.0 participating in a djinn,
     * will not have the ability to wrap a PortableServiceRegistrar, so it
     * can participate as a ServiceRegistrar instance. 
     * However it is intended that the Registrar's
     * proxy's implementation will be able to be unmarshalled
     * with a suitable codebase that also implements ServiceRegistrar for
     * recipients IncomingUnicastRequest's
     * 
     * At some future point in time, a djinn will have a minimum Apache River
     * version requirement for participation, at that stage old application
     * code will rely upon the PortableServiceRegistrarWrapper and ServiceRegistrar
     * will no longer be implemented directly by the proxy class.
     * 
     * At the same point in time the serialized form of the PortableServiceRegistrar
     * will be that of MarshalledInstance, not MarshalledObject as it currently is.
     *
     * @param str the stream to marshal to
     * @param reg the registrar object to marshal
     * @param groups the groups in which the registrar is currently a
     * member
     * @exception IOException a problem occurred during marshaling
     */
    public static void marshal(OutputStream str,
			       PortableServiceRegistrar reg,
			       String[] groups)
	throws IOException
    {
	ObjectOutputStream ostr = new MiToMoOutputStream(str);
	ostr.writeObject(new MarshalledInstance(reg));
	ostr.writeInt(groups.length);
	for (int i = 0; i < groups.length; i++) {
	    ostr.writeUTF(groups[i]);
	}
	ostr.flush();
    }
    
}
