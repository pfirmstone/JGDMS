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
import java.util.logging.Logger;

/**
 */
public class LocalHostLookup
{
    private final static Logger logger = Logger.getLogger(LocalHostLookup.class.getName());

    private static InetAddress localHost ;

    public static InetAddress getLocalHost() throws UnknownHostException
    {
        if( localHost == null ) {
            setLocalHost( InetAddress.getLocalHost() );
        }
        return localHost ;
    }

    private static void setLocalHost(InetAddress localHost)
    {
        if( localHost.isLoopbackAddress() ) {
            logger.warning( "local host is loopback" );
        }
        LocalHostLookup.localHost = localHost ;
    }

    private LocalHostLookup()
    {
    }
}
