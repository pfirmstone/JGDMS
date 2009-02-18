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
package com.sun.jini.qa.harness;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.PrintStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.StringTokenizer;

/**
 * Generates an xml report of the test results.
 * <p>
 * This class relies on these config entries:
 * <ul>
 * <li><code>com.sun.jini.qa.harness.generateXml.resultLog</code> is
 *     the file name of the report.
 * </ul>
 */
public class XmlReport {

    private QAConfig config; 
    private PrintStream out; 
    private TestList testList;
    private String category;

    /**
     * Construct a new XmlReport object.
     *
     * @param config the config object used to retrieve config entries
     * @param testList the data structure contains test results
     */
    XmlReport(QAConfig config, TestList testList) 
	throws IOException, IllegalStateException 
    {
	this.config = config;
	this.testList = testList;
	out = getPrintStream();

	category = config.getStringConfigVal("categories", null);
        if (category == null)
            category = config.getStringConfigVal("category", null);
        if (category == null) {
	    throw new IllegalStateException(
		"A category must have been specified to create an XML report");
        } else {
            StringTokenizer st = new StringTokenizer(category, ",");
	    if (st.countTokens() != 1) {
	        throw new IllegalStateException(
		    "Only 1 category can be specified to create an XML report");
	    } else {
		category = st.nextToken();
	    }
	}
    }

    /**
     * Generate the xml report using the testList provided to constructor.
     */
    public void generate() throws IOException {
	printHeading();
	out.println("<report>");
	printVersion();
	printDate();
	printSuite();
	out.println("</report>");
    }

    /**
     * Opens stream to write the report to.  The underlying filename
     * is retrieved from the config object.
     */
    private PrintStream getPrintStream() throws IOException {
	String resultLog = config.getStringConfigVal(
	    "com.sun.jini.qa.harness.generateXml.resultLog","results.xml");
	File resultDir = (new File(resultLog)).getParentFile();
	if (resultDir != null && !resultDir.exists()) {
	    resultDir.mkdirs();
	}
        return new PrintStream(new FileOutputStream(resultLog));
    }

    /**
     * Prints report header.
     */
    private void printHeading() throws IOException {
	out.println("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\" ?>");
	out.println();
    }

    /**
     * Prints the report version.
     */
    private void printVersion() throws IOException { 
	out.println("<version>1.0</version>");
    }

    /**
     * Prints the current date to the report.
     */
    private void printDate() throws IOException { 
	out.println("<date>" 
	    + (new Date(System.currentTimeMillis()))
	    + "</date>");
    }

    /**
     * Prints results table.  Failed tests that are rerun (any number of
     * times) and finally pass are printed as a 'passed' test result.
     */
    private void printSuite() throws IOException {
	out.println("<testSuites>");
	out.println("  <testSuite id=\"" + category + ":0\">");
	out.println("    <name>" + category + "</name>");
	out.println("    <starttime>" + testList.getStartTime() 
	    + "</starttime>");
	out.println("    <endtime>" + testList.getFinishTime() + "</endtime>");

	out.println("    <tests>");
        TestList.TestResultIterator iter = testList.createTestResultIterator();
        while (iter.hasMore()) {
            TestResult[] results = iter.next();
	    for (int i = 0; i < results.length; i++) {
		String logFile = "";
		if (results[i].logFile != null) {
		    logFile = results[i].logFile.getName();
		}
		String status = "";
		switch (results[i].type) {
		    case 0:
		    case 1:
		    case 2:
			status = "Failed";
			break;
		    case 3:
			status = "Skipped";
			break;
		    case 4:
		    case 5:
			status = "Failed";
			break;
		    case 6:
			status = "Passed";
			break;
		    default:
			status = "";
			break;
		}
		out.println("      <test id=\""
		    + formatTestName(results[i].run.td)
		    + ":" + i + "\">");
		out.println("        <name>"
		    + formatTestName(results[i].run.td)
		    + "</name>");
		out.println("        <logfile>" + logFile + "</logfile>");
		out.println("        <message>"
		    + results[i].message
		    + "</message>");
		out.println("        <state>" + status + "</state>");
		out.println("        <status>"
		    + results[i].type
		    + "</status>");
		out.println("        <rerun>" + i + "</rerun>");
		out.println("      </test>");
	    }
	}
	out.println("    </tests>");
	out.println("  </testSuite>");
	out.println("</testSuites>");
    }

    /**
     * Retrieves test name from the given TestDescription and
     * replaces forward slashes ('\') with back slashes ('/').
     */
    private String formatTestName(TestDescription td) {
	return td.getName().replace('\\','/');
    }

}
