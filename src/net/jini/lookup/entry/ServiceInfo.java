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

/**
 * Generic information about a service.  This includes the name of the
 * manufacturer, the product, and the vendor.
 * 
 * @author Sun Microsystems, Inc.
 *
 * @see ServiceInfoBean
 */
public class ServiceInfo extends AbstractEntry implements ServiceControlled {
    private static final long serialVersionUID = -1116664185758541509L;

    /**
     * Construct an empty instance of this class.
     */
    public ServiceInfo() {
    }

    /**
     * Construct an instance of this class, with all fields
     * initialized appropriately.
     * 
     * @param name          a <code>String</code> representing the name value
     * @param manufacturer  a <code>String</code> representing the manufacturer
     *                      value
     * @param vendor        a <code>String</code> representing the vendor value
     * @param version       a <code>String</code> representing the version value
     * @param model         a <code>String</code> representing the model value
     * @param serialNumber  a <code>String</code> representing the serial 
     *                      number value
     */
    public ServiceInfo(String name, String manufacturer, String vendor,
		       String version, String model, String serialNumber)
    {
	this.name = name;
	this.manufacturer = manufacturer;
	this.vendor = vendor;
	this.version = version;
	this.model = model;
	this.serialNumber = serialNumber;
    }

    /**
     * The name of the product or package of which this service is an
     * instance.  This field should not include the name of the
     * manufacturer or vendor.
     *
     * @serial
     */
    public String name;

    /**
     * The name of the manufacturer or author of this service.  For
     * example, "Sun Microsystems".
     *
     * @serial
     */
    public String manufacturer;

    /**
     * The name of the vendor of this service.  This may have the same
     * value as the manufacturer field, or it may be different.
     *
     * @serial
     */
    public String vendor;

    /**
     * The version of this service.  This is a free-form field, but
     * should follow accepted version-naming conventions to make
     * visual identification easier.
     *
     * @serial
     */
    public String version;

    /**
     * The model name or number of this service, if any.
     *
     * @serial
     */
    public String model;

    /**
     * The serial number of this instance of the service, if any.
     *
     * @serial
     */
    public String serialNumber;
}
