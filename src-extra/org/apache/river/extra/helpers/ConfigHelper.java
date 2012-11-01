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

import com.sun.jini.config.Component;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.rmi.Remote;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.config.ConfigurationProvider;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.id.Uuid;
import org.apache.river.api.common.Beta;

/**
 *
 */
@Beta
public abstract class ConfigHelper
{
    public static final String DGC = "dgc";
    public static final String IC = "ic";
    public static final String UUID = "uuid";
    public static final String VERIFY = "verify";
    public static final String EXPORTER = "exporter";
    public static final String ILF = "ilf";
    public static final String KEEPALIVE = "keepAlive";
    public static final String SERVERENDPOINT = "serverEndpoint";
    public static final String PREPARER = "preparer";

    protected Configuration configuration;

    public ConfigHelper(Configuration configuration)
    {
        this.configuration = configuration;
    }

    public ConfigHelper(URL resource) throws ConfigurationException
    {
        String[] configArgs = new String[] { resource.toExternalForm() };
        this.configuration = ConfigurationProvider.getInstance(configArgs);
    }

    protected ProxyHelper getProxyHelper(String component)
    {
        //TODO: allow custom proxyhelper per component.

        return new ProxyHelper(configuration);
    }

    private Component findComponentAnnotation( Class<?> cls )
    {
        if( !Remote.class.isAssignableFrom(cls) ) {
            return null ;
        }

        Component jc;

        if( Proxy.isProxyClass(cls) ) {
            Class[] ifs = cls.getInterfaces();
            for( Class<?> c : ifs ) {
                jc = findComponentAnnotation(c);
                if( jc != null ) {
                    return jc ;
                }
            }
            return null ;
        }

        jc = cls.getAnnotation(Component.class);
        if( jc != null ) {
            return jc ;
        }

        Class[] ifs = cls.getInterfaces();
        for( Class<?> c : ifs ) {
            jc = findComponentAnnotation(c);
            if( jc != null ) {
                return jc ;
            }
        }
        return null ;
    }

    protected String getComponent( Class<?> cls )
    {
        Component jc = findComponentAnnotation(cls);

        if (jc != null) {
            String comp = jc.value();
            if( comp == null ) {
                comp = cls.getName();
            }
            return comp ;
        } else {
            return cls.getName();
        }
    }

    protected InvocationConstraints getInvocationConstraints(String component) throws ConfigurationException
    {
        InvocationConstraints ic = (InvocationConstraints) configuration.getEntry(component, IC, InvocationConstraints.class, null);
        return ic ;
    }

    protected Uuid getUuid(String component) throws ConfigurationException
    {
        Uuid uuid = (Uuid) configuration.getEntry(component, UUID, Uuid.class, null);
        // a null uuid means the BasicJeriExporter generates one.
        return uuid;
    }

    protected boolean getDgc(String component) throws ConfigurationException
    {
        Boolean b = (Boolean) configuration.getEntry(component, DGC, boolean.class, false);
        return b.booleanValue();
    }

    /**
     * Should verify if proxies are trusted?
     */
    protected boolean getVerify(String component) throws ConfigurationException
    {
        return (Boolean)configuration.getEntry(component, VERIFY, boolean.class, false);
    }

}
