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
package org.apache.river.extra.discovery;

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.jini.core.discovery.LookupLocator;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.core.lookup.ServiceTemplate;

/**
 * Implementation which accepts an array of <code>LookupLocator</code>s and
 * uses them to find replacement services.  This allows service discovery
 * across subnets.
 */
public class UnicastServiceFinder implements ServiceFinder {

    private static final Logger logger = Logger.getLogger(UnicastServiceFinder.class.getSimpleName());

    private LookupLocator[] lookupLocators;

    /**
     * @param lookupLocators
     * @throws IllegalArgumentException if the argumet s null or is a zero length array
     */
    public UnicastServiceFinder(final LookupLocator[] lookupLocators) {
        setLookupLocators(lookupLocators);
    }

    public Object findNewService(final ServiceTemplate template) throws RemoteException {
        Object proxy = null;

        for(int i=0 ; i<this.lookupLocators.length && null == proxy ; i++) {
            try {
                ServiceRegistrar ssr = this.lookupLocators[i].getRegistrar();
                proxy = ssr.lookup(template);
            } catch (IOException ioe) {
                logger.log(Level.WARNING, "Unable to lookup service on jini://"+this.lookupLocators[i].getHost()+":"+this.lookupLocators[i].getPort(), ioe);
            } catch (ClassNotFoundException cce) {
                logger.log(Level.WARNING, "Unable to lookup service on jini://"+this.lookupLocators[i].getHost()+":"+this.lookupLocators[i].getPort(), cce);
            }
        }

        if(null == proxy) {
            throw new RemoteException("Cannot find valid service");
        }

        return proxy;
    }

    public void terminate() {
    }

    private void setLookupLocators(final LookupLocator[] lookupLocators) {
        if(null == lookupLocators) {
            throw new IllegalArgumentException("LookupLocator array cannot be null");
        }
        if(0 == lookupLocators.length) {
            throw new IllegalArgumentException("LookupLocator array must have length > 0");
        }
        this.lookupLocators = lookupLocators;
    }
}
