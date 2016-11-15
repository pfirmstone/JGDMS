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

package net.jini.export;

/**
 * Provides a means to obtain a proxy from an exported remote object.
 *
 * <p>This interface is typically used in conjunction with activatable
 * remote objects.  An activatable remote object can implement this
 * interface so that its proxy can be obtained once the object is
 * activated.  If an activatable remote object does not implement this
 * interface, it must define a constructor that takes as arguments an
 * {@link java.rmi.activation.ActivationID} and a {@link
 * java.rmi.MarshalledObject}, and it must be serializable and marshalling
 * the object produces a suitable proxy for the remote object.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 **/
public interface ProxyAccessor {

    /**
     * Returns a proxy object for this remote object.  If this remote
     * object is not exported (and hence, no proxy is available), then
     * <code>null</code> is returned.
     *
     * @return a proxy, or <code>null</code>
     **/
    Object getProxy();
}
