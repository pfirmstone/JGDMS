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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Properties;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.config.ConfigurationFile;
import org.apache.river.config.ConfigurationFactory;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;

/**
 */
public class VelocityConfigurationBuilder
    implements ConfigurationFactory
{
    private String serviceHost = null ;

    private int servicePort = 0 ;

    private String registryHost = null ;

    private int registryPort = 0 ;

    private String group = "org.apache.river.demo" ;

    private String codebase = "" ;

    private boolean disableMulticast = false ;

    public VelocityConfigurationBuilder()
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
        Properties p = new Properties();
        p.load( getClass().getResourceAsStream("velocity.properties") );

        VelocityEngine ve = new VelocityEngine(p);

        VelocityContext context = new VelocityContext();

        if( disableMulticast ) {
            final String mcstr = "multicastInterfaces = new java.net.NetworkInterface[] { } ;" ;
            context.put("multicastInterfaces", mcstr );
        }

        context.put("registryEndpoint", String.format("TcpServerEndpoint.getInstance(%s,%d)",stringToLiteral(registryHost),registryPort) );
        context.put("serverEndpoint", String.format("TcpServerEndpoint.getInstance(%s,%d)",stringToLiteral(serviceHost),servicePort) );
        context.put("groups", String.format("\"%s\"",group) );

        Template template = ve.getTemplate( getClass().getResource("template.vm").toExternalForm() );

        StringWriter sw = new StringWriter();

        template.merge(context, sw);

        return sw.toString();
    }

    private String stringToLiteral( String s )
    {
        if( s == null ) {
            return "null" ;
        }
        return String.format("\"%s\"",s);
    }

    public Configuration createConfiguration() throws ConfigurationException
    {
        try {
            StringReader sr = new StringReader( getConfigurationText() );

            final ConfigurationFile cf = new ConfigurationFile(sr,null);

            return cf ;
        } catch( ConfigurationException c ) {
            throw c ;
        } catch( Exception e ) {
            throw new ConfigurationException("",e);
        }
    }

    public void print( File file ) throws IOException
    {
        FileWriter fw = new FileWriter(file);
        print(fw);
        fw.close();
    }

    public void print( Writer wr ) throws IOException
    {
        wr.append( getConfigurationText() );
    }
}
