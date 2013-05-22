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

package com.sun.jini.start;

import java.rmi.MarshalledObject;
import java.rmi.Remote;
import java.rmi.activation.ActivationID;
import java.rmi.activation.ActivationSystem;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import net.jini.activation.ActivationExporter;
import net.jini.export.Exporter;
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.tcp.TcpServerEndpoint;
import net.jini.security.BasicProxyPreparer;
import net.jini.security.ProxyPreparer;

/**
 * The provided implementation
 * of the {@link SharedGroup} service.
 *
 * The following items are discussed below:
 * <ul>
 * <li><a href="#configEntries">Configuring SharedGroupImpl</a>
 * <li><a href="#logging">Logging</a>
 * </ul>
 *
 * <a name="configEntries">
 * <h3>Configuring SharedGroupImpl</h3>
 * </a>
 *
 * This implementation of <code>SharedGroupImpl</code> supports the
 * following configuration entries, with component
 * <code>com.sun.jini.start</code>:
 *
 *   <table summary="Describes the activationIdPreparer configuration entry"
 *          border="0" cellpadding="2">
 *     <tr valign="top">
 *       <th scope="col" summary="layout"> <font size="+1">&#X2022;</font>
 *       <th scope="col" align="left" colspan="2"> <font size="+1"><code>
 *       activationIdPreparer</code></font>
 *     <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *       Type: <td> {@link net.jini.security.ProxyPreparer}
 *     <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *       Default: <td> <code>
 *         new {@link net.jini.security.BasicProxyPreparer}()
 *         </code>
 *     <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *       Description: <td> The proxy preparer for the service's activation
 *       ID. The value should not be <code>null</code>. 
 * 
 *       The service starter calls the  
 *       {@link java.rmi.activation.ActivationID#activate
 *       activate} method to activate the service.
 *   </table>
 *
 *   <table summary="Describes the activationSystemPreparer configuration
 *          entry"
 *          border="0" cellpadding="2">
 *     <tr valign="top">
 *       <th scope="col" summary="layout"> <font size="+1">&#X2022;</font>
 *       <th scope="col" align="left" colspan="2"> <font size="+1"><code>
 *       activationSystemPreparer</code></font>
 *     <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *       Type: <td> {@link net.jini.security.ProxyPreparer}
 *     <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *       Default: <td> <code>
 *         new {@link net.jini.security.BasicProxyPreparer}()</code>
 *     <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *       Description: <td> The proxy preparer for the proxy for the
 *       activation system. The value should not be <code>null</code>. This
 *       entry is obtained at service start and restart. 
 * 
 *       The service calls the {@link
 *       java.rmi.activation.ActivationSystem#unregisterGroup
 *       unregisterGroup} method on the {@link
 *       java.rmi.activation.ActivationSystem} upon service destruction.
 *   </table>
 *
 *   <table summary="Describes the exporter configuration entry"
 *          border="0" cellpadding="2">
 *     <tr valign="top">
 *       <th scope="col" summary="layout"> <font size="+1">&#X2022;</font>
 *       <th scope="col" align="left" colspan="2"> <font size="+1"><code>
 *       exporter</code></font>
 *     <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *       Type: <td> {@link net.jini.export.Exporter}
 *     <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *       Default: <td>
 * <pre>
 * new {@link net.jini.activation.ActivationExporter}(
 *     <i>activationID</i>,
 *     new {@link net.jini.jeri.BasicJeriExporter}(
 *         {@link net.jini.jeri.tcp.TcpServerEndpoint#getInstance TcpServerEndpoint.getInstance}(0),
 *         new {@link net.jini.jeri.BasicILFactory}(), false, true))
 * </pre>
 *     <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *       Description: <td> The object to use for exporting the service. The
 *       value should not be <code>null</code>. The call to 
 *       <code>getEntry</code> will supply the activation ID in
 *       the <code>data</code> argument. This entry is obtained at service
 *       start and restart.
 *   </table> <p>
 *  
 *   <table summary="Describes the loginContext configuration entry"
 *     border="0" cellpadding="2">
 *   <tr valign="top">
 *     <th scope="col" summary="layout"> <font size="+1">&#X2022;</font>
 *     <th scope="col" align="left" colspan="2"> <font size="+1"><code>
 *   loginContext</code></font>
 *   <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *     Type: <td> {@link javax.security.auth.login.LoginContext}
 *   <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *     Default: <td> <code>null</code>
 *   <tr valign="top"> <td> &nbsp <th scope="row" align="right">
 *     Description: <td> If not <code>null</code>, specifies the JAAS
 *     login context to use for performing a JAAS login and supplying the
 *     {@link javax.security.auth.Subject} to use when running the
 *     service. If <code>null</code>, no JAAS login is performed. This
 *     entry is obtained at service start and restart.
 *   </table>
 *
 *
 *<a name="logging">
 *<h3>Loggers and Logging Levels</h3>
 *</a>
 *
 *The SharedGroupImpl service implementation uses the {@link
 *java.util.logging.Logger}, named <code>com.sun.jini.sharedGroup</code>. 
 *The following table describes the
 *type of information logged as well as the levels of information logged.
 *<p>
 *
 *  <table border="1" cellpadding="5"
 *	 summary="Describes logging performed by sharedGroup at different
 *	 logging levels">
 *
 *  <caption halign="center" valign="top"><b><code>
 *	   com.sun.jini.start.sharedGroup</code></b></caption>
 *
 *  <tr> <th scope="col"> Level <th scope="col"> Description
 *
 *  <tr> <td> {@link java.util.logging.Level#FINE FINE} <td> 
 *    for low level
 *    service operation tracing
 *  <tr> <td> {@link java.util.logging.Level#FINER FINER} <td> 
 *    for lower level
 *    service operation tracing
 *  <tr> <td> {@link java.util.logging.Level#FINEST FINEST} <td> 
 *    for lowest level
 *    service operation tracing
 *
 *  </table> <p>
 * 
 * @author Sun Microsystems, Inc.
 *
 */
public class SharedGroupImpl extends AbstractSharedGroup implements Remote {
    /**
     * Activation constructor. 
     */
    SharedGroupImpl(ActivationID activationID, MarshalledObject data)
	throws Exception
    {
        super(activationID, data);
        export();
    }
}
