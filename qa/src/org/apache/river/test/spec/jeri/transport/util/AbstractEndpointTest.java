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
package org.apache.river.test.spec.jeri.transport.util;

//harness imports
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.LegacyTest;

//jeri imports
import org.apache.river.qa.harness.Test;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.config.ConfigurationFile;
import net.jini.jeri.ServerEndpoint;

//java.util
import java.util.logging.Logger;
import java.util.logging.Level;

public abstract class AbstractEndpointTest implements LegacyTest {

    protected static QAConfig sysConfig;
    protected static Logger log;
    private final static String component = "org.apache.river.test.spec."
        + "jeri.serverendpoint";
    private final static String seEntry = "endpoint";

    public Test construct(QAConfig config) {
        sysConfig = config;
        log = Logger.getLogger("org.apache.river.test.spec.jeri.transport");
        return this;
    }

    public void tearDown() {
    }

    public ServerEndpoint getServerEndpoint() throws ConfigurationException{
//        Configuration config = sysConfig.getConfiguration();
	Configuration config = getConfiguration();
        ServerEndpoint se = (ServerEndpoint)
            config.getEntry(component,seEntry,ServerEndpoint.class);
        log.finest("ServerEndpoint extracted from the configuration is " + se);
        return se;
    }

    public Object getConfigObject(Class c, String name)
        throws ConfigurationException {
        Object o = getConfiguration().getEntry(component,name,c);
        log.finest("Entry " + component + "." + name + " extracted from the"
            + " configuration with value " + o);
        return o;
    }

    public Configuration getConfiguration() throws ConfigurationException {
	String name = sysConfig.getStringConfigVal("testConfiguration", "-");
	System.out.println("Loading configuration file " + name);
        return new ConfigurationFile(new String[]{name});
    }

    public static Logger getLogger(){
        return log;
    }
}
