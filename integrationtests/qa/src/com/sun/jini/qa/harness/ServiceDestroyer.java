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
package com.sun.jini.qa.harness;

import com.sun.jini.admin.DestroyAdmin;
import com.sun.jini.start.SharedActivatableServiceDescriptor.Created;

import java.rmi.activation.ActivationException;
import java.rmi.activation.ActivationSystem;
import java.rmi.activation.UnknownObjectException;
import java.rmi.activation.ActivationGroup;
import java.rmi.RemoteException;

import net.jini.admin.Administrable;
import net.jini.config.Configuration;

/** 
 * This class provides static methods that can be used to destroy a service.
 * This implementation was taken from the <code>com.sun.jini.start</code>
 * package when it became obsolete there.
 */
class ServiceDestroyer {

    static final int DESTROY_SUCCESS = 0;
    /* Failure return codes */
    static final int SERVICE_NOT_ADMINISTRABLE = -1;
    static final int SERVICE_NOT_DESTROY_ADMIN = -2;
    static final int DEACTIVATION_TIMEOUT      = -3;
    static final int PERSISTENT_STORE_EXISTS   = -4;

    static final int N_MS_PER_SEC = 1000;
    static final int DEFAULT_N_SECS_WAIT = 600;

    /**
     * Administratively destroys the service referenced by the input
     * parameter. The service input to this method must implement
     * both <code>net.jini.admin.Administrable</code> and the
     * <code>com.sun.jini.admin.DestroyAdmin</code> interfaces
     * in order for this method to successfully destroy the service.
     *
     * @param service reference to the service to destroy
     * @return <code>true</code> if the service's destroy method was invoked
     *         successfully; <code>false</code> otherwise.
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         service's backend. When this exception does occur, the
     *         service may or may not have been successfully destroyed.
     */
    static int destroy(Object service) throws RemoteException {
        /* First, test that the service implements both of the appropriate
         * administration interfaces
         */
        DestroyAdmin destroyAdmin = null;
        if( !(service instanceof Administrable) ) {
            return SERVICE_NOT_ADMINISTRABLE;
        }
        Object admin = ((Administrable)service).getAdmin();
        if( !(admin instanceof DestroyAdmin) ) {
            return SERVICE_NOT_DESTROY_ADMIN;
        }
        destroyAdmin = (DestroyAdmin)admin;
        destroyAdmin.destroy();
        return DESTROY_SUCCESS;
    }

    /**
     * Administratively destroys the service referenced by the
     * <code>proxy</code> parameter, which is assumed to be running 
     * under a shared VM environment. This method attempts to verify 
     * that the desired service is indeed destroyed by verifying that
     * the service's activation information/descriptor is no longer 
     * registered with the activation system.
     *
     * @param created   the <code>Created</code> object returned when the
     *                  service was started
     * @param nSecsWait the number of seconds to wait for the service's 
     *                  activation descriptor to be no longer registered with 
     *                  the activation system
     * @param config    a <code>Configuration</code> object which is unused
     *                  and probably present only for historical reasons
     *
     * @return <code>int</code> value that indicates either success or 
     *         one of a number of possible reasons for failure to destroy
     *         the service. Possible values are:
     * <p><ul>
     *   <li> ServiceDestroyer.DESTROY_SUCCESS
     *   <li> ServiceDestroyer.SERVICE_NOT_ADMINISTRABLE - returned when
     *        the service to destroy is not an instance of 
     *        net.jini.admin.Administrable
     *   <li> ServiceDestroyer.SERVICE_NOT_DESTROY_ADMIN - returned when
     *        the service to destroy is not an instance of 
     *        com.sun.jini.admin.DestroyAdmin
     *   <li> ServiceDestroyer.DEACTIVATION_TIMEOUT - returned when the
     *        service's activation descriptor is still registered with the
     *        activation system after the number of seconds to wait have passed
     *   <li> ServiceDestroyer.PERSISTENT_STORE_EXISTS - returned when the
     *        directory in which the service stores its persistent state
     *        still exists after the service has been successfully destroyed
     * </ul>
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         service's backend. When this exception does occur, the
     *         service may or may not have been successfully destroyed.
     * @throws java.rmi.activation.ActivationException typically, this
     *         exception occurs when problems arise while attempting to
     *         interact with the activation system
     */
    static int destroy(Created created, int nSecsWait, Configuration config)  
	throws RemoteException, ActivationException
    {
	Object proxy = created.proxy;
        int destroyCode = destroy(proxy);
        if(destroyCode != DESTROY_SUCCESS) return destroyCode;
        /* Verify the service has actually been destroyed by waiting until
         * service's activation ID is no longer registered with the
         * activation system.
         *
         * Since an exception will be thrown when an attempt is made to
         * retrieve an activation descriptor for an ID which is not
         * registered, this method makes repeated attempts to retrieve the
         * activation descriptor until such an exception is thrown,
         * or until the indicated number of seconds to wait has passed.
         */
        boolean deactivated = false;
        for(int i = 0; i < nSecsWait; i++) {
            try {
                ActivationGroup.getSystem().getActivationDesc(created.aid);
            } catch (UnknownObjectException e) {
                deactivated = true;
                break;
            }
            try {
                Thread.sleep(1*N_MS_PER_SEC);
            } catch (InterruptedException e) { }
        }
        if(!deactivated) return DEACTIVATION_TIMEOUT;
        return DESTROY_SUCCESS;
    }
}

