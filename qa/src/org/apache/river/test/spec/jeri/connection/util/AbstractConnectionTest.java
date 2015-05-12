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
package org.apache.river.test.spec.jeri.connection.util;

//test harness related imports
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.LegacyTest;


//java.util.logging
import org.apache.river.qa.harness.Test;
import java.util.logging.Logger;

/**
 * Abstract class for <code>BasicJeriExporter</code> tests
 */
public abstract class AbstractConnectionTest implements LegacyTest{

    protected static QAConfig config;
    protected static Logger log = Logger.getLogger(
        "org.apache.river.test.spec.jeri.connection");
    private int port = 0;

    public Test construct(QAConfig config) {
        this.config = config;
        port = config.getIntConfigVal(
            "org.apache.river.test.spec.jeri.connection.listenPort", 9090);
        return this;
    }

    public void tearDown() {
    }

    public static Logger getLogger() {
        return log;
    }

    public int getListenPort() {
        return port;
    }
}
