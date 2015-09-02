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
package org.apache.river.test.spec.lookupservice.test_set01;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;

import java.util.logging.Level;
import org.apache.river.qa.harness.TestException;

import org.apache.river.test.spec.lookupservice.QATestRegistrar;
import org.apache.river.test.spec.lookupservice.QATestUtils;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.core.lookup.ServiceItem;
import net.jini.core.lookup.ServiceRegistration;
import net.jini.core.lookup.ServiceTemplate;
import net.jini.core.entry.Entry;
import java.rmi.RemoteException;

/** This class is used to verify that the method getFieldValues() operates
 *  as expected when a template containing only initialized attributes 
 *  (non-null fields) is input.
 *
 *  In particular, this test wishes to verify the following statement from
 *  the lookup specification:
 *
 *    "The getFieldValues method looks at all service items that match the
 *     specified template, finds every entry (among those service items) 
 *     that matches tmpl.attributeSetTemplates[setIndex], and returns
 *     the set of values of the specified field of those entries. ... Null
 *     (not an empty array) is returned if there are no matching items.
 *  
 *  The difference between this test class and the other class that
 *  tests the getFieldValues() method is in the template that is used for
 *  matching the registered services. This class will input a template 
 *  containing attributes that are initialized; that is, attributes that
 *  contain non-null fields. Whereas the other test class will employ
 *  a template containing non-initialized attributes.
 * 
 *  To perform this test, 1 instance of 1 of the test service classes
 *  is created and registered with the lookup service; that service is
 *  registered with a set of initialized attributes. A template is
 *  created containing all of the initialized attributes. For each 
 *  initialized attribute contained by the service, and for each field 
 *  of each attribute, getFieldValues() is called with the template 
 *  as input argument. The array of field value objects that is returned 
 *  is then compared against the expected set of field value objects. 
 *
 *  @see org.apache.river.qa.harness.TestEnvironment
 *  @see org.apache.river.test.spec.lookupservice.QATestRegistrar
 *  @see org.apache.river.test.spec.lookupservice.QATestUtils
 */
public class GetFieldValuesAttr extends QATestRegistrar {

    private ServiceItem srvcItem ;
    private ServiceRegistration srvcReg ;
    private ServiceTemplate emptyTmpl;
    private ServiceRegistrar proxy;

    private Entry[] attrEntries;
    private ServiceTemplate tmpl;

    private String[][] fieldStr
    = { {""},
        {""},
        {""},
        {"i0_03","b0_03","s0_03"},
        {"i0_04","b0_04","s0_04"},
        {"i0_05","b0_05","s0_05","l0_05"},
        {"i0_06","b0_06","s0_06"},
        {"i0_07","b0_07","s0_07","l0_07","i1_07","b1_07","s1_07","l1_07"},

        {"i0_08","b0_08","s0_08","l0_08",
         "i1_08","b1_08","s1_08","l1_08",
         "i1_08","b1_08","s1_08","l1_08"},

        {"i0_09","b0_09","s0_09","l0_09"},
        {"i0_10","b0_10","s0_10","i1_10","b1_10","s1_10"},

        {"i0_11","b0_11","s0_11","l0_11",
         "i1_11","b1_11","s1_11","l1_11",
         "i2_11","b2_11","s2_11","l2_11"},

        {"i0_12","b0_12","s0_12","l0_12",
         "i1_12","b1_12","s1_12","l1_12",
         "i2_12","b2_12","s2_12","l2_12",
         "i3_12","b3_12","s3_12","l3_12"}
    };

    /** Performs actions necessary to prepare for execution of the 
     *  current QA test.
     *
     *  Creates the lookup service. Retrieves the proxies to the lookup 
     *  service. Loads each of the attribute classes and creates an initialized
     *  instance (non-null fields) of each loaded class. Creates a template
     *  containing only the initialized attributes.  Loads one of the 
     *  service classes and creates an instance of it containing all of the 
     *  initialized attributes; registers that service class with the lookup 
     *  service.
     *  @exception QATestException will usually indicate an "unresolved"
     *  condition because at this point the test has not yet begun.
     */
    public Test construct(QAConfig sysConfig) throws Exception {
        /* create the lookup service */
	super.construct(sysConfig);
        /* retrieve the proxies to the lookup service */
	proxy = super.getProxy();
        /* load each of the attribute classes and create an initialized
         * instance (non-null fields) of each loaded class; create a
         * ServiceTemplate containing the attributes just created
         */
        attrEntries = super.createNonNullFieldAttributes();
	tmpl = new ServiceTemplate(null,null,attrEntries);
        /* load one of the service classes and create an instance of it
         * containing all of the initialized attributes created above;
         * register that service classe with the lookup service
         */
        srvcItem = super.createServiceItem(TEST_SRVC_CLASSES[0],0,attrEntries);
        srvcReg  = registerItem(srvcItem,Long.MAX_VALUE, proxy);
        return this;
    }

    /** Performs actions necessary to prepare for execution of the 
     *  current QA test.
     *
     *  For each attribute belonging to the service registered with lookup and
     *     for each field value of the attribute:
     *       1. Calls the method getFieldValues() inputting the template
     *          created during construct.
     *       2. Verifies that getFieldValues() returns the expected results.
     *  @exception QATestException usually indicates test failure
     */
    public void run() throws Exception {
	Object[] fieldValues = null;
	for(int i=0;i<fieldStr.length;i++) {
	    for(int j=0;j<fieldStr[i].length;j++) {
		try {
	            fieldValues = proxy.getFieldValues(tmpl,i,fieldStr[i][j]);
	            if (!(fieldValuesEqualExpected(fieldValues,i,j)))
		    {
	                throw new TestException
	                            ("For Attr["+i+"], Field ("+fieldStr[i][j]+
	                                                "): Unexpected Value");
		    }
		} catch (NoSuchFieldException e) {  // is this necessary??
	            if (!(fieldValuesEqualExpected(fieldValues,i,j)))
		    {
	                throw new TestException
	                            ("For Attr["+i+"], Field ("+fieldStr[i][j]+
	                                                "): Unexpected Value");
		    }
	   	}
	    }
	}
    }

    /* determine the current attribute type, then verify the fieldValue
     * based on that type
     */
    private boolean fieldValuesEqualExpected(Object[] fieldValues,
                                             int attrIndx,
                                             int fieldIndx) {
        switch(attrIndx) {
	    case 0:
	    case 1:
	    case 2:
                if (fieldValues == null) {return true;} else {return false;}
	    case 3:
                return a03(fieldValues,fieldIndx);
	    case 4:
                return a04(fieldValues,fieldIndx);
	    case 5:
                return a05(fieldValues,fieldIndx);
	    case 6:
                return a06(fieldValues,fieldIndx);
	    case 7:
                return a07(fieldValues,fieldIndx);
	    case 8:
                return a08(fieldValues,fieldIndx);
	    case 9:
                return a09(fieldValues,fieldIndx);
	    case 10:
                return a10(fieldValues,fieldIndx);
	    case 11:
                return a11(fieldValues,fieldIndx);
	    case 12:
                return a12(fieldValues,fieldIndx);
	    default: return false;
	}
    }

    /* verify the current set of fieldValues for attribute class Attr03 */
    private boolean a03(Object[] fieldValues,int fieldIndx) {
        if (fieldValues == null) return false;
        Integer[] i0 = {new Integer(300)};
        Boolean[] b0 = {new Boolean(true)};
        String[]  s0 = {new String("attribute string 302")};
        switch(fieldIndx) {
	    case 0:
                if (i0.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(i0[i].equals((Integer)fieldValues[i]))) return false;
		}
                return true;
	    case 1:
                if (b0.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(b0[i].equals((Boolean)fieldValues[i]))) return false;
		}
                return true;
	    case 2:
                if (s0.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(s0[i].equals((String)fieldValues[i]))) return false;
		}
                return true;
	    case 3:
                return false;
	    default: return false;
	}
    }
    /* verify the current set of fieldValues for attribute class Attr04 */
    private boolean a04(Object[] fieldValues,int fieldIndx) {
        if (fieldValues == null) return false;
        Integer[] i0 = {new Integer(400)};
        Boolean[] b0 = {new Boolean(true)};
        String[]  s0 = {new String("attribute string 402")};
        switch(fieldIndx) {
	    case 0:
                if (i0.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(i0[i].equals((Integer)fieldValues[i]))) return false;
		}
                return true;
	    case 1:
                if (b0.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(b0[i].equals((Boolean)fieldValues[i]))) return false;
		}
                return true;
	    case 2:
                if (s0.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(s0[i].equals((String)fieldValues[i]))) return false;
		}
                return true;
	    case 3:
                return false;
	    default: return false;
	}
    }
    /* verify the current set of fieldValues for attribute class Attr05 */
    private boolean a05(Object[] fieldValues,int fieldIndx) {
        if (fieldValues == null) return false;
        Integer[] i0 = {new Integer(500)};
        Boolean[] b0 = {new Boolean(true)};
        String[]  s0 = {new String("attribute string 502")};
        Long[]    l0 = {new Long(503)};
        switch(fieldIndx) {
	    case 0:
                if (i0.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(i0[i].equals((Integer)fieldValues[i]))) return false;
		}
                return true;
	    case 1:
                if (b0.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(b0[i].equals((Boolean)fieldValues[i]))) return false;
		}
                return true;
	    case 2:
                if (s0.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(s0[i].equals((String)fieldValues[i]))) return false;
		}
                return true;
	    case 3:
                if (l0.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(l0[i].equals((Long)fieldValues[i]))) return false;
		}
                return true;
	    default: return false;
	}
    }
    /* verify the current set of fieldValues for attribute class Attr06 */
    private boolean a06(Object[] fieldValues,int fieldIndx) {
        if (fieldValues == null) return false;
        Integer[] i0 = {new Integer(600)};
        Boolean[] b0 = {new Boolean(true)};
        String[]  s0 = {new String("attribute string 602")};
        switch(fieldIndx) {
	    case 0:
                if (i0.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(i0[i].equals((Integer)fieldValues[i]))) return false;
		}
                return true;
	    case 1:
                if (b0.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(b0[i].equals((Boolean)fieldValues[i]))) return false;
		}
                return true;
	    case 2:
                if (s0.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(s0[i].equals((String)fieldValues[i]))) return false;
		}
                return true;
	    case 3:
                return true;
	    default: return false;
	}
    }
    /* verify the current set of fieldValues for attribute class Attr07 */
    private boolean a07(Object[] fieldValues,int fieldIndx) {
        if (fieldValues == null) return false;
        Integer[] i0 = {new Integer(700)};
        Boolean[] b0 = {new Boolean(true)};
        String[]  s0 = {new String("attribute string 702")};
        Long[]    l0 = {new Long(703)};
        Integer[] i1 = {new Integer(710)};
        Boolean[] b1 = {new Boolean(true)};
        String[]  s1 = {new String("attribute string 712")};
        Long[]    l1 = {new Long(713)};
        switch(fieldIndx) {
	    case 0:
                if (i0.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(i0[i].equals((Integer)fieldValues[i]))) return false;
		}
                return true;
	    case 1:
                if (b0.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(b0[i].equals((Boolean)fieldValues[i]))) return false;
		}
                return true;
	    case 2:
                if (s0.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(s0[i].equals((String)fieldValues[i]))) return false;
		}
                return true;
	    case 3:
                if (l0.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(l0[i].equals((Long)fieldValues[i]))) return false;
		}
                return true;
	    case 4:
                if (i1.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(i1[i].equals((Integer)fieldValues[i]))) return false;
		}
                return true;
	    case 5:
                if (b1.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(b1[i].equals((Boolean)fieldValues[i]))) return false;
		}
                return true;
	    case 6:
                if (s1.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(s1[i].equals((String)fieldValues[i]))) return false;
		}
                return true;
	    case 7:
                if (l1.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(l1[i].equals((Long)fieldValues[i]))) return false;
		}
                return true;
	    default: return false;
	}
    }
    /* verify the current set of fieldValues for attribute class Attr08 */
    private boolean a08(Object[] fieldValues,int fieldIndx) {
        if (fieldValues == null) return false;
        Integer[] i0 = {new Integer(800)};
        Boolean[] b0 = {new Boolean(true)};
        String[]  s0 = {new String("attribute string 802")};
        Long[]    l0 = {new Long(803)};
        Integer[] i1 = {new Integer(810)};
        Boolean[] b1 = {new Boolean(true)};
        String[]  s1 = {new String("attribute string 812")};
        Long[]    l1 = {new Long(813)};
        Integer[] i2 = {new Integer(810)};
        Boolean[] b2 = {new Boolean(true)};
        String[]  s2 = {new String("attribute string 812")};
        Long[]    l2 = {new Long(813)};
        switch(fieldIndx) {
	    case 0:
                if (i0.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(i0[i].equals((Integer)fieldValues[i]))) return false;
		}
                return true;
	    case 1:
                if (b0.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(b0[i].equals((Boolean)fieldValues[i]))) return false;
		}
                return true;
	    case 2:
                if (s0.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(s0[i].equals((String)fieldValues[i]))) return false;
		}
                return true;
	    case 3:
                if (l0.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(l0[i].equals((Long)fieldValues[i]))) return false;
		}
                return true;
	    case 4:
                if (i1.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(i1[i].equals((Integer)fieldValues[i]))) return false;
		}
                return true;
	    case 5:
                if (b1.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(b1[i].equals((Boolean)fieldValues[i]))) return false;
		}
                return true;
	    case 6:
                if (s1.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(s1[i].equals((String)fieldValues[i]))) return false;
		}
                return true;
	    case 7:
                if (l1.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(l1[i].equals((Long)fieldValues[i]))) return false;
		}
                return true;
	    case 8:
                if (i1.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(i1[i].equals((Integer)fieldValues[i]))) return false;
		}
                return true;
	    case 9:
                if (b1.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(b1[i].equals((Boolean)fieldValues[i]))) return false;
		}
                return true;
	    case 10:
                if (s1.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(s1[i].equals((String)fieldValues[i]))) return false;
		}
                return true;
	    case 11:
                if (l1.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(l1[i].equals((Long)fieldValues[i]))) return false;
		}
                return true;
	    default: return false;
	}
    }
    /* verify the current set of fieldValues for attribute class Attr09 */
    private boolean a09(Object[] fieldValues,int fieldIndx) {
        if (fieldValues == null) return false;
        Integer[] i0 = {new Integer(900)};
        Boolean[] b0 = {new Boolean(true)};
        String[]  s0 = {new String("attribute string 902")};
        Long[]    l0 = {new Long(903)};
        switch(fieldIndx) {
	    case 0:
                if (i0.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(i0[i].equals((Integer)fieldValues[i]))) return false;
		}
                return true;
	    case 1:
                if (b0.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(b0[i].equals((Boolean)fieldValues[i]))) return false;
		}
                return true;
	    case 2:
                if (s0.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(s0[i].equals((String)fieldValues[i]))) return false;
		}
                return true;
	    case 3:
                if (l0.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(l0[i].equals((Long)fieldValues[i]))) return false;
		}
                return true;
	    default: return false;
	}
    }
    /* verify the current set of fieldValues for attribute class Attr10 */
    private boolean a10(Object[] fieldValues,int fieldIndx) {
        if (fieldValues == null) return false;
        Integer[] i0 = {new Integer(1000)};
        Boolean[] b0 = {new Boolean(true)};
        String[]  s0 = {new String("attribute string 1002")};
        Integer[] i1 = {new Integer(1010)};
        Boolean[] b1 = {new Boolean(true)};
        String[]  s1 = {new String("attribute string 1012")};
        switch(fieldIndx) {
	    case 0:
                if (i0.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(i0[i].equals((Integer)fieldValues[i]))) return false;
		}
                return true;
	    case 1:
                if (b0.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(b0[i].equals((Boolean)fieldValues[i]))) return false;
		}
                return true;
	    case 2:
                if (s0.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(s0[i].equals((String)fieldValues[i]))) return false;
		}
                return true;
	    case 3:
                if (i1.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(i1[i].equals((Integer)fieldValues[i]))) return false;
		}
                return true;
	    case 4:
                if (b1.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(b1[i].equals((Boolean)fieldValues[i]))) return false;
		}
                return true;
	    case 5:
                if (s1.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(s1[i].equals((String)fieldValues[i]))) return false;
		}
                return true;
	    default: return false;
	}
    }
    /* verify the current set of fieldValues for attribute class Attr11 */
    private boolean a11(Object[] fieldValues,int fieldIndx) {
        if (fieldValues == null) return false;
        Integer[] i0 = {new Integer(1100)};
        Boolean[] b0 = {new Boolean(true)};
        String[]  s0 = {new String("attribute string 1102")};
        Long[]    l0 = {new Long(1103)};
        Integer[] i1 = {new Integer(1110)};
        Boolean[] b1 = {new Boolean(true)};
        String[]  s1 = {new String("attribute string 1112")};
        Long[]    l1 = {new Long(1113)};
        Integer[] i2 = {new Integer(1120)};
        Boolean[] b2 = {new Boolean(true)};
        String[]  s2 = {new String("attribute string 1122")};
        Long[]    l2 = {new Long(1123)};
        switch(fieldIndx) {
	    case 0:
                if (i0.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(i0[i].equals((Integer)fieldValues[i]))) return false;
		}
                return true;
	    case 1:
                if (b0.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(b0[i].equals((Boolean)fieldValues[i]))) return false;
		}
                return true;
	    case 2:
                if (s0.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(s0[i].equals((String)fieldValues[i]))) return false;
		}
                return true;
	    case 3:
                if (l0.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(l0[i].equals((Long)fieldValues[i]))) return false;
		}
                return true;
	    case 4:
                if (i1.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(i1[i].equals((Integer)fieldValues[i]))) return false;
		}
                return true;
	    case 5:
                if (b1.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(b1[i].equals((Boolean)fieldValues[i]))) return false;
		}
                return true;
	    case 6:
                if (s1.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(s1[i].equals((String)fieldValues[i]))) return false;
		}
                return true;
	    case 7:
                if (l1.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(l1[i].equals((Long)fieldValues[i]))) return false;
		}
                return true;
	    case 8:
                if (i2.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(i2[i].equals((Integer)fieldValues[i]))) return false;
		}
                return true;
	    case 9:
                if (b2.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(b2[i].equals((Boolean)fieldValues[i]))) return false;
		}
                return true;
	    case 10:
                if (s2.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(s2[i].equals((String)fieldValues[i]))) return false;
		}
                return true;
	    case 11:
                if (l2.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(l2[i].equals((Long)fieldValues[i]))) return false;
		}
                return true;
	    default: return false;
	}
    }
    /* verify the current set of fieldValues for attribute class Attr12 */
    private boolean a12(Object[] fieldValues,int fieldIndx) {
        if (fieldValues == null) return false;
        Integer[] i0 = {new Integer(1200)};
        Boolean[] b0 = {new Boolean(true)};
        String[]  s0 = {new String("attribute string 1202")};
        Long[]    l0 = {new Long(1203)};
        Integer[] i1 = {new Integer(1210)};
        Boolean[] b1 = {new Boolean(true)};
        String[]  s1 = {new String("attribute string 1212")};
        Long[]    l1 = {new Long(1213)};
        Integer[] i2 = {new Integer(1220)};
        Boolean[] b2 = {new Boolean(true)};
        String[]  s2 = {new String("attribute string 1222")};
        Long[]    l2 = {new Long(1223)};
        Integer[] i3 = {new Integer(1230)};
        Boolean[] b3 = {new Boolean(true)};
        String[]  s3 = {new String("attribute string 1232")};
        Long[]    l3 = {new Long(1233)};
        switch(fieldIndx) {
	    case 0:
                if (i0.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(i0[i].equals((Integer)fieldValues[i]))) return false;
		}
                return true;
	    case 1:
                if (b0.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(b0[i].equals((Boolean)fieldValues[i]))) return false;
		}
                return true;
	    case 2:
                if (s0.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(s0[i].equals((String)fieldValues[i]))) return false;
		}
                return true;
	    case 3:
                if (l0.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(l0[i].equals((Long)fieldValues[i]))) return false;
		}
                return true;
	    case 4:
                if (i1.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(i1[i].equals((Integer)fieldValues[i]))) return false;
		}
                return true;
	    case 5:
                if (b1.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(b1[i].equals((Boolean)fieldValues[i]))) return false;
		}
                return true;
	    case 6:
                if (s1.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(s1[i].equals((String)fieldValues[i]))) return false;
		}
                return true;
	    case 7:
                if (l1.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(l1[i].equals((Long)fieldValues[i]))) return false;
		}
                return true;
	    case 8:
                if (i2.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(i2[i].equals((Integer)fieldValues[i]))) return false;
		}
                return true;
	    case 9:
                if (b2.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(b2[i].equals((Boolean)fieldValues[i]))) return false;
		}
                return true;
	    case 10:
                if (s2.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(s2[i].equals((String)fieldValues[i]))) return false;
		}
                return true;
	    case 11:
                if (l2.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(l2[i].equals((Long)fieldValues[i]))) return false;
		}
                return true;
	    case 12:
                if (i3.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(i3[i].equals((Integer)fieldValues[i]))) return false;
		}
                return true;
	    case 13:
                if (b3.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(b3[i].equals((Boolean)fieldValues[i]))) return false;
		}
                return true;
	    case 14:
                if (s3.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(s3[i].equals((String)fieldValues[i]))) return false;
		}
                return true;
	    case 15:
                if (l3.length != fieldValues.length) return false;
	        for(int i=0;i<fieldValues.length;i++) {
                    if (!(l3[i].equals((Long)fieldValues[i]))) return false;
		}
                return true;
	    default: return false;
	}
    }
}
