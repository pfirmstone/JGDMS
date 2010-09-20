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


import net.jini.discovery.DiscoveryGroupManagement;

import java.util.HashSet;
import java.util.Iterator;

/**
 * This class contains a set of static methods that provide general-purpose
 * functions for processing group sets; functions such as displaying the
 * contents of one or more group sets, comparing the contents of two group
 * sets, etc.
 *
 * @see net.jini.discovery.DiscoveryGroupManagement
 * @see com.sun.jini.qa.harness.QAConfig
 */
public class GroupsUtil {

    private static Logger logger = Logger.getLogger("com.sun.jini.qa.harness");

    /** Displays the contents of the input array which contains the names
     *  of a set of groups.
     * 
     *  @param groups       <code>String</code> array containing the names of
     *                      the groups to be displayed
     *  @param groupSetName identifying <code>String</code> that will be 
     *                      displayed along with the contents of the array.
     *                      This parameter can contain any value, even null,
     *                      but will typically be set to the name of the
     *                      array whose contents will be displayed. If null
     *                      is input, the default String "groups" will be used
     *  @param level        the logging level.
     */
    public static void displayGroupSet(String[] groups,
                                       String   groupSetName,
                                       Level    level)
    {
        if( (Level.OFF).equals(level) ) return;//logging off -- don't display
        String name = ((groupSetName == null) ? "groups" : groupSetName);
        if(groups != DiscoveryGroupManagement.ALL_GROUPS){
            if(groups.length == 0){
                logger.log(level, name + " = NO_GROUPS");
            } else {
                for(int i=0;i<groups.length;i++){
                    logger.log(level, name + "["+i+"] = " + groups[i]);
                }
            }
        } else {
            logger.log(level, name + " = ALL_GROUPS");
        }
    }


    /** Displays the contents of the input array which contains the names
     *  of a set of groups.
     */
    public static void displayGroupSet(String[] groups, Level level) {
        displayGroupSet(groups, null, level);
    }

    /** Compares two sets of group names ignoring duplicate names. If 
     *  debugging is turned on (qautil.debug=true), this method uses an
     *  instance of <code>com.sun.jini.qa.harness.QATestUtil</code> to
     *  display information about the two sets being compared.
     *  
     *  This method should be invoked by tests that need to compare
     *  two sets of groups for equivalent content in which duplicate names
     *  are unimportant.
     * 
     *  @param groupSet1    <code>String</code> array containing the first
     *                      set of group names to compare
     *  @param groupSet2    <code>String</code> array containing the second
     *                      set of group names to compare
     *  @param level        the logging level
     *
     *  @return <code>true</code> if the sets are equivalent
     */
    public static boolean compareGroupSets(String[] groupSet1,
					   String[] groupSet2,
					   Level    level)
    {
        /* Handle ALL_GROUPS */
        if( groupSet1 == DiscoveryGroupManagement.ALL_GROUPS ) {
            if ( groupSet2 == DiscoveryGroupManagement.ALL_GROUPS ) {
                return true;
            } else {
                if( !((Level.OFF).equals(level)) ) {
                    logger.log(level,
		      " -- groupSet1 = ALL_GROUPS, groupSet2 != ALL_GROUPS");
                }//endif
		return false;
            }
        } else {//groupSet1 != DiscoveryGroupManagement.ALL_GROUPS
            if ( groupSet2 == DiscoveryGroupManagement.ALL_GROUPS ) {
                if( !((Level.OFF).equals(level)) ) {
                    logger.log(level,
                      " -- groupSet1 != ALL_GROUPS, groupSet2 == ALL_GROUPS");
                }//endif
		return false;
            }
        }
        /* Remove any duplicates in either set of groups */
        HashSet hashSet1 = new HashSet();
        if(groupSet1 != null) {
            for(int i=0;i<groupSet1.length;i++) {
                hashSet1.add(groupSet1[i]);
            }
        }
        HashSet hashSet2 = new HashSet();
        if(groupSet2 != null) {
            for(int i=0;i<groupSet2.length;i++) {
                hashSet2.add(groupSet2[i]);
            }
        }
        /* NO_GROUPS ? */
        boolean noGroups1 = false;
        boolean noGroups2 = false;
        if(hashSet1.size() == 0)  noGroups1 = true;
        if(hashSet2.size() == 0)  noGroups2 = true;
        if(hashSet1.size() == 1) {
            String name = (String)(hashSet1.iterator()).next();
            if( ("none").compareToIgnoreCase(name) == 0 )  noGroups1 = true;
        }//endif
        if(hashSet2.size() == 1) {
            String name = (String)(hashSet2.iterator()).next();
            if( ("none").compareToIgnoreCase(name) == 0 )  noGroups2 = true;
        }//endif
        if(noGroups1 && noGroups2) return true;
        /* Determine if the sets have the same number of elements */
        if(hashSet1.size() != hashSet2.size()) {
            if( !((Level.OFF).equals(level)) ) {
                logger.log(level, " -- number of groups not equal");
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
            String group1 = (String)itr1.next();
                int j = 0;
                for(Iterator itr2=hashSet2.iterator();itr2.hasNext();j++) {
                    String group2 = (String)itr2.next();
                    if( !((Level.OFF).equals(level)) ) {
                        logger.log(level, "groupSet1["+i+"] = "+group1);
                        logger.log(level, "groupSet2["+j+"] = "+group2);
                        logger.log(level, "");
                    }//endif
                    if( group2.equals(group1) ) {
                        hashSet2.remove(group2);
                        break;
                    }
                }//end loop(groupSet2)
        }//endloop(groupSet1)
        /* The two sets are equal only if, after the above comparison loop,
         * there are no elements left in the second set 
         */
        if(hashSet2.size() != 0) {
            if( !((Level.OFF).equals(level)) ) {
                logger.log(level, " -- group sets not equal");
            }//endif
	    return false;
        }
	return true;
    }//end compareGroupSets

    /** Constructs a set of groups that contains elements which duplicate
     *  other elements in the set, as well as one or more of the groups
     *  contained in the <code>groups</code> parameter. This method is
     *  useful for constructing a set of groups with which to attempt
     *  group addition, replacement, and removal, in which the existence
     *  of duplicate elements is important.
     * 
     *  @param groups a <code>String</code> array in which the set of group
     *                names returned by this method will contain one or more
     *                of the elements of this set
     * 
     *  @return a <code>String</code> array in which at least one element
     *          duplicates other element(s) in the set, and at least one
     *          element duplicates at least one of the group names contained
     *          in the <code>groups</code> parameter.
     */
    public static String[] getGroupsWithDups(String[] groups) {
        String[] initGroups = new String[] {"newGroup0",
                                            "newGroup1",
                                            "newGroup2",
                                            "newGroup1",
                                            "newGroup3",
                                            "newGroup2"};
        /* Construct the set of groups by concatenating the set of 
         * elements made up of every other element of the input set
         * with the initial set defined above (which contains non-unique
         * elements). In this way, the return set contains some, but not
         * all of the elements in the input set (therefore, it duplicates
         * elements from the input set); and it contains at least one
         * other element which is not unique in the return set (therefore,
         * the return set itself contains duplicate elements).
         */
        String[] dupGroupSet = null;
        if(groups != DiscoveryGroupManagement.ALL_GROUPS){
            if(groups.length == 0){//DiscoveryGroupManagement.NO_GROUPS
                return initGroups;
            } else {
                int n = 1+((groups.length - 1)/2);
                dupGroupSet = new String[n+initGroups.length];
                for(int i=0;i<n;i++){
                    dupGroupSet[i] = groups[2*i];
                }
                for(int i=0;i<initGroups.length;i++){
                    dupGroupSet[i+n] = initGroups[i];
                }
                return dupGroupSet;
            }
        } else {//groups == DiscoveryGroupManagement.ALL_GROUPS
            return initGroups;
        }
    }//end getGroupsWithDups

    /** Constructs a set of groups that is a sub-set of the 
     *  <code>groups</code> parameter. This method is useful for
     *  constructing a set of groups with which to attempt group
     *  manipulation (in particular removal), in which a sub-set of a
     *  particular set of groups is important.
     * 
     *  @param groups a <code>String</code> array in which the set of group
     *                names returned by this method will be a sub-set of
     *                <code>groups</code> parameter
     * 
     *  @return a <code>String</code> array which is a sub-set of the set of
     *          group names referenced by the <code>groups</code> parameter.
     */
    public static String[] getSubset(String[] groups) {
        String[] defaultGroups = new String[] {"newGroup0",
                                               "newGroup1",
                                               "newGroup2",
                                               "newGroup3"};
        /* Construct a sub-set of the input set of groups by selecting
         * every other element of that input set.
         */
        String[] subSet = null;
        if(groups != null){
            if(groups.length == 0){
                return defaultGroups;
            } else {
                int n = 1+((groups.length - 1)/2);
                subSet = new String[n];
                for(int i=0;i<n;i++){
                    subSet[i] = groups[2*i];
                }
            }
            return subSet;
        } else {//groups == null
            return defaultGroups;
        }
    }//end getSubset

    /** Given an index value, retrieves the set of member groups with which
     *  a lookup service corresponding to that index has been (or will be)
     *  started. If the index value is zero and no member group configuration
     *  item with that index exists in the resource configuration file, then
     *  this method will attempt to retrieve the member groups item with
     *  no index.
     * 
     *  @param config instance of QAConfig class through which the member
     *                groups will be retrieved
     *  @param index  the index value of the particular configuration item
     *                to retrieve
     *
     *  @return String array containing the names or the groups to which the
     *          lookup service corresponding to the value of the index 
     *          parameter belongs
     */
    public static String[] getMemberGroups(QAConfig config, int index) {
        return config.parseString(getMemberGroupsStr(config,index),",");
    }//end getMemberGroups

    /** Retrieves the set of member groups with which a lookup service
     *  corresponding to the 'membergroups' configuration item having no
     *  index has been (or will be) started. If there is no such configuration
     *  item, this method attempts the retrieval using an index value of
     *  zero.
     * 
     *  @param config instance of the QAConfig class through which the member
     *                groups will be retrieved
     *
     *  @return String array containing the names or the groups to which the
     *          lookup service belonging to the indicated member groups
     *          belongs
     */
    public static String[] getMemberGroups(QAConfig config) {
        return config.parseString(getMemberGroupsStr(config),",");
    }//end getMemberGroups

    /** Given an index value, retrieves the configuration string that 
     *  identifies the set of member groups with which a lookup service
     *  corresponding to that index has been (or will be) started. If the
     *  index value is zero and no member group configuration item with
     *  that index exists in the resource configuration file, then
     *  this method will attempt to retrieve the member groups item with
     *  no index.
     * 
     *  @param config instance of the QAConfig class through which the member
     *                groups will be retrieved
     *  @param index  the index value of the particular configuration item
     *                to retrieve
     *
     *  @return comma-separaged String containing the names of the groups in
     *          which the lookup service corresponding - to the value of the
     *          index parameter - is a member
     */
    public static String getMemberGroupsStr(QAConfig config, int index) {
        String name = "net.jini.core.lookup.ServiceRegistrar.membergroups";
        String str = config.getStringConfigVal(name+"."+index,null);
        if( (str == null) && (index == 0) ) {
            str = config.getStringConfigVal(name,null);
        }
        return str;
    }//end getMemberGroupsStr

    /** Retrieves the configuration string - having no index - that identifies
     *  the set of member groups with which the corresponding lookup service
     *  has been (or will be) started. If there is no such configuration
     *  item, this method attempts the retrieval using an index value of
     *  zero.
     * 
     *  @param config instance of the QAConfig class through which the member
     *                groups will be retrieved
     * 
     *  @return comma-separaged String containing the names of the groups in
     *          which the appropriate lookup service is a member
     */
    public static String getMemberGroupsStr(QAConfig config) {
        String name = "net.jini.core.lookup.ServiceRegistrar.membergroups";
        String str = config.getStringConfigVal(name,null);
        if(str == null) {
            str = config.getStringConfigVal(name+".0",null);
        }
        return str;
    }//end getMemberGroupsStr

    /** Converts a <code>String</code> array to a comma-separated 
     *  <code>String</code> in which each token corresponds to an element
     *  of the input array.
     * 
     *  @param strs the <code>String</code> array to convert
     *
     *  @return comma-separaged <code>String</code> in which each token
     *          corresponds to an element of the input <code>String</code>
     *          array
     */
    public static String toCommaSeparatedStr(String[] strs) {
        if(strs == null) return null;
        if(strs.length == 0) return new String("");
        StringBuffer strBuf = new StringBuffer(strs[0]);
        for(int i=1;i<strs.length;i++) {
            strBuf = strBuf.append(","+strs[i]);
        }
        return strBuf.toString();
    }//end toCommaSeparatedStr

} //end class GroupsUtil
