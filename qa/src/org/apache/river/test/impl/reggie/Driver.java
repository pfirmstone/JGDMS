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
package org.apache.river.test.impl.reggie;

import org.apache.river.start.ServiceStarter;
import java.net.InetAddress;
import java.util.logging.Logger;
import java.util.logging.Level;
import net.jini.config.ConfigurationProvider;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.discovery.DiscoveryEvent;
import net.jini.discovery.DiscoveryListener;
import net.jini.discovery.LookupDiscovery;

/**
 * Main class which executes the test 
 */
public class Driver implements DiscoveryListener {

    private static final Logger logger = Logger.getLogger(
        Driver.class.getName());

    private volatile boolean done = false;

    public static void main(String[] args) {
        try {
            InetAddress[] addresses = InetAddress.getAllByName("testClient");
            StringBuffer buff = new StringBuffer();
            buff.append("testClient resolves to: ");
            for (int i=0; i < addresses.length; i++) {
                buff.append(addresses[i]).append(" ");
            }
            logger.log(Level.INFO, buff.toString());
            startReggie();
            Thread.currentThread().sleep(10000);
            LookupDiscovery ld = new LookupDiscovery(
                new String[]{
                    "org.apache.river.test.impl.reggie.MultihomedClientTest"},
                ConfigurationProvider.getInstance(new String[]{
                    System.getProperty("lookupConfig")}));
            Driver driver = new Driver();
            ld.addDiscoveryListener(driver);
            ServiceRegistrar[] registrars = ld.getRegistrars();
            for (int i=0; i < registrars.length; i++) {
                driver.done = true;
                System.out.println(registrars[i]);
            }
            int timeout = 
                Integer.getInteger("timeout").intValue();
            long stopAt = System.currentTimeMillis() + (timeout * 60 * 1000);
            while (!driver.done && System.currentTimeMillis() < stopAt) {
                Thread.currentThread().sleep(1000);
            }
            if (driver.done) {
                System.exit(0);
            } else {
                System.exit(-1);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }
        
    }

    public void discovered(DiscoveryEvent e) {
        done = true;
        System.out.println(e.getRegistrars()[0]);
    }

    public void discarded(DiscoveryEvent e) {
        System.out.println(e.getRegistrars()[0]);
    }

    private static void startReggie() throws Exception {
        Thread t = new Thread(new Runnable() {
            public void run() {
                ServiceStarter.main(new String[]{System.getProperty(
                    "startConfig")});
            }
        });
        t.start();
    }

}
