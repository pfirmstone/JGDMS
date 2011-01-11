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

package org.apache.river.configbuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.config.ConfigurationFile;

/**
 *
 * @author sim
 */
//TODO: abstract?
public class ConfigurationBuilder
{

    public ConfigurationBuilder()
    {
    }

    public String getConfigurationText() throws IOException
    {
        //TODO: create real implementation.

        InputStream is = getClass().getResourceAsStream("example.config");
        StringBuilder sb = new StringBuilder();
        while(true) {
            int c = is.read();
            if( c < 0 ) {
                break ;
            }
            sb.append((char)c);
        }

        return sb.toString();
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
            return super.getSpecialEntry(name);
        }

        @Override
        protected Class getSpecialEntryType(String name) throws ConfigurationException
        {
            if( "$configuration".equals(name) ) {
                return this.getClass();
            }
            return super.getSpecialEntryType(name);
        }

    }

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
