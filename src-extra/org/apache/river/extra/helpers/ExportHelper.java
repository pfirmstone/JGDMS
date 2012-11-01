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
import java.rmi.Remote;
import java.rmi.server.ExportException;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.constraint.BasicMethodConstraints;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.MethodConstraints;
import net.jini.export.Exporter;
import net.jini.id.Uuid;
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.InvocationLayerFactory;
import net.jini.jeri.ServerEndpoint;
import org.apache.river.api.common.Beta;

/**
 *
 */
@Beta
public class ExportHelper
    extends ConfigHelper
{
    private static final Logger logger = Logger.getLogger(ExportHelper.class.getName());

    public ExportHelper(URL resource) throws ConfigurationException
    {
        super(resource);
    }

    public ExportHelper(Configuration configuration)
    {
        super(configuration);
    }

    public <T extends Remote> T export(T obj, Class<T> cls)
        throws ExportException, ConfigurationException
    {
        return export(obj,cls,null);
    }

    public <T extends Remote> T export( T obj, Class<T> cls, String component )
        throws ExportException, ConfigurationException
    {
        if( component == null ) {
            component = getComponent(cls);
        }

        Exporter exp = getExporter(component);

        final Remote exportedObj = exp.export(obj);

        if (logger.isLoggable(Level.FINE)) {
            logger.fine( "proxy=" + exportedObj );
        }

        return (T) exportedObj;
    }

    public Exporter getExporter( String component )
        throws ConfigurationException
    {
        Exporter exp = (Exporter) configuration.getEntry(component, EXPORTER, Exporter.class, null);

        if( exp == null ) {
            ServerEndpoint se = getServerEndpoint(component);
            InvocationLayerFactory ilf = getILFactory(component);
            boolean keepAlive = getKeepAlive(component);
            boolean dgc = getDgc(component);
            Uuid uuid = getUuid(component);

            exp = new BasicJeriExporter(se, ilf, dgc, keepAlive, uuid);
        }

        return exp;
    }

    private InvocationLayerFactory getILFactory( String component )
        throws ConfigurationException
    {
        InvocationLayerFactory ilf = (InvocationLayerFactory) configuration.getEntry(component, ILF, InvocationLayerFactory.class, null);

        if( ilf == null ) {
            //boolean verify = getVerify(component);

            InvocationConstraints ic = getInvocationConstraints(component);
            if( ic == null ) {
                ic = InvocationConstraints.EMPTY ;
            }
            MethodConstraints serverConstraints = new BasicMethodConstraints(ic);
            ilf = new BasicILFactory(serverConstraints,null);
            //ilf = new ProxyTrustILFactory(serverConstraints,null);
        }
        
        return ilf ;
    }

    private ServerEndpoint getServerEndpoint( String component )
        throws ConfigurationException
    {
        // entry must exist.
        ServerEndpoint se = (ServerEndpoint) configuration.getEntry(component, SERVERENDPOINT, ServerEndpoint.class, Configuration.NO_DEFAULT);

        // check if entry is not null.
        if( se == null ) {
            throw new NullPointerException("serverEndpoint is null in " + component );
        }

        return se ;
    }

    private boolean getKeepAlive(String component) throws ConfigurationException
    {
        Boolean b = (Boolean) configuration.getEntry(component, KEEPALIVE, boolean.class, false);
        return b.booleanValue();
    }


}
