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
package com.sun.jini.test.impl.reggie;

import com.sun.jini.qa.harness.LegacyTest;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;

/**
 * LegacyTest that verifies that Reggie will attempt to connect to
 * all addresses a multihomed client host resolves to
 * 
 */
public class MultihomedClientTest implements LegacyTest {

    private String command = System.getProperty("java.home") + File.separator
        + "bin" + File.separator + "java ";
    private QAConfig config = null;

    public Test construct(QAConfig config) {
        this.config = config;
        return this;
    }

    public void run() throws Exception {
        StringBuffer buff = new StringBuffer(command);
        appendProperties(buff);
        buff.append("com.sun.jini.test.impl.reggie.Driver");
        System.out.println(buff);
        Process p = Runtime.getRuntime().exec(buff.toString());
        new ProcessReader(p.getInputStream(),System.out);
        new ProcessReader(p.getErrorStream(),System.err);
        p.waitFor();
        if (p.exitValue()!=0) 
            throw new Exception("Test failed");
    }

    public void tearDown(){};

    private void appendProperties(StringBuffer buffer) {
        buffer.append("-cp ").append(
            config.getStringConfigVal("testClasspath",""))
                .append(File.pathSeparator).append(
                config.getStringConfigVal("metaInf","")).append(" ");
        buffer.append("-Djava.security.manager= ");
        buffer.append("-Djava.security.policy=").append(
            config.getStringConfigVal("policy","")).append(" ");
        buffer.append("-Dsun.net.spi.nameservice.provider.1=").append(
            config.getStringConfigVal("nameservice","")).append(" ");
        buffer.append("-Dpolicy=").append(
            config.getStringConfigVal("policy","")).append(" ");
        buffer.append("-Dlib-dl=").append(config.getStringConfigVal(
            "lib-dl","")).append(" ");
        buffer.append("-DtoolClassPath=").append(
            config.getStringConfigVal("toolClassPath","")).append(" ");
        buffer.append("-DreggieClasses=").append(
            config.getStringConfigVal("reggieClasses","")).append(" ");
        buffer.append("-DreggieConfig=").append(
            config.getStringConfigVal("reggieConfig","")).append(" ");
        buffer.append("-DstartConfig=").append(
            config.getStringConfigVal("startConfig","")).append(" ");
        buffer.append("-DlookupConfig=").append(
            config.getStringConfigVal("lookupConfig","")).append(" ");
        buffer.append("-Dtimeout=").append(
            config.getStringConfigVal("timeout","")).append(" ");       
        buffer.append("-Djava.util.logging.config.file=").append(
            config.getStringConfigVal("logging", "")).append(" ");

    }

    /**
     * Safe to start thread in constructor, it's stateless.
     */
    public static class ProcessReader {
        public ProcessReader(final InputStream input, final PrintStream out) {
            Thread inputThread = new Thread( new Runnable() {
                public void run() {
                    BufferedReader inputReader =
                        new BufferedReader( new InputStreamReader(input));
                    String line = null;
                    do {
                        try {
                            line = inputReader.readLine();
                            if (line!=null) {
                                out.println(line);
                                out.flush();
                            }
                        } catch (Exception e){
                            line=null;
                        }
                    } while(line!=null);
                    out.flush();
                    out.close();
                }
            });
            inputThread.setDaemon(false);
            inputThread.start();
        }
    }
}
