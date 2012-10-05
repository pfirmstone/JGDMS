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

package org.apache.river.extra.helpers;

import java.net.URL;
import java.rmi.RemoteException;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.constraint.BasicMethodConstraints;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.MethodConstraints;
import net.jini.security.BasicProxyPreparer;
import net.jini.security.ProxyPreparer;

/**
 *
 */
public class ProxyHelper
    extends ConfigHelper
{
    private static Logger logger = Logger.getLogger( ProxyHelper.class.getName()  );

    public ProxyHelper(URL resource) throws ConfigurationException
    {
        super(resource);
    }

    public ProxyHelper(Configuration configuration)
    {
        super(configuration);
    }


    public <T> T prepare( T proxy, String component ) throws RemoteException, ConfigurationException
    {
        if( component == null ) {
            component = getComponent(proxy.getClass());
        }

        if( logger.isLoggable(Level.FINE) ) {
            logger.fine( component );
        }

        ProxyPreparer preparer = getPreparer(component);

        proxy = (T) preparer.prepareProxy(proxy);

        return proxy ;
    }

    private ProxyPreparer getPreparer(String component) throws ConfigurationException
    {
        ProxyPreparer preparer = (ProxyPreparer) configuration.getEntry(component, PREPARER, ProxyPreparer.class, null);

        if( preparer == null ) {
            boolean verify = getVerify(component);

            InvocationConstraints ic = getInvocationConstraints(component);

            if( ic == null ) {
                preparer = new BasicProxyPreparer(verify,null);
            } else {
                MethodConstraints mc = new BasicMethodConstraints(ic);
                preparer = new BasicProxyPreparer(verify, mc, null);
            }
        }
        
        return preparer;
    }

}
