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
 * The address of the physical component of a service.  This is
 * distinct from the Location class in that it is intended for use
 * with the Location class in geographically dispersed organizations.
 * 
 * @author Sun Microsystems, Inc.
 *
 * @see Location
 * @see AddressBean
 */
public class Address extends AbstractEntry {
    private static final long serialVersionUID = 2896136903322046578L;

    /**
     * Construct an empty instance of this class.
     */
    public Address() {
    }

    /**
     * Construct an instance of this class, with all fields
     * initialized appropriately.
     *
     * @param street              a String representing the street address
     * @param organization        a String representing the organization
     * @param organizationalUnit  a String representing the organizational unit
     * @param locality            a String representing the locality
     * @param stateOrProvince     a String representing the state or province
     * @param postalCode          a String representing the postal code
     * @param country             a String representing the country
     */
    public Address(String street, String organization,
		   String organizationalUnit, String locality,
		   String stateOrProvince, String postalCode, String country)
    {
	this.street = street;
	this.organization = organization;
	this.organizationalUnit = organizationalUnit;
	this.locality = locality;
	this.stateOrProvince = stateOrProvince;
	this.postalCode = postalCode;
	this.country = country;
    }
    
    /**
     * Street address.  For example, "901&nbsp;San Antonio Road".
     *
     * @serial
     */
    public String street;

    /**
     * Name of the company or organization that provides this service.
     * For example, "Sun Microsystems".
     *
     * @serial
     */
    public String organization;

    /**
     * The unit within the organization that provides this service.
     * For example, "Information Services".
     *
     * @serial
     */
    public String organizationalUnit;

    /**
     * City or locality name.  For example, "Palo Alto".
     *
     * @serial
     */
    public String locality;

    /**
     * Full name or standard postal abbreviation of a state or
     * province.  For example, "CA" (for California).
     *
     * @serial
     */
    public String stateOrProvince;

    /**
     * Postal code.  For example, in the United States, this is a ZIP
     * code; in Ireland, it might be either empty or a postal district
     * of Dublin.
     *
     * @serial
     */
    public String postalCode;

    /**
     * Country name.
     *
     * @serial
     */
    public String country;
}
