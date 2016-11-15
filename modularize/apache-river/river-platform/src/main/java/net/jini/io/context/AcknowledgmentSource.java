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

package net.jini.io.context;

/**
 * A server context element for registering interest in receiving an
 * acknowledgment that the remote call's result data has been
 * delivered to and processed by the client.
 *
 * @author Sun Microsystems, Inc.
 * @see net.jini.export.ServerContext#getServerContextElement
 * @since 2.0
 **/
public interface AcknowledgmentSource {

    /**
     * Registers the specified listener as interested in receiving an
     * acknowledgment that the remote call's result data has been
     * processed by the client.  Multiple listeners may be registered
     * with this object.
     *
     * <p>The implementation of this interface may refuse a
     * registration, such as if the remote call's result data has
     * already been written.  This method returns <code>true</code> if
     * the listener was successfully registered, and
     * <code>false</code> if the registration was refused.
     *
     * @param listener the listener to register
     *
     * @return <code>true</code> if the listener was successfully
     * registered, and <code>false</code> if the registration was
     * refused
     *
     * @throws NullPointerException if <code>listener</code> is
     * <code>null</code>
     **/
    boolean addAcknowledgmentListener(Listener listener);

    /**
     * A callback object for registering with an {@link
     * AcknowledgmentSource} server context element to handle the
     * receipt of an acknowledgment that the remote call's result data
     * has been processed by the client.
     **/
    interface Listener {

	/**
	 * Handles either receipt of an acknowledgment that the remote
	 * call's result data has been processed by the client or an
	 * indication that no acknowledgment will be received.
	 *
	 * <p>If <code>received</code> is <code>true</code>, then a
	 * positive acknowledgment has been received that the remote
	 * call's result data has been processed by the client.
	 *
	 * If <code>received</code> is <code>false</code>, then the
	 * implementation of this interface has determined that no
	 * positive acknowledgment for the associated data will be
	 * received (perhaps due, for example, to connection failure
	 * or timeout).
	 *
	 * @param received <code>true</code> if an acknowledgment was
	 * received, and <code>false</code> if no acknowledgment will
	 * be received
	 **/
	void acknowledgmentReceived(boolean received);
    }
}
