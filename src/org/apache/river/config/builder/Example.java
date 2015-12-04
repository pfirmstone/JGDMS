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

import org.apache.river.start.ServiceStarter;
import java.security.Permission;
import net.jini.config.ConfigurationException;

/**
 */
public class Example {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws ConfigurationException
    {
        System.setSecurityManager(new SecurityManager() {

            @Override
            public void checkPermission(Permission perm)
            {
                // ignore
            }

        } );


        VelocityConfigurationBuilder cb = new VelocityConfigurationBuilder();

        cb.setServicePort(1234);
        
        //TODO: cb.setThisHere(true);
        //TODO: cb.setThatThere(false);

        ServiceStarter.main(cb.createConfiguration());
    }

}
