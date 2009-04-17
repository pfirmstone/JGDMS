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
import java.util.logging.Logger;

import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;

import net.jini.core.discovery.LookupLocator;

import java.net.MalformedURLException;
import java.util.HashSet;
import java.util.Iterator;

/**
 * This class contains a set of static methods that provide general-purpose
 * functions for processing sets of locators; functions such as displaying the
 * contents of one or more locator sets, comparing the contents of two such
 * sets, etc.
 *
 * @see com.sun.jini.qa.harness.QATestUtil
 */
public class LocatorsUtil {

    private static Logger logger = Logger.getLogger("com.sun.jini.qa.harness");

    /** Using an instance of <code>com.sun.jini.qa.harness.QATestUtil</code>,
     *  displays the contents of the input array whose elements are 
     *  instances of <code>net.jini.core.discovery.LookupLocator</code>.
     *  The contents of that array will be displayed only if the QATestUtil
     *  instance is configured to display debugging output (qautil.debug=true).
     * 
     *  @param locators       <code>LookupLocator</code> array containing
     *                        the locator instances that are to be displayed
     *  @param locatorSetName identifying <code>String</code> that will be 
     *                        displayed along with the contents of the array.
     *                        This parameter can contain any value, even null,
     *                        but will typically be set to the name of the
     *                        array whose contents will be displayed. If null
     *                        is input, the default String "locators" will be
     *                        used.
     *  @param level          the logging level
     */
    public static void displayLocatorSet(LookupLocator[] locators,
                                         String          locatorSetName,
                                         Level           level)
    {
        if( (Level.OFF).equals(level) ) return;//logging off -- don't display
        String name = ((locatorSetName == null) ? "locators" : locatorSetName);
        if(locators != null){
            if(locators.length == 0){
                logger.log(level, name + " = NO_LOCATORS");
            } else {
                for(int i=0;i<locators.length;i++){
                    logger.log(level, name + "["+i+"] = " + locators[i]);
                }
            }
        } else {
            logger.log(level, name + " = null");
        }
    }

    /** Displays the contents of the input array whose elements are 
     *  instances of <code>net.jini.core.discovery.LookupLocator</code>.
     */
    public static void displayLocatorSet(LookupLocator[] locators, Level level)
    {
        displayLocatorSet(locators, null, level);
    }


    /** Using an instance of <code>com.sun.jini.qa.harness.QATestUtil</code>,
     *  displays the given instance of the locator class
     *  <code>net.jini.core.discovery.LookupLocator</code>. The indicated
     *  instance of <code>LookupLocator</code> will be displayed only if
     *  the QATestUtil instance is configured to display debugging output
     *  (qautil.debug=true).
     * 
     *  @param locator        <code>LookupLocator</code> instance referencing
     *                        the locator information that is to be displayed
     *  @param locatorName    identifying <code>String</code> that will be 
     *                        displayed along with the locator information.
     *                        This parameter can contain any value, even null,
     *                        but will typically be set to the name of the
     *                        object that will be displayed. If null
     *                        is input, the default String "locator" will be
     *                        used.
     */
    public static void displayLocator(LookupLocator locator,
                                      String        locatorName,
                                      Level         level)
    {
        if( (Level.OFF).equals(level) ) return;//logging off -- don't display
        String name = ((locatorName == null) ? "locator" : locatorName);
        logger.log(level, name + " = " + locator);
    }

    /** Displays the given instance of the locator class
     *  <code>net.jini.core.discovery.LookupLocator</code>.
     */
    public static void displayLocator(LookupLocator locator, Level level)
    {
        displayLocator(locator, null, level);
    }

    /** Compares the contents of two sets of locators ignoring duplicate
     *  locators. If debugging is turned on (qautil.debug=true), this method
     *  uses an instance of <code>com.sun.jini.qa.harness.QATestUtil</code> to
     *  display information about the two sets being compared.
     *  
     *  This method should be invoked by tests that need to compare
     *  two sets of locators for equivalent content in which duplicate
     *  locators are unimportant.
     * 
     *  @param locatorSet1  <code>LookupLocator</code> array containing
     *                      the first set of locator instances that are to
     *                      be compared
     *  @param locatorSet2  <code>LookupLocator</code> array containing
     *                      the second set of locator instances that are to
     *                      be compared
     *
     *  @return <code>true</code> if the locator sets are equivalent
     */
    public static boolean compareLocatorSets(LookupLocator[] locatorSet1,
					     LookupLocator[] locatorSet2,
					     Level level)
    {
        /* Handle null */
        if( locatorSet1 == null ) {
            if ( locatorSet2 == null ) {
                return true;
            } else {
                if( !((Level.OFF).equals(level)) ) {
                    logger.log(level,
			   " -- locatorSet1 = null, locatorSet2 != null");
                }//endif
		return false;
            }
        } else {//locatorSet1 != null
            if ( locatorSet2 == null ) {
                if( !((Level.OFF).equals(level)) ) {
                    logger.log(level, 
			   " -- locatorSet1 != null, locatorSet2 == null");
                }//endif
		return false;
            }
        }
        /* Remove any duplicates in either set of locators */
        HashSet hashSet1 = new HashSet();
        if(locatorSet1 != null) {
            for(int i=0;i<locatorSet1.length;i++) {
                hashSet1.add(locatorSet1[i]);
            }
        }
        HashSet hashSet2 = new HashSet();
        if(locatorSet2 != null) {
            for(int i=0;i<locatorSet2.length;i++) {
                hashSet2.add(locatorSet2[i]);
            }
        }
        /* Both sets empty ? */
        if( (hashSet1.size() == 0) && (hashSet2.size() == 0) ) {
            return true;
        }
        /* Determine if the sets have the same number of elements */
        if(hashSet1.size() != hashSet2.size()) {
            if( !((Level.OFF).equals(level)) ) {
                logger.log(level, " -- number of locators not equal");
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
        for(Iterator itr1=hashSet1.iterator();itr1.hasNext();i++) {
            LookupLocator locator1 = (LookupLocator)itr1.next();
                int j = 0;
                for(Iterator itr2=hashSet2.iterator();itr2.hasNext();j++) {
                    LookupLocator locator2 = (LookupLocator)itr2.next();
                    if( !((Level.OFF).equals(level)) ) {
                        logger.log(level, "locatorSet1["+i+"] = " +locator1);
                        logger.log(level, "locatorSet2["+j+"] = " +locator2);
                        logger.log(level, "");
                    }//endif
                    if( locator2.equals(locator1) ) {
                        hashSet2.remove(locator2);
                        break;
                    }
                }//end loop(locatorSet2)
        }//endloop(locatorSet1)
        /* The two sets are equal only if, after the above comparison loop,
         * there are no elements left in the second set 
         */
        if(hashSet2.size() != 0) {
            if( !((Level.OFF).equals(level)) ) {
                logger.log(level, " -- locator sets not equal");
            }//endif
	    return false;
        }
	return true;
    }//end compareLocatorSets

    /** Constructs a set of locators that contains elements which duplicate
     *  other elements in the set, as well as one or more of the locators
     *  contained in the <code>locators</code> parameter. This method is
     *  useful for constructing a set of locators with which to attempt
     *  locator addition, replacement, and removal, in which the existence
     *  of duplicate elements is important.
     * 
     *  @param locators array of instances of the class
     *                  <code>net.jini.core.discovery.LookupLocator</code>
     *                  in which the set of locators returned by this
     *                  method will contain one or more of the elements
     *                  of this set
     *
     *  @throws java.net.MalformedURLException this exception occurs when
     *          while attempting to construct instances of LookupLocator
     * 
     *  @return an array of <code>net.jini.core.discovery.LookupLocator</code>
     *          in which at least one element duplicates other element(s) in
     *          the set, and at least one element duplicates at least one
     *          of the locators contained in the <code>locators</code>
     *          parameter.
     */
    public static LookupLocator[] getLocatorsWithDups(LookupLocator[] locators)
                                                 throws MalformedURLException
    {
        LookupLocator[] initLocators =
            new LookupLocator[] 
	            {QAConfig.getConstrainedLocator("jini://newHost0:5160"),
		     QAConfig.getConstrainedLocator("jini://newHost1:6161"),
		     QAConfig.getConstrainedLocator("jini://newHost2:7162"),
		     QAConfig.getConstrainedLocator("jini://newHost1"),
		     QAConfig.getConstrainedLocator("jini://newHost3:8163"),
		     QAConfig.getConstrainedLocator("jini://newHost2:7162")
		    };
        /* Construct the set of locators by concatenating the set of 
         * elements made up of every other element of the input set
         * with the initial set defined above (which contains non-unique
         * elements). In this way, the return set contains some, but not
         * all of the elements in the input set (therefore, it duplicates
         * elements from the input set); and it contains at least one
         * other element which is not unique in the return set (therefore,
         * the return set itself contains duplicate elements).
         */
        LookupLocator[] dupLocatorSet = null;
        if(locators != null){
            if(locators.length == 0){
                return initLocators;
            } else {
                int n = 1+((locators.length - 1)/2);
                dupLocatorSet = new LookupLocator[n+initLocators.length];
                for(int i=0;i<n;i++){
                    dupLocatorSet[i] = locators[2*i];
                }
                for(int i=0;i<initLocators.length;i++){
                    dupLocatorSet[i+n] = initLocators[i];
                }
                return dupLocatorSet;
            }
        } else {//locators == null
            return initLocators;
        }
    }//end getLocatorsWithDups

    /** Constructs a set of locators that is a sub-set of the 
     *  <code>locators</code> parameter. This method is useful for
     *  constructing a set of locators with which to attempt locator
     *  manipulation (in particular removal), in which a sub-set of a
     *  particular set of locators is important.
     * 
     *  @param locators array of instances of the class
     *                  <code>net.jini.core.discovery.LookupLocator</code>
     *                  in which the set of locators returned by this
     *                  method will be a sub-set of the  <code>locators</code>
     *                  parameter
     *
     *  @throws java.net.MalformedURLException this exception occurs when
     *          while attempting to construct instances of LookupLocator
     * 
     *  @return an array of <code>net.jini.core.discovery.LookupLocator</code>
     *          which is a sub-set of the set of locators referenced by the
     *          <code>locators</code> parameter.
     */
    public static LookupLocator[] getSubset(LookupLocator[] locators)
                                                 throws MalformedURLException
    {
        LookupLocator[] defaultLocators =
            new LookupLocator[] 
	              {QAConfig.getConstrainedLocator("jini://newHost0:5160"),
		       QAConfig.getConstrainedLocator("jini://newHost1:6161"),
		       QAConfig.getConstrainedLocator("jini://newHost2:7162"),
		       QAConfig.getConstrainedLocator("jini://newHost3:8163")
		      };
        /* Construct a sub-set of the input set of locators by selecting
         * every other element of that input set.
         */
        LookupLocator[] subSet = null;
        if(locators != null){
            if(locators.length == 0){
                return defaultLocators;
            } else {
                int n = 1+((locators.length - 1)/2);
                subSet = new LookupLocator[n];
                for(int i=0;i<n;i++){
                    subSet[i] = locators[2*i];
                }
            }
            return subSet;
        } else {//locators == null
            return defaultLocators;
        }
    }//end getSubset

    /** Given an index value, retrieves the port on which a lookup service
     *  corresponding to that index has been (or will be) started; then 
     *  constructs and returns the locator url using that port and the
     *  local hostname. If the index value is zero and no port item with
     *  that index exists in the resource configuration file, then
     *  this method will attempt to retrieve the prot item with no index.
     * 
     *  @param qaUtil instance of this utility class through which the port
     *                will be retrieved
     *  @param index  the index value of the particular configuration item
     *                to retrieve
     *
     *  @return String representing the locator url constructed from the
     *          retrieved port corresponding to the value of the index 
     *          parameter
     */
    public static String getLocatorUrl(QAConfig config, int index) {
        String name = "net.jini.core.lookup.ServiceRegistrar.port";
        int port = config.getIntConfigVal(name+"."+index,-1);
        if( (port == -1) && (index == 0) ) {
            port = config.getIntConfigVal(name,-1);
        }
        if(port == -1) return null;
        return "http://" + getHost(config) + ":" + port;
    }//end getLocatorUrl

    private static String getHost(QAConfig config) {
	return config.getStringConfigVal("HOST", "localhost");
    }

    /** Retrieves the port with which a lookup service corresponding to the
     *  'port' configuration item having no index has been (or will be)
     *  started. If there is no such configuration item, this method attempts
     *  the retrieval using an index value of zero.
     * 
     *  @param qaUtil instance of this utility class through which the port
     *                will be retrieved
     *
     *  @return String representing the locator url constructed from the
     *          retrieved port
     */
    public static String getLocatorUrl(QAConfig config) {
        String name = "net.jini.core.lookup.ServiceRegistrar.port";
        int port = config.getIntConfigVal(name,-1);
        if(port == -1) {
            port = config.getIntConfigVal(name+".0",-1);
        }
        if(port == -1) return null;
        return "http://" + getHost(config) + ":" + port;
    }//end getLocatorUrl

} //end class LocatorsUtil


