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
package org.apache.river.extra.selfhealing;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.rmi.RemoteException;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.jini.admin.Administrable;
import net.jini.core.lookup.ServiceTemplate;

import org.apache.river.extra.discovery.ServiceFinder;

/**
 * Simple implementation of a self-healing proxy.  Instead of standard River
 * service discovery, this <code>ServiceWrapper</code> is used alongside the
 * <code>SelfHealingServiceFactory</code>.  Then, when a method is called on a
 * (wrapped) service, should that service call fail the wrapper will
 * automatically to find a replacement service and re-make the same method call
 * to the newly discovered service.
 * 
 * This leaves the remainder of the application to only have to deal with the
 * occurance of when no service can be found in the djinn at all, rather than
 * having to deal with generic service failures, relookups and retries.
 * 
 * This suite of classes is intended to be a reference implementation and
 * alternative strategies for service lookup and re-invokation can be easily
 * implemented.
 * 
 * @see SelfHealingServiceFactory
 */
public class ServiceWrapper implements InvocationHandler, Administrable {

    private static final Logger logger = Logger.getLogger(ServiceWrapper.class.getSimpleName());

    private ServiceFinder finder;
    private ServiceTemplate template;
    private Object proxy;

    /**
     * Ideally, this cntr would be invoked from the <code>SelfHealingServiceFactory</code>
     * but there is no reason why this should be enforced.
     * 
     * It simply creates the <code>ServiceWrapper</code> with a lazy-located
     * service proxy based on the input template.
     * 
     * @param finder - The specific method for locating services in the djinn
     * @param template - The template used for finding replacement services
     */
    ServiceWrapper(final ServiceFinder finder, final ServiceTemplate template) {
        setServiceFinder(finder);
        setServiceTemplate(template);
    }

    /**
     * This method attempts the invoke the specified method call on the service.
     * If that method call fails then an attempt it made to find a replacement
     * service and the method call is re-executed against the new service.
     * Assuming one is found.
     * 
     * If the second method invokation fails then the exception is thrown back
     * to the caller.
     * 
     * Inherited from <code>InvocationHandler</code>, this forms the basis of
     * our reflection based wrapper.  Usually the input object is the one to
     * have the method invoked on it, since we are looking up and using our
     * own services proxies, this argument is ignored.
     * 
     */
    public Object invoke(final Object ignored, final Method method, final Object[] args) throws Throwable {
        initServiceProxy();

        Object response = null;
        boolean serviceCallFailed = false;

        try {
            response = execute(method, args);
        } catch (RemoteException re) {
            logger.log(Level.WARNING, "Service invocation failed: "+re.getMessage(), re);
            serviceCallFailed = true;
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Service invocation failed: "+t.getMessage(), t);
            serviceCallFailed = true;
        }

        //attempt to find a replacement service and reissue the call
        if(serviceCallFailed) {
            logger.info("Service call failed.  Looking for replacement service");
            initServiceProxy();
            response = execute(method, args);
        }

        return response;
    }

    /**
     * Convenience method to return the <code>Administrable</code> object from
     * the service proxy.  It assumes that the service proxy implements this
     * interface, and if it does not the <code>ClassCastException</code> is
     * thrown to the caller.
     * 
     * NOTE: Not personally convinced that this is necessary...
     */
    public Object getAdmin() throws RemoteException {
        if(null == this.proxy) {
            throw new RemoteException("No service proxy");
        }
        return ((Administrable)this.proxy).getAdmin();
    }

    private Object execute(final Method method, final Object[] args) throws RemoteException {
        try {
            logger.finest("Invoking method ["+method+"] on "+this.proxy);
            return method.invoke(this.proxy, args);
        } catch (IllegalAccessException iae) {
            throw new RemoteException("Cannot execute method because "+iae.getMessage(), iae);
        } catch (IllegalArgumentException iae) {
            throw new RemoteException("Cannot execute method because "+iae.getMessage(), iae);
        } catch (InvocationTargetException ite) {
            throw new RemoteException("Cannot execute method because "+ite.getMessage(), ite);
        }
    }

    private void initServiceProxy() throws RemoteException {
        if(null == this.proxy) {
            logger.finer("Looking for a service proxy");
            this.proxy = this.finder.findNewService(this.template);
        }
    }

    private void setServiceFinder(final ServiceFinder finder) {
        if(null == finder) {
            throw new IllegalArgumentException("ServiceFinder cannot be null");
        }
        this.finder = finder;
    }

    private void setServiceTemplate(final ServiceTemplate template) {
        if(null == template) {
            throw new IllegalArgumentException("ServiceTemplate cannot be null");
        }
        this.template = template;
    }

}