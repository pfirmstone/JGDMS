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
package net.jini.lookup.ui.attribute;

import java.util.Map;
import java.util.Set;
import java.util.Collection;
import java.util.Iterator;
import java.util.Collections;
import org.apache.river.lookup.util.ConsistentMap;

/**
 * UI attribute that enables clients to get a list of
 * the fully qualified names and version numbers of packages
 * required by a UI.
 *
 * <P>
 * One or more <CODE>RequiredPackages</CODE> attributes may appear
 * in the attributes of a <CODE>UIDescriptor</CODE>. Client programs
 * interested in a UI may wish to verify that they have all required
 * packages mentioned in the <CODE>RequiredPackages</CODE> attributes
 * (if any) contained in the UI's <CODE>UIDescriptor</CODE>, before
 * they attempt to create the UI. If the client is lacking any required
 * packages (either because the entire package is absent or because the
 * package is present but of an incompatible version), the client will
 * not be able to use the UI,
 *
 * <P>
 * The intent of this attribute is to provide a quick way for a client program
 * to determine that a UI is unusable by a client, not to grant a guarantee that a UI
 * is definitely usable by the client. If a client is missing a required package,
 * or has an incompatible version of a required package, the client cannot use the UI.
 * But if the client has compatible versions of all required packages listed in a
 * <CODE>RequiredPackage</CODE> attribute, the client may or may not be able to
 * use the UI.
 *
 * <P>
 * UI providers should take bold and valiant strides to list in a <CODE>RequiredPackage</CODE>
 * attribute all known packages required of the client, so that if
 * the client discovers it has compatible versions of all listed packages and
 * attempts to generate the UI via the factory method, the client will likely
 * succeed. However, client programmers should bear in mind that a
 * <CODE>RequiredPackage</CODE> attribute doesn't necessarily list
 * <EM>all</EM> required packages. As a result, satisfying all required packages
 * doesn't absolutely guarantee the UI will work on the client.
 * As a result, client programs should program defensively.
 * (For example, clients should probably catch <CODE>LinkageError</CODE>
 * in appropriate places when dealing with UIs, even if they find they have
 * compatible versions of all required packages listed in <CODE>RequiredPackage</CODE>
 * attributes.)
 *
 * The version numbers must take the form of "specification version numbers," as used
 * by the <CODE>java.lang.Package</CODE> class:
 *
 * <BLOCKQUOTE>
 * Specification version numbers use a "Dewey Decimal" syntax that consists of positive
 * decimal integers separated by periods ".", for example, "2.0" or "1.2.3.4.5.6.7". This
 * allows an extensible number to be used to represent major, minor, micro, etc versions.
 * The version number must begin with a number.
 * </BLOCKQUOTE>
 *
 * @author Bill Venners
 */
public class RequiredPackages implements java.io.Serializable {

    private static final long serialVersionUID = 1814117871506550968L;

    /**
     * @serial A <code>Map</code> of <code>String</code> keys to
     *     <code>String</code> values.  The keys contained
     *     in the <CODE>Map</CODE> must be <CODE>String</CODE>s
     *     that represent fully qualified names of required packages.
     *     Each value contained in the <CODE>Map</CODE> must
     *     be the oldest version number of the package (defined by the
     *     key) that is compatible with the UI. Version numbers are
     *     <CODE>String</CODE>s in the form of "specification version
     *     numbers," as used by the <CODE>java.lang.Package</CODE> class.
     */
    private Map requiredPackages;

    /**
     * Constructs a <CODE>RequiredPackages</CODE> attribute
     * with the passed <CODE>Map</CODE>. The keys contained
     * in the passed <CODE>Map</CODE> must be <CODE>String</CODE>s
     * that represent fully qualified names of required packages.
     * Each value contained in the passed <CODE>Map</CODE> must
     * be the oldest version number of the package (defined by the
     * key) that is compatible with the UI. Version numbers are
     * <CODE>String</CODE>s in the form of
     * "specification version numbers," as used
     * by the <CODE>java.lang.Package</CODE> class. This constructor copies
     * the contents of the passed <code>Map</code> into a
     * serializable unmodifiable <code>Map</code> that has a
     * consistent serialized form across all VMs.
     *
     * @param packages a map of <code>String</code> fully qualified
     * names of required packages to <code>String</code> version
     * numbers
     *
     * @throws NullPointerException if <CODE>packages</CODE>
     * is <CODE>null</CODE> or if any keys or values contained
     * in <CODE>packages</CODE> are <CODE>null</CODE>.
     *
     * @throws IllegalArgumentException if any non-null key or
     * value contained in <CODE>packages</CODE> set is not an instance of
     * <CODE>java.lang.String</CODE>.
     */
    public RequiredPackages(Map packages) {

        if (packages == null) {
            throw new NullPointerException();
        }

        Set typeNames = packages.keySet();
        Iterator it = typeNames.iterator();
        while (it.hasNext()) {
            Object o = it.next();
            if (o == null) {
                throw new NullPointerException();
            }
            if (!(o instanceof String)) {
                throw new IllegalArgumentException();
            }
        }

        Collection versions = packages.values();
        it = versions.iterator();
        while (it.hasNext()) {
            Object o = it.next();
            if (o == null) {
                throw new NullPointerException();
            }
            if (!(o instanceof String)) {
                throw new IllegalArgumentException();
            }
        }

        requiredPackages = new ConsistentMap(packages);
    }

    /**
     * Returns an iterator over the set of <CODE>String</CODE>
     * fully qualified package names required
     * by the UI generated by the UI factory stored in
     * the marshalled object of the same <CODE>UIDescriptor</CODE>.
     * The returned <CODE>Iterator</CODE> does not support
     * <CODE>remove()</CODE>.
     *
     * @return an iterator over the set of <code>String</code>
     *     fully qualified names for required packages
     */
    public Iterator iterator() {

        return requiredPackages.keySet().iterator();
    }

    /**
     * Returns a version number for the required package
     * whose fully qualified package name is passed as
     * the <CODE>packageName</CODE> parameter. If the
     * passed <CODE>String</CODE> does not represent a
     * required package listed in this <CODE>RequiredPackage</CODE>
     * attribute, this method returns <CODE>null</CODE>.
     *
     * The version number returned should be a "specification version number," as used
     * by the <CODE>java.lang.Package</CODE> class:
     *
     * <BLOCKQUOTE>
     * Specification version numbers use a "Dewey Decimal" syntax that consists of positive
     * decimal integers separated by periods ".", for example, "2.0" or "1.2.3.4.5.6.7". This
     * allows an extensible number to be used to represent major, minor, micro, etc versions.
     * The version number must begin with a number.
     * </BLOCKQUOTE>
     *
     * @return the version number for the passed required package, or <code>null</code> if
     *     the passed package is not required
     * @throws NullPointerException if <CODE>packageName</CODE>
     * is <CODE>null</CODE>.
     */
    public String getVersion(String packageName) {

        if (packageName == null) {
            throw new NullPointerException();
        }

        return (String) requiredPackages.get(packageName);
    }

    /**
     * Returns a <CODE>java.util.Map</CODE> whose keys
     * are <CODE>String</CODE>s that represent fully
     * qualified names of required packages and whose
     * values are be the oldest version number of the
     * package (defined by the
     * key) that is compatible with the UI. Version numbers are
     * <CODE>String</CODE>s in the form of
     * "specification version numbers," as used
     * by the <CODE>java.lang.Package</CODE> class:
     *
     * The version numbers contained as values in the returned <CODE>Map</CODE>
     * should be a "specification version number," as used
     * by the <CODE>java.lang.Package</CODE> class:
     *
     * <BLOCKQUOTE>
     * Specification version numbers use a "Dewey Decimal" syntax that consists of positive
     * decimal integers separated by periods ".", for example, "2.0" or "1.2.3.4.5.6.7". This
     * allows an extensible number to be used to represent major, minor, micro, etc versions.
     * The version number must begin with a number.
     * </BLOCKQUOTE>
     *
     * @return an unmodifiable map of package name keys to version number values
     */
    public Map getRequiredPackages() {

        return requiredPackages;
    }

    /**
     * Compares the specified object (the <CODE>Object</CODE> passed
     * in <CODE>o</CODE>) with this <CODE>RequiredPackages</CODE>
     * object for equality. Returns true if the specified object
     * is not null, if the specified object's class is
     * <CODE>RequiredPackages</CODE>, if the two sets of
     * package-version pairs are the same size, and if every package-version pair mentioned in the
     * specified <CODE>RequiredPackages</CODE> object (passed in <CODE>o</CODE>) is also mentioned
     * in this <CODE>RequiredPackages</CODE> object.
     *
     * @param o the object to compare against
     * @return <code>true</code> if the objects are the same,
     *     <code>false</code> otherwise.
     */
    public boolean equals(Object o) {

        if (o == null) {
            return false;
        }

        if (o.getClass() != RequiredPackages.class) {
            return false;
        }

        RequiredPackages packages = (RequiredPackages) o;

        if (!requiredPackages.equals(packages.requiredPackages)) {
            return false;
        }

        return true;
    }

    /**
     * Returns the hash code value for this <CODE>RequiredPackages</CODE> object.
     *
     * @return the hashcode for this object
     */
    public int hashCode() {
        return requiredPackages.hashCode();
    }
}

