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
package com.sun.jini.tool.envcheck;

import com.sun.jini.tool.envcheck.Reporter.Message;

import java.text.MessageFormat;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Base class for plugins providing rudimentary I18N support
 */
abstract public class AbstractPlugin implements Plugin {

    /** the resource bundle for the concrete class for this instance */
    private ResourceBundle bundle = null;

    /** the resource bundle for this abstract class */
    private static ResourceBundle abstractBundle = 
	Util.getResourceBundle(com.sun.jini.tool.envcheck.EnvCheck.class);

    /** 
     * Initialize the base class by obtaining the resource bundle
     * associated with the instantiated class.
     */
    protected AbstractPlugin() {
	bundle = Util.getResourceBundle(getClass());
    }

    //inherit javadoc
    public boolean isPluginOption(String opt) {
	return false;
    }

    /**
     * Get the resource bundle for this class.
     *
     * @return the resource bundle
     */
    protected ResourceBundle getBundle() {
	return bundle;
    }

    /**
     * Get the format string associated with <code>key</code> from the
     * resource bundle for this class.
     *
     * @param key the key identifying the format string
     * @return the format string
     */
    protected String getString(String key) {
	return Util.getString(key, bundle);
    }

    /**
     * Get the format string associated with <code>key</code> from the
     * resource bundle for this class.
     *
     * @param key the key identifying the format string
     * @param val the value to associate with {0}
     * @return the format string
     */
    protected String getString(String key, Object val) {
	return Util.getString(key, bundle, val);
    }

    /**
     * Get the format string associated with <code>key</code> from the
     * resource bundle for this class.
     *
     * @param key the key identifying the format string
     * @param val1 the value to associate with {0}
     * @param val2 the value to associate with {1}
     * @return the format string
     */
    protected String getString(String key, Object val1, Object val2) {
	return Util.getString(key, bundle, val1, val2);
    }

    /**
     * Get the format string associated with <code>key</code> from the
     * resource bundle for this class.
     *
     * @param key the key identifying the format string
     * @param v1 the value to associate with {0}
     * @param v2 the value to associate with {1}
     * @param v3 the value to associate with {2}
     * @return the format string
     */
    protected String getString(String key, Object v1, Object v2, Object v3) {
	return Util.getString(key, bundle, v1, v2, v3);
    }

    /**
     * Return a fully qualified external class name for the given static
     * inner class name.
     *
     * @param name the unqualified name of the inner class
     * @return the fully qualified name
     */
    protected String taskName(String name) {
	return getClass().getName() + "$" + name;
    }

    /**
     * Utility method which can be called to process objects of 
     * unexpected type which are returned by a subtask. If
     * <code>returnedValue</code> is not a <code>Throwable</code>, 
     * then <code>Thread.dumpStack()</code> is called to help located
     * the source of the problem. Otherwise a stacktrace is printed;
     * the <code>-traces</code> option does not affect this trace.
     *
     * @param returnedValue the object returned by the subtask
     * @param source descriptive text identifying the source at the time
     *               the subtask was launched
     */
    protected void handleUnexpectedSubtaskReturn(Object returnedValue, 
						 String source) 
    {
	Message message;
	if (returnedValue == null) {
	    message = new Message(Reporter.ERROR,
				  Util.getString("abstractPlugin.nullvalue", 
						 abstractBundle),
				  null);
	    Reporter.print(message, source);
	    Thread.dumpStack();
	} else if (!(returnedValue instanceof Throwable)) {
	    message = new Message(Reporter.ERROR,
				  Util.getString("abstractPlugin.unknownObject",
						 abstractBundle,
						 returnedValue.toString()),
				  null);
	    Reporter.print(message, source);
	    Thread.dumpStack();
	} else { // force stack trace, don't pass exception in the message
	    Throwable t = (Throwable) returnedValue;
	    message = new Message(Reporter.ERROR,
				  Util.getString("abstractPlugin.excReturn",
						 abstractBundle,
						 t.getMessage()),
				  null);
	    System.err.println(Util.getString("abstractPlugin.excReturn",
						abstractBundle,
						t.getMessage()));
	    Reporter.print(message, source);
	    t.printStackTrace();
	}
    }
}
