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
package net.jini.lookup.entry;

import net.jini.entry.AbstractEntry;
import java.awt.Image;

/**
 * Human-oriented information about the "type" of a service.  This is
 * not related to its data or class types, and is more oriented towards
 * allowing someone to determine what a service (for example, a printer)
 * does and that it is similar to another, without needing to know
 * anything about data or class types for the Java(TM) platform.
 * 
 * @author Sun Microsystems, Inc.
 */
public abstract class ServiceType extends AbstractEntry
				  implements ServiceControlled
{
    private static final long serialVersionUID = -6443809721367395836L;

    /**
     * Instantiate this class.
     */
    public ServiceType() {
    }

    /**
     * Get an icon for this service.  This icon may be localized where
     * appropriate.  The valid parameter values are the same as for
     * the getIcon method of java.beans.BeanInfo.
     *
     * @param iconKind the kind of icon to return
     * @return the icon for this service
     */
    public Image getIcon(int iconKind) {
	return null;
    }

    /**
     * Return the localized display name of this service.
     * @return the localized display name of this service
     */
    public String getDisplayName() {
	return null;
    }

    /**
     * Return a localized short description of this service.
     * @return a String representing the short description of this service 
     */
    public String getShortDescription() {
	return null;
    }
}
