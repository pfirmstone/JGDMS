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
package org.apache.river.qa.harness;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.text.MessageFormat;
import java.util.Date;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

/**
 * Logging formatter for test harness output.The message is optionally
 * prepended with the host, time, level, and an abbreviated class.method
 * signature indicating the source of the log message. These fields can be
 * selectively disabled via the following <code>logging</code> properties:
 * <pre>
 *   org.apache.river.qa.harness.HarnessFormatter.showhost=false
 *   org.apache.river.qa.harness.HarnessFormatter.showlevel=false
 *   org.apache.river.qa.harness.HarnessFormatter.showsource=false
 *   org.apache.river.qa.harness.HarnessFormatter.showtime=false
 * </pre>
 * Note that the host name will be prepended only if the test is
 * running distributed.
 * <p>
 * The logging property 
 * <code>org.apache.river.qa.harness.HarnessFormatter.timeinterval</code>
 * defines the interval in seconds between the generation of timestamps.
 * If &lt;= 0, the time is prepended to each log record. Otherwise,
 * a separate timestamp line is printed whenever a log record is printed
 * and <code>timeinterval</code> seconds has elapsed since the previous
 * timestamp. The default value is 10 seconds. If the property is specified
 * and the value is not a valid integer, the default value is silently used.
 */
class HarnessFormatter extends SimpleFormatter {

    /** the format string for the timestamp */
    private final static String format = "{0,time}";

    /** the message formatter for formatting the timestamp */
    private MessageFormat formatter;

    /** the platform dependent line separator string */
    private String sep = System.getProperty("line.separator");

    /** flag to control showing the time */
    private boolean showTimestamp = true;

    /** flag to control showing the calling method */
    private boolean showSource = true;

    /** flag to control showing the logging level */

    private boolean showLevel = true;

    /** flag to control showing the host name */
    private boolean showHost = true;

    /** the host name */
    private String hostName = "unknown";

    /** the most recent timestamp */
    private Date lastTimestamp = null;

    /** the interval between timestamps */
    private int interval = 10;

    /**
     * Construct the formatter. Obtain logging properties which
     * control the output format.
     */
    HarnessFormatter() {
	boolean distributed = QAConfig.isDistributed();
	LogManager manager = LogManager.getLogManager();
	String cname = getClass().getName();

	String val = manager.getProperty(cname +".showlevel");
	if (val != null && val.equalsIgnoreCase("false")) {
	    showLevel = false;
	}
	val = manager.getProperty(cname +".showsource");
	if (val != null && val.equalsIgnoreCase("false")) {
	    showSource = false;
	}
	val = manager.getProperty(cname +".showtime");
	if (val != null && val.equalsIgnoreCase("false")) {
	    showTimestamp = false;
	}
	val = manager.getProperty(cname +".showhost");
	if (val != null && val.equalsIgnoreCase("false")) {
	    showHost = false;
	}
	val = manager.getProperty(cname + ".timeinterval");
	if (val != null) {
	    try {
		interval = Integer.parseInt(val);
	    } catch (NumberFormatException e) {
	    }
	}
	if (!distributed) {
	    showHost = false;
	}
	try {
	    if (showHost) {
		hostName = InetAddress.getLocalHost().getHostName();
	    }
	} catch (Exception ignore) {
	}
    }

    /**
     * Format the log record. For levels of <code>CONFIG</code>,
     * <code>INFO</code>, and <code>SEVERE</code>, the log message is simply
     * logged without modification. For other logging levels, the message is
     * optionally prepended with the host, time, level, and an abbreviated
     * class.method signature indicating the source of the log message.
     * If the log record contains an exception, a trace of the exception
     * is also generated.
     *
     * @param record the logging record
     * @return the formatted record
     */
    public synchronized String format(LogRecord record) {

	String message = formatMessage(record);
	StringBuffer sb = new StringBuffer();
	Level level = record.getLevel();
	boolean lineNotEmpty = false;
	if (showTimestamp) {
	    if (formatter == null) {
		formatter = new MessageFormat(format);
	    }
	    Date date = new Date();
	    date.setTime(record.getMillis());
	    int millis = interval * 1000;
	    if (lastTimestamp == null
		|| interval <= 0
		|| (date.getTime() - lastTimestamp.getTime()) > millis)
	    {
		if (interval > 0) {
		    sb.append("\nTIME: ");
		}
		lastTimestamp = date;
		Object[] args = new Object[]{date};
	        formatter.format(args, sb, null);
		if (interval > 0) {
		    sb.append("\n\n");
		} else {
		    lineNotEmpty = true;
		}
	    }
	}
	if (showHost) {
	    if (lineNotEmpty) {
		sb.append(" ");
	    }
	    sb.append(hostName);
	    lineNotEmpty = true;
	}
	if (showSource) {
	    String className = record.getSourceClassName();
	    if (className != null) {
		if (lineNotEmpty) {
		    sb.append(" ");
		}
		int last = className.lastIndexOf(".");
		if (last >= 0) {
		    className = className.substring(last + 1);
		}
		className += ".";
		sb.append(className);
		lineNotEmpty = true;
	    }
	    String methodName = record.getSourceMethodName();
	    if (methodName != null) {
		if (className == null) {
		    if (lineNotEmpty) {
			sb.append(" ");
		    }
		}
		sb.append(methodName);
		lineNotEmpty = true;
	    }
	}
	if (showLevel) {
	    if (lineNotEmpty) {
		sb.append(" ");
	    }
	    sb.append(record.getLevel().getLocalizedName());
	    lineNotEmpty = true;
	}
	if (lineNotEmpty) {
	    sb.append(": ");
	}
	sb.append(message);
	sb.append(sep);
	if (record.getThrown() != null) {
	    try {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		record.getThrown().printStackTrace(pw);
		pw.close();
		sb.append(sw.toString());
	    } catch (Exception ex) {
	    }
	}
	return sb.toString();
    }
}
	    
