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

package net.jini.config;

import org.apache.river.logging.Levels;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A <code>Configuration</code> with no entries. Applications can use an
 * instance of this class to simplify handling cases where no configuration is
 * specified rather than, for example, checking for a <code>null</code>
 * configuration.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 *
 * <!-- Implementation Specifics -->
 *
 * This implementation uses the {@link Logger} named
 * <code>net.jini.config</code> to log information at the following logging
 * levels: <br>
 *
 * <table border="1" cellpadding="5" summary="Describes logging performed by
 *	  the EmptyConfiguration class at different logging levels">
 *
 * <caption><b><code>net.jini.config</code></b></caption>
 *
 * <tr> <th scope="col"> Level <th scope="col"> Description
 *
 * <tr> <td> {@link Levels#FAILED FAILED} <td> problems getting entries,
 *	including getting entries that are not found
 *
 * <tr> <td> {@link Level#FINE FINE} <td> returning default values
 *
 * </table>
 */
public class EmptyConfiguration extends AbstractConfiguration {

    /** A <code>Configuration</code> with no entries. */
    public static final EmptyConfiguration INSTANCE = new EmptyConfiguration();

    /* Insure there is only one instance */
    private EmptyConfiguration() { }

    /**
     * Always throws an exception -- this configuration contains no entries.
     *
     * @throws NullPointerException {@inheritDoc}
     * @throws NoSuchEntryException unless <code>component</code>,
     * <code>name</code>, or <code>type</code> is <code>null</code>
     */
    @Override
    protected <T> T getEntryInternal(
	String component, String name, Class<T> type, Object data)
	throws NoSuchEntryException
    {
	if (component == null || name == null || type == null) {
	    throw new NullPointerException(
		"component, name and type cannot be null");
	}
	throw new NoSuchEntryException(
	    "Entry not found for component " + component + ", name " + name);
    }
}
