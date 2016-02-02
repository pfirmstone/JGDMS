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

import java.beans.ConstructorProperties;
import java.io.Serializable;
import net.jini.core.entry.Entry;

/**
 * A JavaBeans(TM) component that encapsulates an Address object.
 * 
 * @author Sun Microsystems, Inc.
 *
 * @see Address
 * @see Location
 * @see LocationBean
 */
public class AddressBean implements EntryBean, Serializable {
    private static final long serialVersionUID = 4491500432084550577L;

    /**
     * The Address object associated with this JavaBeans component.
     *
     * @serial
     */
    protected Address assoc;

    /**
     * Construct an instance of this JavaBeans component that is 
     * linked to a new Address object.
     */
    public AddressBean() {
	assoc = new Address();
    }
    
    @ConstructorProperties({"street", "organization", "organizationalunit",
	"locality", "stateOrProvince", "postalCode", "country"})
    public AddressBean(String street,
	    String organization,
	    String organizationalUnit,
	    String locality,
	    String stateOrProvince,
	    String postalCode,
	    String country)
    {
	assoc = new Address(street, organization, organizationalUnit, 
		locality, stateOrProvince, postalCode, country);
    }

    /**
     * Make a link to an Entry object.
     *
     * @param e the Entry object to link to
     * @throws java.lang.ClassCastException the Entry is not of the
     * correct type for this JavaBeans component
     */
    public void makeLink(Entry e) {
	assoc = (Address) e;
    }

    /**
     * Return the Entry linked to by this JavaBeans component.
     */
    public Entry followLink() {
	return assoc;
    }

    /**
     * Return the value of the street field in the Address object linked to by
     * this JavaBeans component.
     *
     * @return a <code>String</code> representing the street value 
     * @see #setStreet
     */
    public String getStreet() {
	return assoc.street;
    }

    /**
     * Set the value of the street field in the Address object linked to by
     * this JavaBeans component.
     * 
     * @param x a <code>String</code> specifying the street
     * @see #getStreet
     */
    public void setStreet(String x) {
	assoc.street = x;
    }

    /**
     * Return the value of the organization field in the Address object linked
     * to by this JavaBeans component.
     *
     * @return a <code>String</code> representing the organization 
     * @see #setOrganization
     */
    public String getOrganization() {
	return assoc.organization;
    }

    /**
     * Set the value of the organization field in the Address object linked to
     * by this JavaBeans component.
     * 
     * @param x a <code>String</code> specifying the organization
     * @see #getOrganization
     */
    public void setOrganization(String x) {
	assoc.organization = x;
    }
    
    /**
     * Return the value of the organizationalUnit field in the Address object
     * linked to by this JavaBeans component.
     *
     * @return a <code>String</code> representing the organizational unit
     * @see #setOrganizationalUnit
     */
    public String getOrganizationalUnit() {
	return assoc.organizationalUnit;
    }

    /**
     * Set the value of the organizationalUnit field in the Address object
     * linked to by this JavaBeans component.
     * 
     * @param x a <code>String</code> specifying the organizational unit
     * @see #getOrganizationalUnit
     */
    public void setOrganizationalUnit(String x) {
	assoc.organizationalUnit = x;
    }
    
    /**
     * Return the value of the locality field in the Address object linked
     * to by this JavaBeans component.
     *
     * @return a <code>String</code> representing the locality
     * @see #setLocality
     */
    public String getLocality() {
	return assoc.locality;
    }

    /**
     * Set the value of the locality field in the Address object linked to
     * by this JavaBeans component.
     * 
     * @param x a <code>String</code> specifying the locality
     * @see #getLocality
     */
    public void setLocality(String x) {
	assoc.locality = x;
    }

    /**
     * Return the value of the stateOrProvince field in the Address object
     * linked to by this JavaBeans component.
     *
     * @return a <code>String</code> representing the state or province
     * @see #setStateOrProvince
     */
    public String getStateOrProvince() {
	return assoc.stateOrProvince;
    }

    /**
     * Set the value of the stateOrProvince field in the Address object
     * linked to by this JavaBeans component.
     * 
     * @param x a <code>String</code> specifying the state or province
     * @see #getStateOrProvince
     */
    public void setStateOrProvince(String x) {
	assoc.stateOrProvince = x;
    }

    /**
     * Return the value of the postalCode field in the Address object linked
     * to by this JavaBeans component.
     *
     * @return a <code>String</code> representing the postal code
     * @see #setPostalCode
     */
    public String getPostalCode() {
	return assoc.postalCode;
    }

    /**
     * Set the value of the postalCode field in the Address object linked to
     * by this JavaBeans component.
     * 
     * @param x a <code>String</code> specifying the postal code
     * @see #getPostalCode
     */
    public void setPostalCode(String x) {
	assoc.postalCode = x;
    }

    /**
     * Return the value of the country field in the Address object linked to
     * by this JavaBeans component.
     *
     * @return a <code>String</code> representing the country
     * @see #setCountry
     */
    public String getCountry() {
	return assoc.country;
    }

    /**
     * Set the value of the country field in the Address object linked to by
     * this JavaBeans component.
     * 
     * @param x a <code>String</code> specifying the country
     * @see #getCountry
     */
    public void setCountry(String x) {
	assoc.country = x;
    }
}
