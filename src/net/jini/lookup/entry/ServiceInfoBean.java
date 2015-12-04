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

import java.io.Serializable;
import net.jini.core.entry.Entry;

/**
 * A JavaBeans(TM) component that encapsulates a ServiceInfo object.
 * 
 * @author Sun Microsystems, Inc.
 *
 * @see ServiceInfo
 */
public class ServiceInfoBean implements EntryBean, Serializable {
    private static final long serialVersionUID = 8352546663361067804L;

    /**
     * The ServiceInfo object associated with this JavaBeans component.
     *
     * @serial
     */
    protected ServiceInfo assoc;

    /**
     * Construct a new JavaBeans component, linked to a new empty 
     * ServiceInfo object.
     */
    public ServiceInfoBean() {
	assoc = new ServiceInfo();
    }

    /**
     * Make a link to an Entry object.
     *
     * @param e the Entry object to link to
     * @exception java.lang.ClassCastException the Entry is not of the
     * correct type for this JavaBeans component
     */
    public void makeLink(Entry e) {
	assoc = (ServiceInfo) e;
    }

    /**
     * Return the Entry linked to by this JavaBeans component.
     */
    public Entry followLink() {
	return assoc;
    }

    /**
     * Return the value of the name field in the ServiceInfo object linked
     * to by this JavaBeans component.
     *
     * @return a String representing the name value
     * @see #setName
     */
    public String getName() {
	return assoc.name;
    }

    /**
     * Set the value of the name field in the ServiceInfo object
     * linked to by this JavaBeans component.
     *
     * @param x  a String specifying the name value 
     * @see #getName
     */
    public void setName(String x) {
	assoc.name = x;
    }

    /**
     * Return the value of the manufacturer field in the ServiceInfo
     * object linked to by this JavaBeans component.
     *
     * @return a String representing the manufacturer value
     * @see #setManufacturer
     */
    public String getManufacturer() {
	return assoc.manufacturer;
    }

    /**
     * Set the value of the manufacturer field in the ServiceInfo
     * object linked to by this JavaBeans component.
     *
     * @param x  a String specifying the manufacturer value 
     * @see #getManufacturer
     */
    public void setManufacturer(String x) {
	assoc.manufacturer = x;
    }

    /**
     * Return the value of the vendor field in the ServiceInfo object
     * linked to by this JavaBeans component.
     *
     * @return a String representing the vendor value
     * @see #setVendor
     */
    public String getVendor() {
	return assoc.vendor;
    }

    /**
     * Set the value of the vendor field in the ServiceInfo object
     * linked to by this JavaBeans component.
     *
     * @param x  a String specifying the vendor value 
     * @see #getVendor
     */
    public void setVendor(String x) {
	assoc.vendor = x;
    }

    /**
     * Return the value of the version field in the ServiceInfo object
     * linked to by this JavaBeans component.
     *
     * @return a String representing the version value
     * @see #setVersion
     */
    public String getVersion() {
	return assoc.version;
    }

    /**
     * Set the value of the version field in the ServiceInfo object
     * linked to by this JavaBeans component.
     *
     * @param x  a String specifying the version value 
     * @see #getVersion
     */
    public void setVersion(String x) {
	assoc.version = x;
    }

    /**
     * Return the value of the model field in the ServiceInfo object
     * linked to by this JavaBeans component.
     *
     * @return a String representing the model value
     * @see #setModel
     */
    public String getModel() {
	return assoc.model;
    }

    /**
     * Set the value of the model field in the ServiceInfo object
     * linked to by this JavaBeans component.
     *
     * @param x  a String specifying the model value 
     * @see #getModel
     */
    public void setModel(String x) {
	assoc.model = x;
    }

    /**
     * Return the value of the serialNumber field in the ServiceInfo object
     * linked to by this JavaBeans component.
     *
     * @return a String representing the serial number
     * @see #setSerialNumber
     */
    public String getSerialNumber() {
	return assoc.serialNumber;
    }

    /**
     * Set the value of the serialNumber field in the ServiceInfo object
     * linked to by this JavaBeans component.
     *
     * @param x  a String specifying the serial number 
     * @see #getSerialNumber
     */
    public void setSerialNumber(String x) {
	assoc.serialNumber = x;
    }
}
