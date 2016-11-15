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

package net.jini.lookup;

import java.rmi.RemoteException;
import java.io.IOException;

/**
 * Methods for controlling which groups a lookup service is a member of,
 * and for controlling which TCP port a lookup service uses for its
 * lookup locator.  Lookup services that implement the Administrable
 * interface should return an admin object that implements this interface.
 * 
 * @author Sun Microsystems, Inc.
 */
public interface DiscoveryAdmin {

    /**
     * Returns an array consisting of the names of the groups in which the
     * lookup service is a member. If the lookup service currently belongs
     * to no groups, this method will return the empty array.
     *
     * @return <code>String</code> array consisting of the names of the groups
     *         in which the lookup service is a member
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         server.
     */
    String[] getMemberGroups() throws RemoteException;

    /**
     * Adds the given set of names to the set whose elements are the names
     * of the groups in which the lookup service is currently a member.
     * Elements in the input set that duplicate names of groups in which
     * the lookup service is already a member will be ignored. If the empty
     * array (<code>NO_GROUPS</code>) is input, this method takes no action.
     *
     * @param groups <code>String</code> array consisting of the names of
     *               the new, additional groups in which the lookup service
     *               is to be a member.
     *
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         server.
     *
     * @throws java.lang.NullPointerException this exception occurs when
     *         either <code>null</code> is input to the <code>groups</code>
     *         parameter, or one or more of the elements of the
     *         <code>groups</code> parameter is <code>null</code>.
     */
    void addMemberGroups(String[] groups) throws RemoteException;

    /**
     * Deletes the elements of the given set of names from the set whose
     * elements are the names of the groups in which the lookup service is
     * currently a member. Any element in the input set that is not a name
     * of a group in which the lookup service is currently a member will be
     * ignored. If the empty array (<code>NO_GROUPS</code>) is input, this
     * method takes no action.
     *
     * @param groups <code>String</code> array consisting of the names to 
     *               remove from the set whose elements are the names of the
     *               groups in which the lookup service is currently a member.
     *
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         server.
     *
     * @throws java.lang.NullPointerException this exception occurs when
     *         either <code>null</code> is input to the <code>groups</code>
     *         parameter, or one or more of the elements of the
     *         <code>groups</code> parameter is <code>null</code>.
     */
    void removeMemberGroups(String[] groups) throws RemoteException;

    /**
     * Replaces the set whose elements are the names of the groups in which
     * the lookup service is currently a member with the given set of group
     * names. Elements in the input set that duplicate other elements in the
     * input set will be ignored. If the empty array (<code>NO_GROUPS</code>)
     * is input, then the lookup service will be a member of no groups.
     *
     * @param groups <code>String</code> array consisting of the names of
     *               the new groups in which the lookup service is to be a
     *               member.
     *
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         server.
     *
     * @throws java.lang.NullPointerException this exception occurs when
     *         either <code>null</code> is input to the <code>groups</code>
     *         parameter, or one or more of the elements of the
     *         <code>groups</code> parameter is <code>null</code>.
     */
    void setMemberGroups(String[] groups) throws RemoteException;

    /**
     * Returns the port number on which the lookup service listens for
     * unicast discovery queries.
     *
     * @return an <code>int</code> representing the port number on which the
     *         lookup service listens for unicast discovery queries.
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         server.
     */
    public int getUnicastPort() throws RemoteException;

    /**
     * Changes the number of the port on which the lookup service is currently
     * listening for unicast discovery queries to the given port number.
     * If a value of zero is input, then the lookup service will first try
     * to listen on the standard unicast discovery port, but if that fails,
     * the lookup service will listen on an arbitrary port.
     *
     * @param port <code>int</code> representing the new port number on which
     *             the lookup service should listen for unicast discovery
     *             queries.
     *
     * @throws java.io.IOException because an invocation of this method will
     *         result in the re-initiation of the unicast discovery process,
     *         which can throw an <code>IOException</code> when socket
     *         allocation occurs.
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         server.
     */
    public void setUnicastPort(int port) throws IOException, RemoteException;
}
