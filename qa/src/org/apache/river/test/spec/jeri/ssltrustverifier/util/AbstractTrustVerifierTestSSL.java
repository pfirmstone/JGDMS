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
package org.apache.river.test.spec.jeri.ssltrustverifier.util;

//harness imports
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.LegacyTest;

//java.util
import org.apache.river.qa.harness.Test;
import java.util.logging.Logger;

public abstract class AbstractTrustVerifierTestSSL implements LegacyTest {

    protected static QAConfig sysConfig;
    protected static Logger log;
    private final static String component = "org.apache.river.test.spec."
        + "jeri.ssltrustverifier";

    //inherit javadoc
    public Test construct(QAConfig config) {
        sysConfig = config;
        log = Logger.getLogger(
            "org.apache.river.test.spec.jeri.ssltrustverifier");
        return this;
    }

    //inherit javadoc
    public void tearDown() {
    }

    //inherit javadoc
    public static Logger getLogger(){
        return log;
    }

    /**
     * Method to extract string values from harness facilities
     * @param name Name of property to get value for
     * @return String value of property
     */
    public String getStringValue(String name) {
        String s = component + "." + name;
        return sysConfig.getStringConfigVal(s,null);
    }
}
