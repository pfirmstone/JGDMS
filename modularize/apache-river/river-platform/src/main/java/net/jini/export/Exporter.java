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

import java.rmi.Remote;
import java.rmi.server.ExportException;

/**
 * An abstraction for exporting a single remote
 * object such that it can receive remote method invocations, and
 * later for unexporting that same remote object.
 *
 * <p>Details of the export and unexport behavior, including the
 * communication protocols used for remote invocation and additional
 * remote invocation semantics, are defined by the particular
 * implementation of this interface.
 *
 * @author	Sun Microsystems, Inc.
 * @since 2.0
 **/
public interface Exporter {

    /**
     * Exports the specified remote object and returns a proxy that can
     * be used to invoke remote methods on the exported remote object.
     * This method must only be invoked once on a given
     * <code>Exporter</code> instance.
     *
     * <p>The returned proxy implements an implementation-specific
     * set of remote interfaces of the remote object and may also implement
     * additional implementation-specific interfaces.
     *
     * <p>A remote interface is an interface that extends the interface
     * <code>java.rmi.Remote</code> and whose methods each declare at least
     * one exception whose type is <code>java.rmi.RemoteException</code>
     * or one of its superclasses.
     * 
     * <p>This method should not be called from within an object constructor,
     * otherwise the object reference will escape during construction, leaving
     * the remote object implementation in an undefined state.
     * 
     * @see org.apache.river.api.util.Startable
     *
     * @param	impl a remote object to export
     * @return	a proxy for the remote object
     *
     * @throws	ExportException if a problem occurs exporting the object
     * @throws	SecurityException if a <code>SecurityException</code>
     *		occurs exporting the object
     * @throws	NullPointerException if <code>impl</code> is <code>null</code>
     * @throws	IllegalStateException if an object has already been exported
     *		with this <code>Exporter</code> instance
     **/
    public Remote export(Remote impl) throws ExportException;

    /**
     * Unexports the remote object that was exported by this
     * <code>Exporter</code> such that it will no longer receive remote
     * method invocations that were made possible as a result of exporting
     * it with this <code>Exporter</code>.  The unexport operation may
     * not occur if the <code>force</code> argument is <code>false</code>.
     * This method must only be invoked after the <code>export</code>
     * method has been invoked on this <code>Exporter</code> instance to
     * export a remote object successfully.
     *
     * <p>This method returns <code>true</code> if upon return, the remote
     * object is no longer exported with this <code>Exporter</code>, and
     * <code>false</code> if the remote object remains exported with this
     * <code>Exporter</code>.  This method will always return
     * <code>true</code> if it has returned <code>true</code> previously.
     *
     * <p>The <code>force</code> parameter serves to indicate whether or not
     * the caller desires the unexport to occur even if there are known
     * remote calls pending or in progress to the remote object that were
     * made possible by this <code>Exporter</code>:
     *
     * <ul>
     * <li>If <code>force</code> is <code>true</code>, then the remote
     * object will be forcibly unexported even if there are remote calls
     * pending or in progress, and this method will return
     * <code>true</code>.
     * 
     * <li>If <code>force</code> is <code>false</code>, then this acts as
     * a hint to the implementation that the remote object should not be
     * unexported if there are known remote calls pending or in progress,
     * and this method will either unexport the remote object and return
     * <code>true</code> or not unexport the remote object and return
     * <code>false</code>.  If the implementation detects that there are
     * indeed remote calls pending or in progress, then it should return
     * <code>false</code>; otherwise, it must return <code>true</code>.
     * If the implementation does not support being able to unexport
     * conditionally based on knowledge of remote calls pending or in
     * progress, then it must implement this method as if
     * <code>force</code> were always <code>true</code>.
     * </ul>
     *
     * <p>If the remote object is unexported as a result of this method,
     * then the implementation may (and should, if possible) prevent remote
     * calls in progress from being able to communicate their results
     * successfully.
     *
     * @param	force if <code>true</code>, the remote object will be
     * unexported even if there are remote calls pending or in progress;
     * if <code>false</code>, the remote object may only be unexported if
     * there are no known remote calls pending or in progress
     *
     * @return	<code>true</code> if the remote object is unexported when
     * this method returns and <code>false</code> otherwise
     *
     * @throws	IllegalStateException if an object has not been exported
     * with this <code>Exporter</code> instance
     **/
    public boolean unexport(boolean force);
}

