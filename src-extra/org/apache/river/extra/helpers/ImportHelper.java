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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.logging.Logger;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.constraint.BasicMethodConstraints;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.MethodConstraints;
import net.jini.id.Uuid;
import net.jini.jeri.BasicInvocationHandler;
import net.jini.jeri.BasicObjectEndpoint;
import net.jini.jeri.Endpoint;
import net.jini.jeri.ObjectEndpoint;
import org.apache.river.common.Beta;

/**
 *
 */
@Beta
public class ImportHelper
    extends ConfigHelper
{
    private static final Logger logger = Logger.getLogger(ImportHelper.class.getName());

    public ImportHelper(URL resource) throws ConfigurationException
    {
        super(resource);
    }

    public ImportHelper(Configuration configuration)
    {
        super(configuration);
    }

    public <T> T getInstance( Endpoint endpoint, Class<T> cls, String component )
        throws RemoteException, ConfigurationException
    {
        final Uuid uuid = getUuid(component);
        final boolean dgc = getDgc(component);

        if( uuid == null ) {
            throw new ConfigurationException( "should specify "+UUID );
        }

        ObjectEndpoint oe = new BasicObjectEndpoint(endpoint, uuid, dgc);

        InvocationConstraints ic = getInvocationConstraints(component);
        if( ic == null ) {
            ic = InvocationConstraints.EMPTY ;
        }

        MethodConstraints serverConstraints = new BasicMethodConstraints(ic);

        InvocationHandler handler = new BasicInvocationHandler( oe, serverConstraints );

        Object inst = Proxy.newProxyInstance(getClass().getClassLoader(), new Class[] { cls }, handler );
        
        ProxyHelper ph = getProxyHelper(component);
        inst = ph.prepare(inst, component);

        return (T)inst ;
    }

    public <T> T getInstance( Endpoint endpoint, Class<T> cls )
        throws RemoteException, ConfigurationException
    {
        String component = getComponent(cls);

        return getInstance(endpoint,cls,component);
    }
}
