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

package com.sun.jini.test.share;

import java.util.logging.Level;

import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;

import com.sun.jini.constants.VersionConstants;
import com.sun.jini.lookup.entry.BasicServiceType;

import net.jini.lookup.entry.ServiceControlled;
import net.jini.lookup.entry.ServiceInfo;
import net.jini.lookup.entry.ServiceType;
import net.jini.entry.AbstractEntry;
import net.jini.core.entry.Entry;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import java.net.MalformedURLException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;

import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * This class contains a set of static methods that provide general-purpose
 * functions for processing sets of attributes; functions such as displaying
 * information about the contents of one or more attribute sets, comparing
 * the contents of two such sets, etc.
 *
 * @see com.sun.jini.qa.harness.QATestUtil
 */
public class AttributesUtil {

    private static Logger logger = 
	Logger.getLogger("com.sun.jini.qa.harness");

    /** Static convenience class for testing functionality in which 
     *  attributes must be added/modified/retrieved
     */
    public static class TestAttr00 extends AbstractEntry {
        public String val0;
        public String val1;
        public String val2;
        public TestAttr00() { }
        public TestAttr00(String val0,String val1,String val2) { 
            this.val0 = val0;
            this.val1 = val1;
            this.val2 = val2;
        }
    }//end class TestAttr00

    /** Static convenience class for testing functionality in which 
     *  ServiceControlled attributes must be added/modified/retrieved
     */
    public static class TestAttrSC00 extends AbstractEntry
                                     implements ServiceControlled
    {
        public String val0;
        public String val1;
        public TestAttrSC00() { }
        public TestAttrSC00(String val0,String val1) { 
            this.val0 = val0;
            this.val1 = val1;
        }
    }//end class TestAttrSC00

    /** Using either values contained in the configure property file, or
     *  default values, constructs and returns an instance of the attribute
     *  class <code>net.jini.lookup.entry.ServiceInfo</code>.
     * 
     *  @param qaUtil <code>com.sun.jini.qa.harness.QATestUtil</code> instance
     *                through which the pertinent values in the configuration
     *                file are retrieved
     *
     *  @return an instance of <code>net.jini.lookup.entry.ServiceInfo</code>
     *          constructed from either values contained in the configuration
     *          property file or default values defined in this method.
     */
    public static Entry getServiceInfoEntryFromConfig(QAConfig config) {
        String name = config.getStringConfigVal
                             ("net.jini.lookup.entry.ServiceInfo.name",
                              "Jini(TM) Service");
        String manufacturer = config.getStringConfigVal
                             ("net.jini.lookup.entry.ServiceInfo.manufacturer",
                              "Sun Microsystems, Inc.");
        String vendor = config.getStringConfigVal
                             ("net.jini.lookup.entry.ServiceInfo.vendor",
                              "Sun Microsystems, Inc.");
	String version = VersionConstants.SERVER_VERSION;
	try {
	    Class c = Class.forName("com.sun.jini.constants.VersionConstants");
	    Field f = c.getField("SERVER_VERSION");
	    version = (String) f.get(null);
	} catch (Exception e) {
	    logger.log(Level.SEVERE, 
		       "Failed to obtain Jini version reflectively.\n"
		     + "Using default value: " + version);
	}
        String model = config.getStringConfigVal
                             ("net.jini.lookup.entry.ServiceInfo.model",
                              "");
        String serialNumber = config.getStringConfigVal
                             ("net.jini.lookup.entry.ServiceInfo.serialNumber",
                              "");
        return new ServiceInfo(name,manufacturer,vendor,version,
                               model,serialNumber);
    }//end getServiceInfoEntryFromConfig

    /** Using either values contained in the configure property file, or
     *  default values, constructs and returns an instance of the attribute
     *  class <code>com.sun.jini.lookup.entry.BasicServiceType</code>.
     * 
     *  @param qaUtil <code>com.sun.jini.qa.harness.QATestUtil</code> instance
     *                through which the pertinent values in the configuration
     *                file are retrieved
     *
     *  @return <code>com.sun.jini.lookup.entry.BasicServiceType</code>
     *          instance constructed from either values contained in the
     *          configuration property file or default values defined in
     *          this method.
     */
    public static Entry getBasicServiceTypeFromConfig(QAConfig config) {
        String type = config.getStringConfigVal
                           ("com.sun.jini.lookup.entry.BasicServiceType.type",
                            "Jini(TM) Service");
        return new BasicServiceType(type);
    }//end getBasicServiceTypeFromConfig

    /** Using an instance of <code>com.sun.jini.qa.harness.QATestUtil</code>,
     *  displays the contents of the input array whose elements are 
     *  instances of <code>net.jini.core.entry.Entry</code>. The contents
     *  of that array will be displayed only if the QATestUtil instance is
     *  configured to display debugging output (qautil.debug=true).
     * 
     *  @param attributes       <code>Entry</code> array whose elements 
     *                          correspond to the instances that are to be
     *                          displayed
     *  @param attributeSetName identifying <code>String</code> that will be 
     *                          displayed along with the contents of the array.
     *                          This parameter can contain any value, even
     *                          <code>null</code>, but will typically be set
     *                          to the name of the array whose contents will
     *                          be displayed. If <code>null</code> is input,
     *                          the default String "Attribute Set" will be
     *                          used.
     *  @param level            the logging level
     */
    public static void displayAttributeSet(Entry[] attributes,
                                           String  attributeSetName,
					   Level   level)
    {
        if( (Level.OFF).equals(level) ) return;//logging off -- don't display
        String name = ((attributeSetName == null) ?
                                         "Attribute Set" : attributeSetName);
        if(attributes == null) {
            logger.log(level, name + " = null");
        } else if(attributes.length <= 0) {
            logger.log(level, name + " = NO_ATTRIBUTES");
        } else {
	    logger.log(level, name);
	    logger.log(level, 
		       "  -- Number of Attributes = " + attributes.length);
	    for(int i=0;i<attributes.length;i++) {
		displayAttribute(attributes[i], "", level);
	    }
	}
    }

    /** Using an instance of <code>com.sun.jini.qa.harness.QATestUtil</code>,
     *  displays the contents of the <code>attribute</code> parameter. 
     *  If the <code>attribute</code> parameter is a well-known
     *  <code>Entry</code> type, then all of its field values will be
     *  displayed; otherwise, only the parameter's string value will be
     *  displayed. Note that information about the <code>attribute</code>
     *  parameter will be displayed only if the QATestUtil instance is
     *  configured to display debugging output (qautil.debug=true).
     * 
     *  @param attribute  instance of <code>Entry</code> whose fields
     *                    string value will be displayed
     *  @param level      the logging level
     */
    public static void displayAttribute(Entry attribute, 
					String label,
					Level level) {
        if( (Level.OFF).equals(level) ) return;//logging off -- don't display
        if(attribute == null) {
            logger.log(level, "Class = null");
            return;
        }
        String classStr = (attribute.getClass()).toString();
        int indx = 1 + classStr.lastIndexOf(".");
        try {
            logger.log(level, 
		       "Class = " + classStr.substring(indx) + "  "
		     + "Loader = " + attribute.getClass().getClassLoader());
	} catch(IndexOutOfBoundsException e) {
            logger.log(level, "Class = " + classStr);
        }
        Field[] fields = getFieldInfo(attribute);
        try {
            for (int i = 0; i < fields.length; i++) {
                Field  attributeField = fields[i];
                Object attributeValue = attributeField.get(attribute);
                logger.log(level, 
			   label 
			 + "Field[" + i + "] Value = " 
			 + attributeValue);
	    }
        } catch (IllegalAccessException e) {
            // should never happen -- all fields are public
        }
    }

    /** Displays the contents of the input array whose elements are 
     *  instances of <code>net.jini.core.entry.Entry</code>.
     */
    public static void displayAttributeSet(Entry[] attributes, Level level)
    {
        displayAttributeSet(attributes, null, level);
    }

    /** Compares the contents of two sets of attributes ignoring duplicate
     *  attributes. If debugging is turned on (qautil.debug=true), this method
     *  uses an instance of <code>com.sun.jini.qa.harness.QATestUtil</code> to
     *  display information about the two sets being compared.
     *  
     *  This method should be invoked by tests that need to compare
     *  two sets of attributes for equivalent content in which duplicate
     *  attributes are unimportant.
     * 
     *  @param attributeSet1  <code>Entry</code> array containing the first
     *                        set of attribute instances that are to be
     *                        compared
     *  @param attributeSet2  <code>Entry</code> array containing the second
     *                        set of attribute instances that are to be
     *                        compared
     *  @param level          the logging level
     *
     *  @return <code>true</code> if the sets are equivalent.
     */
    public static boolean compareAttributeSets(Entry[] attributeSet1,
					       Entry[] attributeSet2,
					       Level   level)
    {
        /* Handle null */
        if( attributeSet1 == null ) {
            if ( attributeSet2 == null ) {
                return true;
            } else {
                if( !((Level.OFF).equals(level)) ) {
                    logger.log(level, 
			   "attributeSet1 = null, attributeSet2 != null");
                }//endif
		return false;
            }
        } else {//attributeSet1 != null
            if ( attributeSet2 == null ) {
                if( !((Level.OFF).equals(level)) ) {
                    logger.log(level, 
			   "attributeSet1 != null, attributeSet2 == null");
                }//endif
		return false;
            }
        }
        /* Remove any duplicates in either set of attributes */
        HashSet hashSet1 = new HashSet();
        if(attributeSet1 != null) {
            for(int i=0;i<attributeSet1.length;i++) {
                hashSet1.add(attributeSet1[i]);
            }
        }
        HashSet hashSet2 = new HashSet();
        if(attributeSet2 != null) {
            for(int i=0;i<attributeSet2.length;i++) {
                hashSet2.add(attributeSet2[i]);
            }
        }
        /* Both sets empty ? */
        if( (hashSet1.size() == 0) && (hashSet2.size() == 0) ) {
            return true;
        }
        /* Determine if the sets have the same number of elements */
        if(hashSet1.size() != hashSet2.size()) {
            if( !((Level.OFF).equals(level)) ) {
                logger.log(level, 
		       "number of attributes not equal ("
		      + hashSet1.size() + " != " + hashSet2.size() + ")");
            }//endif
	    return false;
        }
        /* Determine if the sets have the same content */
        if( !((Level.OFF).equals(level)) ) {
            logger.log(level, "");
            logger.log(level, "comparison loop");
            logger.log(level, "");
        }//endif
        int i = 0;
        for(Iterator itr1=hashSet1.iterator(); itr1.hasNext(); i++) {
            Entry attribute1 = (Entry)itr1.next();
            displayAttribute(attribute1, "attributeSet1[" + i + "]", level);
            int j = 0;
            for(Iterator itr2=hashSet2.iterator(); itr2.hasNext(); j++) {
                Entry attribute2 = (Entry)itr2.next();
                displayAttribute(attribute2, "attributeSet2[" + j + "]", level);
                if(specialCheck(attribute2, attribute1) 
		   ||  attribute2.equals(attribute1) ) {
                    hashSet2.remove(attribute2);
                    break;
                }
            }//end loop(attributeSet2)
            if( !((Level.OFF).equals(level)) )  logger.log(level, "");
        }//endloop(attributeSet1)
        /* The two sets are equal only if, after the above comparison loop,
         * there are no elements left in the second set 
         */
        if(hashSet2.size() != 0) {
            if( !((Level.OFF).equals(level)) ) {
                logger.log(level, "attributes left over from "
		           + "comparison loop, attribute sets not equal");
            }//endif
	    return false;
        }
	return true;
    }//end compareAttributeSets

    /** Returns public, non-static, non-transient, non-final fields contained
     *  in the given <code>entry</code> parameter
     */
    public static Field[] getFieldInfo(Entry entry) {
        final int SKIP_MODIFIERS
                   = (Modifier.STATIC | Modifier.TRANSIENT | Modifier.FINAL);
        /* Scan the array to see if it can be used as-is or if a smaller
         * array must be constructed because some fields must be skipped.
         * If so, then create an ArrayList and add unskippable fields to it;
         * then fetch the array back out of it.
         */
        Field[] fields = entry.getClass().getFields();
        ArrayList usable = null;
        for (int i = 0; i < fields.length; i++) {
            // exclude this one?
            if ((fields[i].getModifiers() & SKIP_MODIFIERS) != 0) {
                if (usable == null) {           //first excluded: set up for it
                    usable = new ArrayList();   //allocate the list of usables
                    for (int j = 0; j < i; j++){//earlier fields are usable
                        usable.add(fields[j]);
                    }
                }
            } else {                            // not excluded
                if (usable != null) {           // tracking usable fields?
                    usable.add(fields[i]);
                }
            }
        }
        if (usable != null) {
            fields = (Field[]) usable.toArray(new Field[usable.size()]);
        }
        return fields;
    }//end getFieldInfo

    /**
     * 'Equality' checks for attributes which may be preferred, but visible
     * to the test classpath
     */
    private static boolean specialCheck(Entry a1, Entry a2) {
	if (a1 instanceof ServiceType && a2 instanceof ServiceType) {
	    ServiceType t1 = (ServiceType) a1;
	    ServiceType t2 = (ServiceType) a2;
	    if (t1.getDisplayName() != null) {
		if (!t1.getDisplayName().equals(t2.getDisplayName())) {
		    return false;
		} 
	    } else if (t2.getDisplayName() != null) {
		    return false;
	    }
	    if (t1.getShortDescription() != null) {
		if (!t1.getShortDescription().equals(t2.getShortDescription())) {
		    return false;
		}
	    } else if (t2.getShortDescription() != null) {
		    return false;
	    }
	    return true;
	}
	return false;
    }
} //end class AttributesUtil


