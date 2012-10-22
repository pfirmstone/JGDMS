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

package org.apache.river.config;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.river.common.Beta;

/**
 * Provides river with the external host network identity.
 */
@Beta
public class LocalHostLookup
{
    private final static Logger logger = Logger.getLogger(LocalHostLookup.class.getName());
    
    private static LocalHostLookupProvider provider;

    public static InetAddress getLocalHost() throws UnknownHostException
    {
        return getProvider().getLocalHost();
    }

    public static String getHostName() throws UnknownHostException
    {
        return getProvider().getHostName();
    }

    public static String getHostAddress() throws UnknownHostException
    {
        return getProvider().getHostAddress();
    }

    private static synchronized LocalHostLookupProvider getProvider()
    {
        if( provider == null ) {
            setProvider( new DefaultLocalHostLookupProvider() );
        }
        return provider ;
    }

    public static synchronized void setProvider(LocalHostLookupProvider prvdr )
    {
        if( LocalHostLookup.provider != null ) {
            throw new RuntimeException( "provider already set" );
        }
        
        LocalHostLookup.provider = prvdr ;

        try {
            if (getLocalHost().isLoopbackAddress()) {
                logger.warning("local host is loopback");
            }
        } catch (UnknownHostException ex) {
            logger.log(Level.WARNING,"",ex);
        }
    }

    static class DefaultLocalHostLookupProvider 
        implements LocalHostLookupProvider
    {

        @Override
        public InetAddress getLocalHost() throws UnknownHostException
        {
            return InetAddress.getLocalHost();
        }

        @Override
        public String getHostName() throws UnknownHostException
        {
            return InetAddress.getLocalHost().getCanonicalHostName();
        }

        @Override
        public String getHostAddress() throws UnknownHostException
        {
            return InetAddress.getLocalHost().getHostAddress();
        }
        
    }
    
    private LocalHostLookup()
    {
    }
}
