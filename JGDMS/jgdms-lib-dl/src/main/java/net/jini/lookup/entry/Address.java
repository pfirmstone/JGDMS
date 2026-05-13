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

import java.io.IOException;
import net.jini.core.entry.EntryWireField;
import net.jini.core.entry.GetEntryArg;
import net.jini.core.entry.PutEntryArg;
import net.jini.core.entry.SerialEntry;
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
@SerialEntry
public class Address extends AbstractEntry {
    private static final long serialVersionUID = 2896136903322046578L;

    /**
     * Returns the wire field schema for this entry class.
     *
     * @return array of wire fields in declaration order
     */
    public static EntryWireField[] entryForm() {
        return new EntryWireField[] {
            new EntryWireField("street",             String.class),
            new EntryWireField("organization",       String.class),
            new EntryWireField("organizationalUnit", String.class),
            new EntryWireField("locality",           String.class),
            new EntryWireField("stateOrProvince",    String.class),
            new EntryWireField("postalCode",         String.class),
            new EntryWireField("country",            String.class),
        };
    }

    /**
     * Deserialization constructor required by {@link SerialEntry @SerialEntry}.
     *
     * @param arg the source of field values
     * @throws IOException if a field value cannot be read or validated
     */
    public Address(GetEntryArg arg) throws IOException {
        street             = arg.get("street",             null, String.class);
        organization       = arg.get("organization",       null, String.class);
        organizationalUnit = arg.get("organizationalUnit", null, String.class);
        locality           = arg.get("locality",           null, String.class);
        stateOrProvince    = arg.get("stateOrProvince",    null, String.class);
        postalCode         = arg.get("postalCode",         null, String.class);
        country            = arg.get("country",            null, String.class);
    }

    /**
     * Serialization method required by {@link SerialEntry @SerialEntry}.
     *
     * @param arg the destination for field values
     * @param obj the instance to serialize
     * @throws IOException if a field value cannot be written
     */
    public static void serialize(PutEntryArg arg, Address obj) throws IOException {
        arg.put("street",             obj.street);
        arg.put("organization",       obj.organization);
        arg.put("organizationalUnit", obj.organizationalUnit);
        arg.put("locality",           obj.locality);
        arg.put("stateOrProvince",    obj.stateOrProvince);
        arg.put("postalCode",         obj.postalCode);
        arg.put("country",            obj.country);
        arg.writeArgs();
    }

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
