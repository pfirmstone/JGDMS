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

package org.apache.river.phoenix;

import java.rmi.MarshalledObject;
import java.rmi.RemoteException;
import java.rmi.activation.ActivationException;
import java.rmi.activation.ActivationGroup;
import java.rmi.activation.ActivationGroupDesc;
import java.rmi.activation.ActivationGroupID;
import java.rmi.activation.ActivationMonitor;
import java.rmi.activation.ActivationSystem;
import java.rmi.server.UnicastRemoteObject;
import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import net.jini.config.Configuration;
import net.jini.export.Exporter;
import net.jini.export.ProxyAccessor;
import net.jini.security.ProxyPreparer;

/**
 * The default activation group implementation for phoenix.  Instances of
 * this class are configurable through a {@link Configuration}, as detailed
 * further below, and provide the necessary support to allow exporter-based
 * remote objects to go inactive.  Instances of this class support the
 * creation of remote objects through the normal activatable constructor;
 * an activatable remote object must either implement the {@link
 * ProxyAccessor} interface to return a suitable proxy for the remote
 * object, or the remote object must itself be serializable and marshalling
 * the object must produce a suitable proxy for the remote object.
 * 
 * <p>An instance of this class can be configured by specifying an
 * {@link ActivationGroupData} instance containing configuration options
 * as the initialization data for the activation group. Typically
 * this is accomplished indirectly, by setting the
 * <code>groupConfig</code> configuration entry for
 * phoenix itself. The following entries are obtained from the configuration,
 * all for the component named <code>org.apache.river.phoenix</code>:
 *
 *  <table summary="Describes the loginContext configuration entry"
 *         border="0" cellpadding="2">
 *    <tr valign="top">
 *      <th scope="col">&#X2022;
 *      <th scope="col" align="left" colspan="2"> <code>
 *      loginContext</code>
 *    <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *      Type: <td> <code>{@link javax.security.auth.login.LoginContext}</code>
 *    <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *      Default: <td> <code>null</code>
 *    <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *      Description: <td> JAAS login context
 *  </table>
 *
 *  <table summary="Describes the inheritGroupSubject configuration entry"
 *         border="0" cellpadding="2">
 *    <tr valign="top">
 *      <th scope="col">&#X2022;
 *      <th scope="col" align="left" colspan="2"><code>
 *      inheritGroupSubject</code>
 *    <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *      Type: <td> <code>boolean</code>
 *    <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *      Default: <td> <code>false</code>
 *    <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *      Description: <td> if <code>true</code>, group subject is inherited
 *		when an activatable object is created 
 *  </table>
 *
 *  <table summary="Describes the instantiatorExporter configuration entry"
 *         border="0" cellpadding="2">
 *    <tr valign="top">
 *      <th scope="col">&#X2022;
 *      <th scope="col" align="left" colspan="2"><code>
 *      instantiatorExporter</code>
 *    <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *      Type: <td> <code>{@link net.jini.export.Exporter}</code>
 *    <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *      Default: <td> retains existing JRMP export of instantiator
 *    <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *      Description: <td> {@link java.rmi.activation.ActivationInstantiator}
 *		exporter
 *  </table>
 *
 *  <table summary="Describes the monitorPreparer configuration entry"
 *         border="0" cellpadding="2">
 *    <tr valign="top">
 *      <th scope="col">&#X2022;
 *      <th scope="col" align="left" colspan="2"><code>
 *      monitorPreparer</code>
 *    <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *      Type: <td> <code>{@link net.jini.security.ProxyPreparer}</code>
 *    <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *      Default: <td> <code>new {@link
 *		net.jini.security.BasicProxyPreparer}()</code> 
 *    <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *      Description: <td> {@link java.rmi.activation.ActivationMonitor}
 *		proxy preparer 
 *  </table>
 *
 *  <table summary="Describes the systemPreparer configuration entry"
 *         border="0" cellpadding="2">
 *    <tr valign="top">
 *      <th scope="col">&#X2022;
 *      <th scope="col" align="left" colspan="2"><code>
 *      systemPreparer</code>
 *    <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *      Type: <td> <code>{@link net.jini.security.ProxyPreparer}</code>
 *    <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *      Default: <td> <code>new {@link
 *		net.jini.security.BasicProxyPreparer}()</code> 
 *    <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *      Description: <td> {@link java.rmi.activation.ActivationSystem}
 *		proxy preparer 
 *  </table>
 *
 *  <table summary="Describes the unexportTimeout configuration entry"
 *         border="0" cellpadding="2">
 *    <tr valign="top">
 *      <th scope="col">&#X2022;
 *      <th scope="col" align="left" colspan="2"><code>
 *      unexportTimeout</code>
 *    <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *      Type: <td> <code>int</code>
 *    <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *      Default: <td> <code>60000</code>
 *    <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *      Description: <td> maximum time in milliseconds to wait for
 *		in-progress calls to finish before forcibly unexporting the
 *		group when going inactive 
 *  </table>
 *
 *  <table summary="Describes the unexportWait configuration entry"
 *         border="0" cellpadding="2">
 *    <tr valign="top">
 *      <th scope="col">&#X2022;
 *      <th scope="col" align="left" colspan="2"><code>
 *      unexportWait</code>
 *    <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *      Type: <td> <code>int</code>
 *    <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *      Default: <td> <code>10</code>
 *    <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *      Description: <td> milliseconds to wait between unexport attempts
 *		when going inactive 
 *  </table>
 * 
 * <p>This class depends on its {@link #createGroup createGroup} method being
 * called to initialize the activation group. As such, this class cannot be
 * used in conjunction with the standard <code>rmid</code>.
 *
 * @author Sun Microsystems, Inc.
 * 
 * @since 2.0
 **/
public class ActivationGroupImpl extends AbstractActivationGroup {
   
    /**
     * Creates an {@link java.rmi.activation.ActivationGroup} instance and
     * returns it. An {@link ActivationGroupData} instance is extracted from
     * the initialization data, and a {@link Configuration} is obtained by
     * calling
     * {@link net.jini.config.ConfigurationProvider#getInstance
     * Configuration.Provider.getInstance} with the configuration options from
     * that instance. A {@link LoginContext} is obtained from the
     * <code>loginContext</code> configuration entry, if one exists; if the
     * value is not <code>null</code>, a login is performed on that context,
     * and the resulting {@link Subject} (set to be read-only) is used as the
     * subject when executing the rest of this method. The subject is also
     * used for all subsequent remote calls by this class to the
     * {@link ActivationMonitor}. The {@link ActivationSystem} proxy
     * (obtained from the <code>ActivationGroupID</code>) is passed to the
     * {@link ProxyPreparer} given by the <code>systemPreparer</code>
     * configuration entry, if one exists; a new
     * <code>ActivationGroupID</code> is constructed with the resulting proxy.
     * An {@link Exporter} instance is obtained from the
     * <code>instantiatorExporter</code> configuration entry, if one exists;
     * this exporter will be used (in the constructor of this class) to export
     * the group. A <code>ProxyPreparer</code> instance is obtained from the
     * <code>monitorPreparer</code> configuration entry, if one exists; this
     * preparer will be used (in the constructor of this class) to prepare the
     * <code>ActivationMonitor</code>. A call is then made to
     * {@link ActivationGroup#createGroup ActivationGroup.createGroup} with
     * the new group identifier, the activation group descriptor, and the
     * group incarnation number, and the result of that call is returned.
     *
     * @param id the activation group identifier
     * @param desc the activation group descriptor
     * @param incarnation the group's incarnation number (zero on initial
     * creation)
     * @return the created activation group
     * @throws ActivationException if a group already exists or if an
     * exception occurs during group creation
     */
    public static synchronized
	java.rmi.activation.ActivationGroup createGroup(
					      final ActivationGroupID id,
					      final ActivationGroupDesc desc,
					      final long incarnation)
        throws ActivationException
    {
        return AbstractActivationGroup.createGroup(id, desc, incarnation);
    }
      
    /**
     * Creates an instance with the specified group identifier and
     * initialization data. This constructor must be called indirectly,
     * via {@link #createGroup createGroup}. By default, this instance
     * automatically exports itself as a {@link UnicastRemoteObject}. (This
     * is a limitation of the existing activation system design.) If an
     * {@link Exporter} was obtained by {@link #createGroup createGroup},
     * then this instance is unexported from the JRMP runtime and re-exported
     * using that exporter. (Any incoming remote calls received on the
     * original JRMP export before this instance can be unexported will be
     * refused with a security exception thrown.) The
     * {@link ActivationSystem#activeGroup activeGroup} method of the
     * activation system proxy (in the group identifier) is called to
     * make the group active. The returned {@link ActivationMonitor} proxy
     * is passed to the corresponding {@link ProxyPreparer} obtained by
     * <code>createGroup</code>. Note that after this constructor returns,
     * {@link ActivationGroup#createGroup ActivationGroup.createGroup} will
     * also call <code>activeGroup</code> (so the activation system must
     * accept idempotent calls to that method), but the
     * <code>ActivationMonitor</code> proxy returned by that call will not be
     * used.
     * 
     * @param id the activation group identifier
     * @param data group initialization data (ignored)
     * @throws RemoteException if the group could not be exported or
     * made active, or proxy preparation fails
     * @throws ActivationException if the constructor was not called
     * indirectly from <code>createGroup</code>
     */
    public ActivationGroupImpl(ActivationGroupID id, MarshalledObject data)
	throws ActivationException, RemoteException
    {
	super(id, data);
        export();
    }
    
}
