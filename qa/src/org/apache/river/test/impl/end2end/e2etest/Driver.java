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
package org.apache.river.test.impl.end2end.e2etest;

import java.util.logging.Level;

import org.apache.river.qa.harness.TestException;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.LegacyTest;
import org.apache.river.qa.harness.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.File;
import java.io.FileReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;

import java.net.URL;

import java.util.logging.Level;
import java.util.logging.Logger;

import java.util.Iterator;
import java.util.Properties;

/**
 * This class acts as a wrapper that enable the End2End security test
 * to run as a <code>org.apache.river.qa.harness.LegacyTest</code>
 */
public class Driver implements LegacyTest {

    private QAConfig config;
    private File error;
    private File output;
    private PrintWriter outputStream;
    private PrintWriter errStream;
    private File kError;
    private File kOutput;
    private PrintWriter kOutputStream;
    private PrintWriter kErrStream;
    private String command = System.getProperty("java.home") + File.separator
        + "bin" + File.separator + "java ";
    private static final Logger log = Logger.getLogger("org.apache.river.test.impl."
        + "end2end.e2etest.Driver");

    /**
     * The <code>construct</code> method creates the creates output files for the
     * test and also runs a user specified kinit command in order to populate
     * the kerberos ticket cache with a forwardable ticket.  This is done
     * only if the kerberos provider is being used.
     */
    public Test construct(QAConfig config) throws Exception {
        this.config = config;
        createStreams();
        String props = config.getStringConfigVal(
            "org.apache.river.test.impl.end2end.properties",null);
	URL url = config.getComponentURL(props, null);
        getProperties(url);
        command += config
            .getStringConfigVal("org.apache.river.qa.harness.assertions","")
            + " -cp " + config.getStringConfigVal("testClassPath",null)
            + " org.apache.river.test.impl.end2end.e2etest.End2EndTest";
        return this;
    }

    /**
     * The <code>run</code> method invokes the main method on
     * <code>org.apache.river.test.impl.end2end.e2etest.End2EndTest</code>
     * and then parses the output of the test to determine if it passed or
     * failed.  A failures is reported if any of the tests run fails.  The
     * test passes only if all the tests run pass.
     */
    public void run() throws Exception {
        log.finer(command);
        System.out.println(command);
        Process p = Runtime.getRuntime().exec(command);
        new ProcessReader(p.getInputStream(),outputStream);
        new ProcessReader(p.getErrorStream(),errStream);
        p.waitFor();
        outputStream.close();
        errStream.close();
        processResults();
    }

    /**
     * Dummy method to satisfy <code>org.apache.river.qa.harness.LegacyTest</code>
     * interface.
     */
    public void tearDown() {
    }

    /**
     * The <code>getProperties</code> method extracts the property keys for
     * the test from a file and then requests the values for these keys from
     * the configuration object.  The property values are extracted from the
     * configuration object to allow for string substitution in property
     * values.
     *
     * @param url The URL of the file or jar entry containing the property keys
     * for the test.
     */
    private void getProperties(URL url)
        throws Exception {
        InputStream is = url.openStream();
        Properties props = new Properties();
        props.load(is);
        Iterator it = props.keySet().iterator();
        while (it.hasNext()) {
            String property = (String) it.next();
            String value = null;
            value = config.getStringConfigVal(property,null);
            if (property.equals("policy")) {
                property = "java.security.policy";
            }
            if (value!=null){
                String prop = "-D" + property.trim() + "=" + value.trim()
                    + " ";
                System.out.println(prop);
                log.log(Level.FINE,prop);
                command += prop;
            }
        }
        if (config.getStringConfigVal("end2end.kerberos",null)!=null) {
            command += "-Dend2end.kerberos=true ";
            initializeKerberos();
        } else if (config.getStringConfigVal("end2end.jsse",null)!=null){
            command += "-Dend2end.jsse=true ";
        } else if (config.getStringConfigVal("end2end.https",null)!=null){
            command += "-Dend2end.https=true ";
        }
    }

    /**
     * Creates files to which the standard and error streams for the
     * test are redirected.
     */
    private void createStreams() throws IOException, TestException {
        File dir = config.createUniqueDirectory("end2end_","_output",null);
        output = new File(dir.getAbsolutePath() + File.separator +
            "end2end.output");
        error = new File(dir.getAbsolutePath() + File.separator +
            "end2end.error");
        kError = new File(dir.getAbsolutePath() + File.separator +
            "kerberos.output");
        kOutput = new File(dir.getAbsolutePath() + File.separator +
            "kerberos.error");
        outputStream = new PrintWriter( new FileOutputStream(output));
        errStream = new PrintWriter( new FileOutputStream(error));
        kOutputStream = new PrintWriter( new FileOutputStream(kOutput));
        kErrStream = new PrintWriter( new FileOutputStream(kError));
    }

    /**
     * Reads the contents of the standard output file to determine if any
     * failures occurred in the test.  If any failures occurred, a
     * TestException is thrown.  Otherwise, a pass status is returned.
     */
    private void processResults() throws Exception {
        BufferedReader reader = new BufferedReader(new FileReader(output));
        String line = reader.readLine();
        boolean stop = false;
        int totalFailures = 0;
        int totalTests = 0;
        while ((line!=null)&&(!stop)) {
            if (line.indexOf("Total tests run") > -1) {
                totalTests = Integer.parseInt(
                    line.substring(line.lastIndexOf(":") + 1,
                    line.length()).trim());
            }
            if (line.indexOf("Number of failures") > -1) {
                totalFailures = Integer.parseInt(
                    line.substring(line.lastIndexOf(":") + 1,
                    line.length()).trim());
            }
            if (line.indexOf("Testing Complete") > -1) {
                stop = true;
            }
            line = reader.readLine();
        }
        if (!stop) {
            throw new TestException("Test did not complete"
                + " see details at " + error.getAbsolutePath() + " and"
                + " " + output.getAbsolutePath());
        } else {
            if (totalFailures>0) {
                throw new TestException(totalFailures
                    + " out of " + totalTests + " failed.  See details"
                    + " at " + output.getAbsolutePath());
            }
        }
    }

    /**
     * This method runs a user-specified kinit command to populate the
     * kerberos ticket cache with a forwardable ticket granting ticket.  A
     * forwardable ticket-
     */
    private void initializeKerberos() throws Exception {
        String command = config.getStringConfigVal(
            "org.apache.river.test.impl.end2end.kinit",null);
        log.fine(command);
        Process p = Runtime.getRuntime().exec(command);
        new ProcessReader(p.getInputStream(), kOutputStream);
        new ProcessReader(p.getErrorStream(), kErrStream);
        p.waitFor();
        p.destroy();
    }

    /**
     * Utility class to read output from a process.
     * This class is stateless, this is the only case where it's safe to start
     * a Thread from inside the constructor.
     */
    public static class ProcessReader {
        public ProcessReader(final InputStream input, final PrintWriter out) {
            Thread inputThread = new Thread( new Runnable() {
                public void run() {
                    BufferedReader inputReader =
                        new BufferedReader( new InputStreamReader(input));
                    String line = null;
                    do {
                        try {
                            line = inputReader.readLine();
                            if (line!=null) {
                                System.out.println(line);
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
