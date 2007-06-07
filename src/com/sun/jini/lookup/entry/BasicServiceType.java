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
package com.sun.jini.lookup.entry;

import java.awt.Image;
import java.util.ResourceBundle;
import java.util.MissingResourceException;

/**
 * An implementation of ServiceType that uses ResourceBundles.  If the
 * value of the public field type contains at least one dot ('.'), then
 * the value of the field is used as the name of the ResourceBundle.
 * Otherwise, the name of the ResourceBundle is obtained by prefixing
 * "net.jini.lookup.entry.servicetype." to the value of the public field.
 * The default locale is used.
 *
 * @author Sun Microsystems, Inc.
 *
 */
public class BasicServiceType extends net.jini.lookup.entry.ServiceType {

    private static final long serialVersionUID = -9077088179092831351L;

    /**
     * The type of service.
     *
     * @serial
     */
    public String type;

    transient private ResourceBundle bundle;
    transient private boolean inited = false;

    /** Simple constructor, leaves type field set to null. */
    public BasicServiceType() {}

    /**
     *Simple constructor, sets type field to parameter value.
     *
     * @param type type of service
     */
    public BasicServiceType(String type) {
	this.type = type;
    }

    /** Returns the resource named "icon.<var>int</var>", else null. */
    public Image getIcon(int iconKind) {
	init();
	if (bundle != null) {
	    try {
		return (Image)bundle.getObject("icon." + iconKind);
	    } catch (MissingResourceException e) {
	    }
	}
	return null;
    }

    /**
     * Returns the resource named "name", else the type field
     * stripped of any package prefix (i.e., any characters up to
     * and including the last dot ('.').
     */
    public String getDisplayName() {
	init();
	if (bundle != null) {
	    try {
		return bundle.getString("name");
	    } catch (MissingResourceException e) {
	    }
	}
	return type.substring(type.lastIndexOf('.') + 1);
    }

    /** Returns the resource named "desc", else null. */
    public String getShortDescription() {
	init();
	if (bundle != null) {
	    try {
		return bundle.getString("desc");
	    } catch (MissingResourceException e) {
	    }
	}
	return null;
    }

    private void init() {
	if (!inited) {
	    String name = type;
	    if (name.indexOf('.') < 0)
		name = "net.jini.lookup.entry.servicetype." + name;
	    try {
		bundle = ResourceBundle.getBundle(name);
	    } catch (MissingResourceException e) {
	    }
	    inited = true;
	}
    }
}
