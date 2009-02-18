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

import java.util.logging.Filter;
import java.util.logging.Formatter;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Level;
import java.util.logging.StreamHandler;

/**
 * A custom logging handler which causes logging records to be
 * written to <code>System.out</code>. Several logging properties
 * may be defined:
 * <table>
 * <tr>
 * <td>com.sun.jini.qa.harness.ReportHandler.level
 * <td>the threshold for processing log records. Default is <code>FINEST</code>
 * <tr>
 * <td>com.sun.jini.qa.harness.ReportHandler.filter
 * <td>the name of a filter class to use. Default is <code>null</code>
 * <tr>
 * <td>com.sun.jini.qa.harness.ReportHandler.formatter
 * <td>the name of a formatter class to use. Default is 
 *     <code>com.sun.jini.qa.harness.HarnessFormatter</code>
 * <tr>
 * <td>com.sun.jini.qa.harness.ReportHandler.encoding
 * <td>the character encoding to use. Default is <code>null</code>
 * </table>
 */
class ReportHandler extends StreamHandler {

    /* just process the logging properties */
    private void configure() {
        LogManager manager = LogManager.getLogManager();
	String cname = getClass().getName();

	String val = null;
	try {
	    setLevel(Level.FINEST);
	    val = manager.getProperty(cname +".level");
	    if (val != null) {
		setLevel(Level.parse(val.trim()));
	    }
	} catch (Exception ignore) {
	}
	try {
	    setFilter(null);
	    val = manager.getProperty(cname +".filter");
	    if (val != null) {
                Class clz = ClassLoader.getSystemClassLoader().loadClass(val);
                setFilter((Filter) clz.newInstance());
	    }
	} catch (Exception ignore) {
	}
	try {
	    setFormatter(new HarnessFormatter());
	    val = manager.getProperty(cname +".formatter");
	    if (val != null) {
                Class clz = ClassLoader.getSystemClassLoader().loadClass(val);
                setFormatter((Formatter) clz.newInstance());
	    }
	} catch (Exception ignore) {
	}
	try {
	    setEncoding(null);
	    val = manager.getProperty(cname +".encoding");
	    if (val != null) {
		setEncoding(val);
	    }
	} catch (Exception ex) {
	}
    }

    /**
     * Create a <code>ConsoleHandler</code> for <code>System.err</code>.
     * <p>
     * The <code>ConsoleHandler</code> is configured based on
     * <code>LogManager</code> properties (or their default values).
     * 
     */
    ReportHandler() {
	configure();
	setOutputStream(System.out);
    }

    /**
     * Conditionally Publish the record. The record will be discarded
     * if all of the following are true:
     *
     * <ul>
     *   <li>full logging is turned on
     *   <li>the level of the record is below <code>Level.CONFIG</code>
     *   <li>the logger name begins with <code>net.jini</code> or
     *       <code>com.sun.jini</code>, but not <code>com.sun.jini.qa</code>
     * <ul>
     *
     * @param record the logging record to publish
     */
    public void publish(LogRecord record) {
	super.publish(record);	
	flush();
    }

    /* inherit javadoc */
    public void close() {
	flush();
    }
}
