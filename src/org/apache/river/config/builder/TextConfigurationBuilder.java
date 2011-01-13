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

package org.apache.river.config.builder;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.HashMap;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.config.ConfigurationFile;
import net.jini.jeri.ServerEndpoint;
import net.jini.jeri.tcp.TcpServerEndpoint;
import org.apache.river.config.ConfigurationFactory;

/**
 * @author sim
 */
public class TextConfigurationBuilder
    implements ConfigurationFactory
{
    private HashMap<String,Object> specialEntryMap = new HashMap<String,Object>();

    private String serviceHost = null ;

    private int servicePort = 0 ;

    private String registryHost = null ;

    private int registryPort = 0 ;

    private String group = "org.apache.river.demo" ;

    private String codebase = "" ;

    private boolean disableMulticast = false ;

    public TextConfigurationBuilder()
    {
    }

    public int getServicePort()
    {
        return servicePort;
    }

    public void setServicePort(int servicePort)
    {
        this.servicePort = servicePort;
    }

    public String getServiceHost()
    {
        return serviceHost;
    }

    public void setServiceHost(String serviceHost)
    {
        this.serviceHost = serviceHost;
    }

    public String getRegistryHost()
    {
        return registryHost;
    }

    public void setRegistryHost(String registryHost)
    {
        this.registryHost = registryHost;
    }

    public int getRegistryPort()
    {
        return registryPort;
    }

    public void setRegistryPort(int registryPort)
    {
        this.registryPort = registryPort;
    }

    public String getGroup()
    {
        return group;
    }

    public void setGroup(String group)
    {
        this.group = group;
    }

    public String getCodebase()
    {
        return codebase;
    }

    public void setCodebase(String codebase)
    {
        this.codebase = codebase;
    }

    public boolean isDisableMulticast()
    {
        return disableMulticast;
    }

    public void setDisableMulticast(boolean disableMulticast)
    {
        this.disableMulticast = disableMulticast;
    }

    public String getConfigurationText() throws IOException
    {
        {
            ServerEndpoint ep = TcpServerEndpoint.getInstance(serviceHost,servicePort);
            specialEntryMap.put("$serviceEndpoint", ep);
        }

        {
            ServerEndpoint ep = TcpServerEndpoint.getInstance(registryHost,registryPort);
            specialEntryMap.put("$registryEndpoint", ep);
        }

        specialEntryMap.put("$group", group);
        specialEntryMap.put("$codebase", codebase);

        InputStream is = getClass().getResourceAsStream("template.config");
        StringBuilder sb = new StringBuilder();
        while(true) {
            int c = is.read();
            if( c < 0 ) {
                break ;
            }
            sb.append((char)c);
        }
        is.close();

        String buf = sb.toString();

        {
            String mcstr = "" ;
            if( disableMulticast ) {
                mcstr = "multicastInterfaces = new java.net.NetworkInterface[] { } ;" ;
            }

            buf = buf.replaceAll("%REGGIE.multicastInterfaces%", mcstr );
        }

        return buf ;
    }

    public class ConfigurationFile2
        extends ConfigurationFile
    {

        public ConfigurationFile2(Reader reader, String[] options, ClassLoader cl) throws ConfigurationException
        {
            super(reader, options, cl);
        }

        public ConfigurationFile2(Reader reader, String[] options) throws ConfigurationException
        {
            super(reader, options);
        }

        @Override
        protected Object getSpecialEntry(String name) throws ConfigurationException
        {
            if( "$configuration".equals(name) ) {
                return this ;
            }
            if( specialEntryMap.containsKey(name) ) {
                return specialEntryMap.get(name);
            }
            return super.getSpecialEntry(name);
        }

        @Override
        protected Class getSpecialEntryType(String name) throws ConfigurationException
        {
            if( "$configuration".equals(name) ) {
                return this.getClass();
            }
            if( specialEntryMap.containsKey(name) ) {
                Object obj = specialEntryMap.get(name);
                if( obj == null ) {
                    return null ;
                }
                return obj.getClass();
            }
            return super.getSpecialEntryType(name);
        }

    }

    @Override
    public Configuration createConfiguration() throws ConfigurationException
    {
        try {
            StringReader sr = new StringReader( getConfigurationText() );

            final ConfigurationFile cf = new ConfigurationFile2(sr,null);

            return cf ;
        } catch( ConfigurationException c ) {
            throw c ;
        } catch( Exception e ) {
            throw new ConfigurationException("",e);
        }
    }
}
