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

package net.jini.jeri;

import java.rmi.Remote;
import java.util.Collection;

/**
 * An abstraction used to handle incoming call requests for a remote
 * object.  When exporting a remote object using a {@link
 * BasicJeriExporter}, an invocation dispatcher (and proxy) is created via
 * the {@link InvocationLayerFactory} in the exporter.
 *
 * <p>An invocation dispatcher is generally responsible for reading a
 * representation of the method to be invoked, unmarshalling the arguments
 * for the invocation, invoking the method on the target remote object with
 * those arguments, and marshalling the result of that invocation.
 *
 * @author	Sun Microsystems, Inc.
 * 
 * @see		BasicJeriExporter
 * @since 2.0
 **/
public interface InvocationDispatcher {

    /**
     * Dispatches the invocation represented by an {@link InboundRequest}
     * to the specified remote object.
     *
     * <p>Dispatching the invocation generally entails:
     * <ul>
     * <li>reading, from the inbound call object's input stream, a
     * representation of the method to be invoked and unmarshalling the
     * arguments for the invocation,
     * <li>invoking the method on the target remote object with those
     * arguments, and
     * <li>marshalling, to the inbound call object's output stream,
     * the result.
     * </ul>
     * <p>The result should generally be encoded in a manner
     * that will indicate to the reader of the response whether the result
     * is a return value or an exception.
     *
     * @param	impl a remote object
     * @param	request inbound request object for reading arguments and
     *		writing the result
     * @param	context a modifiable server context collection
     * @throws	NullPointerException if any argument is <code>null</code>
     **/
    public void dispatch(Remote impl,
			 InboundRequest request,
			 Collection context);
}
