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
package com.sun.jini.test.spec.lookupservice;

import com.sun.jini.test.spec.lookupservice.attribute.Attr;
import com.sun.jini.test.spec.lookupservice.ServiceLeaseOverrideProvider;
import net.jini.core.lookup.ServiceEvent;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.core.lookup.ServiceRegistration;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceItem;
import net.jini.core.lookup.ServiceMatches;
import net.jini.core.lookup.ServiceTemplate;
import net.jini.core.entry.Entry;
import net.jini.lookup.entry.ServiceType;
import net.jini.core.lease.*;
import java.rmi.RemoteException;
import java.io.Serializable;
import java.io.IOException;
import java.util.Vector;
import java.util.HashMap;
import java.util.ArrayList;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;

import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QATestEnvironment;

import java.util.logging.Logger;
import java.util.logging.Level;

/** Provides useful constants and utility methods and classes for all 
 *  classes of the Lookup component of the Jini System. Each field and 
 *  member class, and most methods, contained in this class is public 
 *  and static; this class does not need to be instantiated.
 *
 *  @see QATestEnvironment
 *  @see com.sun.jini.test.spec.lookupservice.QATestRegistrar
 */
public class QATestUtils {

    private static Logger logger = 
	Logger.getLogger("com.sun.jini.qa.harness.test");

    /* Class constructor */
    public QATestUtils(){}

    /** The number of milliseconds in 1 second */
    public final static long N_MS_PER_SEC   = 1000;
    /** The number of seconds in 1 minute */
    public final static long N_SECS_PER_MIN = 60;
    /** The number of milliseconds in 1 minute */
    public final static long N_MS_PER_MIN   = N_MS_PER_SEC*N_SECS_PER_MIN;
    /** Strings describing event transition states used in debugging */
    public final static String[] trans_str = {"UNKNOWN",
                                              "MATCH_NOMATCH",
                                              "NOMATCH_MATCH",
                                              "UNKNOWN",
                                              "MATCH_MATCH"};

    /** Class which models an event "tuple" (service,attribute,transition). 
     *  
     *  This class is used by some of the tests that wish to verify that the 
     *  events received from the lookup service are the events expected; 
     *  based on the templates used to register event notification requests.
     */
    public static class SrvcAttrTuple implements Serializable
    {
        static final long serialVersionUID = -8254953323094761933L;

        /** @serial */
        private Object srvcObj;
        /** @serial */
        private Object attrObj;
        /** @serial */
        private int transition;
        /** @serial */
        private ServiceItem[] srvcItems ;
        /** @serial */
        private Entry[][] attrs;

        /** Creates a SrvcAttrTuple with the given transition value.
         *  @param srvcItems the array of registered service items
         *  @param attrs array of Entry type elements containing attributes
         *  @param srvcObj the service component of the new tuple
         *  @param attrObj the attribute component of the new tuple
         *  @param transition the transition component of the new tuple
         */
        public SrvcAttrTuple(ServiceItem[] srvcItems,
                             Entry[][]     attrs,
                             Object        srvcObj,
                             Object        attrObj,
                             int           transition)
        {
            this.srvcItems  = srvcItems;
            this.attrs      = attrs;

            this.srvcObj    = srvcObj;
            this.attrObj    = attrObj;
            this.transition = transition;
        }

        /** Creates a SrvcAttrTuple with the "unknown" (0) transition value.
         *  @param srvcItems the array of registered service items
         *  @param attrs array of Entry type elements containing attributes
         *  @param srvcObj the service component of the new tuple
         *  @param attrObj the attribute component of the new tuple
         */
        public SrvcAttrTuple(ServiceItem[] srvcItems,
                             Entry[][]     attrs,
                             Object        srvcObj,
                             Object        attrObj)
        {
            this.srvcItems  = srvcItems;
            this.attrs      = attrs;

            this.srvcObj    = srvcObj;
            this.attrObj    = attrObj;
            this.transition = 0;
        }

        /** Creates a SrvcAttrTuple with the given transition value
         *  and null reference arrays of service items and attributes.
         *  @param srvcObj the service component of the new tuple
         *  @param attrObj the attribute component of the new tuple
         *  @param transition the transition component of the new tuple
         */
        public SrvcAttrTuple(Object srvcObj,
                             Object attrObj,
                             int    transition)
        {
            this.srvcItems  = null;
            this.attrs      = null;

            this.srvcObj    = srvcObj;
            this.attrObj    = attrObj;
            this.transition = transition;
        }

        /** Creates a SrvcAttrTuple with the "unknown" (0) transition value
         *  and null reference arrays of service items and attributes.
         *  @param srvcObj the service component of the new tuple
         *  @param attrObj the attribute component of the new tuple
         */
        public SrvcAttrTuple(Object srvcObj,
                             Object attrObj)
        {
            this.srvcItems  = null;
            this.attrs      = null;

            this.srvcObj    = srvcObj;
            this.attrObj    = attrObj;
            this.transition = 0;
        }

        /** Sets this tuple equal to the given tuple
         *  @param tuple the SrvcAttrTuple to set this tuple equal to
         */
        public void setEqualTo(SrvcAttrTuple tuple) {
            this.srvcItems  = tuple.srvcItems;
            this.attrs      = tuple.attrs;
            this.srvcObj    = tuple.srvcObj;
            this.attrObj    = tuple.attrObj;
            this.transition = tuple.transition;
        }

        /** Returns the array of service items associated with this tuple */
        public ServiceItem[] getSrvcItems() {
            return srvcItems;
        }

        /** Sets the reference to the array of service items associated with
         *  this tuple equal to the given reference
         *  @param srvcItems the new array of service items
         */
        public void setSrvcItems(ServiceItem[] srvcItems) {
            this.srvcItems = srvcItems;
        }

        /** Returns the array of attributes associated with this tuple */
        public Entry[][] getAttrs() {
            return attrs;
        }

        /** Sets the reference to the array of attributes associated with
         *  this tuple equal to the given reference
         *  @param attrs the new array-of-arrays of attributes
         */
        public void setAttrs(Entry[][] attrs) {
            this.attrs = attrs;
        }

        /** Returns the service item associated with this tuple */
        public Object getSrvcObj() {
            return srvcObj;
        }

        /** Sets the reference to the service item associated with
         *  this tuple equal to the given reference
         *  @param srvcObj the new service item
         */
        public void setSrvcObj(Object srvcObj) {
            this.srvcObj = srvcObj;
        }

        /** Returns the attribute associated with this tuple */
        public Object getAttrObj() {
            return attrObj;
        }

        /** Sets the reference to the attribute associated with
         *  this tuple equal to the given reference
         *  @param attrObj the new attribute
         */
        public void setAttrObj(Object attrObj) {
            this.attrObj = attrObj;
        }

        /** Returns the transition associated with this tuple */
        public int getTransition() {
            return transition;
        }

        /** Sets the transition associated with this tuple equal to the 
         *  given transition
         *  @param transition the new transition value
         */
        public void setTransition(int transition) {
            this.transition = transition;
        }

        /** Determines if the given object is equal to this tuple; where
         *  two tuples are equal if their service items are equal, their
         *  attributes are equal and their transitions are equal.
         *  @param obj the tuple to compare to this tuple
         *  @return boolean
         */
        public boolean equals(Object obj) {
            int trans = ((SrvcAttrTuple)obj).getTransition();
            return ( (objEquals(obj)) && (this.transition == trans) );
        }

        /** Determines if the service item and and the attribute of the 
         *  given object are equal to the corresponding fields of this 
         *  tuple; that is, this method ignores the transition values
         *  of the tuples.
         *  @param obj the tuple to compare to this tuple
         *  @return boolean
         */
        public boolean objEquals(Object obj) {
            if (this == obj) {
                return true;
            } else if ( (obj.getClass()).equals(SrvcAttrTuple.class) ) {
                if ((srvcItems == null)||(attrs == null)) {
                    return false;
		}
                int n=0;
                int k=0;
                Object srvc = ((SrvcAttrTuple)obj).getSrvcObj();
                Object attr = ((SrvcAttrTuple)obj).getAttrObj();
                try {
                    n = getSrvcIndx(this.srvcObj,srvcItems);
                    k = getAttrIndx(this.attrObj,attrs);
                    if (    ((srvcItems[n].service).equals(srvc))
                         && ((attrs[k][0]).equals(attr)) ) {
                        return true;
		    } else {
                        return false;
		    }
                } catch (ArrayIndexOutOfBoundsException e) {
                    return false;
		}
            } else {
                return false;
            }
        }
    }

    /** Steps through the given array of service items searching for the
     *  element that equals the given service item Object; returning the
     *  element index if a match is found and -1 otherwise
     *  @param obj service item object to search for
     *  @param srvcItems array of service items to search
     *  @return int (array index or -1 if no match found)
     */
    public static int getSrvcIndx(Object        obj,
                                  ServiceItem[] srvcItems) {
        try {
            int n;
	    /* get the srvc instance the input Object corresponds to */
            for(n=0;n<srvcItems.length;n++) {
                if ( (srvcItems[n].service).equals(obj) ) {
                    break;
                }
            }
            if (n < srvcItems.length) {
                return n;
	    } else {
                return -1;
	    }
        } catch (ArrayIndexOutOfBoundsException e) {
           return -1;
        } catch (NullPointerException e) {
            return -1;
     	}
    }

    /** Steps through the given array of attribute objects searching for
     *  the element that equals the given attribute Object; returning the
     *  element index if a match is found and -1 otherwise
     *  @param obj attribute object to search for
     *  @return int (array index or -1 if no match found)
     */
    public static int getAttrIndx(Object     obj,
                                  Entry[][]  attrs) {
        try {
            int n;
	    /* get the attribute instance the input Object corresponds to */
            for(n=0;n<attrs.length;n++) {
                if ( (attrs[n][0]).equals(obj) ) {
                    break;
		}
   	    }
            if (n < attrs.length) {
                return n;
	    } else {
                return -1;
	    }
        } catch (ArrayIndexOutOfBoundsException e) {
           return -1;
        } catch (NullPointerException e) {
            return -1;
     	}
    }

    /** Returns the current time in milliseconds 
     *  @return long
     */
    public static long getCurTime() {
	return System.currentTimeMillis();
    }

    /** Creates an instance of the given (loaded) class object. This method 
     *  assumes that the loaded class has a constructor with a single argument
     *  of type int That single int field of the new object will be
     *  initialized to the given value.
     *  @param classObj class type to instantiate
     *  @param instanceIndx value to which to initialize the class field
     *  @exception TestException usually indicates a failure
     *  @return java.lang.Object 
     */
    public static Object createInstance(Class classObj,
                                        int   instanceIndx)
                                                         throws Exception
    {
        Class[] parameterTypes = { int.class };
	    Constructor con = classObj.getConstructor(parameterTypes);
            return con.newInstance(new Object[]{new Integer(instanceIndx)});
    } 

    /** Creates an instance of the given (loaded) class object. This method 
     *  assumes that the loaded class has a constructor with a set of 
     *  arguments corresponding to the contents of given array of Objects. 
     *  The fields of the new object will be initialized to the values 
     *  contained in the given array.
     *  @param classObj class type to instantiate
     *  @param constructorArgs array of Objects for field initialization
     *  @return java.lang.Object 
     */
    public static Object createInstance(Class    classObj,
                                        Object[] constructorArgs)
                                                         throws Exception
    {
        Object o = null;
        if (constructorArgs == null) {
	    o = classObj.newInstance();
        } else {
            Class[] constructorArgTypes = new Class[constructorArgs.length];
	    for (int i=0;i<constructorArgs.length;i++) {
		constructorArgTypes[i] = constructorArgs[i].getClass();
	    }
	    Constructor con = classObj.getConstructor(constructorArgTypes);
	    o = con.newInstance(constructorArgs);
	}
        return o;
    } 

    /** Computes a wait duration based on the given "start" time, a desired 
     *  increment (assuming instanteous time -- the given deltaT), and an 
     *  adjustment to correct for compute time (the current time). A negative 
     *  duration will be treated as an invalid -- or "unresolved" -- test 
     *  case. If the duration is non-negative, then this method will sleep 
     *  for that many milliseconds, and then return.
     *  @param baseT0 the start time
     *  @param deltaT time increment
     *  @exception TestException usually indicates a failure
     */
    public static void computeDurAndWait(long baseT0, long deltaT, long deltaTLimit, Object lock) throws Exception
    {
        long finishTime = baseT0 + deltaT;
        long dur = finishTime - System.currentTimeMillis();
	if(dur > 0) {
            do {
                synchronized (lock) { lock.wait(dur); }
                if ( deltaTLimit > 0 && dur > deltaTLimit) throw new TestException("Waited too long, exceeded limit, possibly due to lock contention");
            } while (finishTime > System.currentTimeMillis()); // In case of spurious wakeup or notify.
	} else {
            throw new TestException("Environment problem; this configuration"
                                    + " does not allow for the timing"
                                    + " assumptions made by the test");
                                    
	}
    }

    /** Sleeps for the given amount of milliseconds
     *  @param deltaT time in milliseconds to sleep
     */
    public static void waitDeltaT(long deltaT, Object lock) throws Exception {
        long finish = System.currentTimeMillis() + deltaT;
        synchronized (lock){
            do {
                lock.wait(deltaT);
            } while (finish > System.currentTimeMillis());
        }
//	Thread.sleep(deltaT);
    }

    /** Returns true if the input argument is even; false otherwise
     *  @param i integer to test for even or odd
     */
    public static boolean isEven(int i) {
        return ((i&1) == 1 ? false:true);
    }

    /** Determines if all of the expected (and no un-expected) events 
     *  have arrived. The test in this method depends on the semantics of 
     *  event-notification. That is, it will use the fact that if the events
     *  were generated for each service class in sequence (which they were),
     *  then the events will arrive in the same sequence. This means we can
     *  expect, when examining the event corresponding to index i, that the
     *  serviceID returned in the ServiceEvent should correspond to the i_th
     *  service registered. If it does not, then failure is declared.
     *
     *  This method is currently employed by the following test classes:
     *
     *                  NotifyOnAttrAdd
     *                  NotifyOnAttrMod
     *                  NotifyOnAttrDel
     *                  NotifyOnSrvcLeaseExpiration
     *
     *  @param eventVector vector containing the events to test
     *  @param nExpectedEvnts number of events expected
     *  @param expectedTransition the expected event transition
     *  @param serviceRegs array of ServiceRegistrations of each service
     *  @exception TestException usually indicates a failure
     */
    public static void verifyEventVector(Vector eventVector,
                                         int nExpectedEvnts,
                                         int expectedTransition,
                                         ServiceRegistration[] serviceRegs)
                                                         throws Exception
    {
        ServiceEvent evnt = null;
        if (eventVector.size() != nExpectedEvnts) {
            throw new TestException("# of Events Received ("+
                                             eventVector.size()+
                                             ") != # of Events Expected ("+
                                             nExpectedEvnts+")");
	} else {
            ServiceID evntSrvcID;
            ServiceID expdSrvcID;
            ServiceID handbackSrvcID;
	    for(int i=0; i<eventVector.size(); i++) {
                evnt = (ServiceEvent)eventVector.elementAt(i);
                if (evnt == null) {
                    throw new TestException
                             ("null Event returned from Vector at element "+i);
	   	} else {
                    if (evnt.getTransition() != expectedTransition) {
			dumpEventIDs(eventVector, serviceRegs);
                        throw new TestException("Unexpected Transition returned ("+
						evnt.getTransition()+")");
		    } else {
                        evntSrvcID = evnt.getServiceID();
                        expdSrvcID = serviceRegs[i].getServiceID();
                        if ( !(evntSrvcID.equals(expdSrvcID)) ) {
                            throw new TestException("Service ID Received ("+
						    evntSrvcID+
						    ") != Service ID Expected ("+
						    expdSrvcID+")");
			} else {
			    handbackSrvcID = 
				(ServiceID)(evnt.getRegistrationObject().get());

			    if ( !(handbackSrvcID.equals(expdSrvcID)) ) {
				throw new TestException
				    ("Handback Service ID ("+
				     handbackSrvcID+
				     ") != Service ID Expected ("+
				     expdSrvcID+")");
			    }
			}
		    }
		}
	    }
	}
	verifyEventItems(eventVector);
    }

    public static void dumpEventIDs(Vector eventVector,
                                    ServiceRegistration[] serviceRegs)
    {
        ServiceEvent evnt = null;
	ServiceID evntSrvcID;
	ServiceID expdSrvcID;
	ServiceID handbackSrvcID;
	for(int i=0; i<eventVector.size(); i++) {
	    evnt = (ServiceEvent)eventVector.elementAt(i);
	    evntSrvcID = evnt.getServiceID();
	    expdSrvcID = serviceRegs[i].getServiceID();
	    System.out.println("Expected ID = " + expdSrvcID + ", received ID = " + evntSrvcID);
	}
    }

    /** Verifies that the ServiceItem in each event is as expected.
     *  If there is no later event with the same service ID, then
     *  compare the item in the event with the current item in the
     *  lookup service.  If the item in the event is null, then the
     *  item must no longer exist in the lookup service, otherwise
     *  the item must exist in the lookup service and be equal to
     *  the one in the event.  We skip an event if there is a later
     *  event with the same service ID, as that means the state of
     *  the item recorded in the event was subsequently changed again.
     *
     *  @param eventVector vector containing the events to test
     *  @exception TestException usually indicates a failure
     */
    public static void verifyEventItems(Vector eventVector)
	throws Exception
    {
	ServiceTemplate tmpl = new ServiceTemplate(null, null, null);
    outer:
	for (int i = 0; i < eventVector.size(); i++) {
	    ServiceEvent evnt = (ServiceEvent)eventVector.elementAt(i);
	    for (int j = i + 1; j < eventVector.size(); j++) {
		ServiceEvent oevnt = (ServiceEvent)eventVector.elementAt(j);
		if (evnt.getServiceID().equals(oevnt.getServiceID()))
		    continue outer;
	    }
	    ServiceItem item = evnt.getServiceItem();
	    tmpl.serviceID = evnt.getServiceID();
	    ServiceRegistrar proxy = (ServiceRegistrar)evnt.getSource();
	    proxy = (ServiceRegistrar) 
		    QAConfig.getConfig().prepare("test.reggiePreparer",
						      proxy);
	    ServiceMatches matches;
	    matches = proxy.lookup(tmpl, 1);
	    if (item == null) {
		if (matches.items.length != 0)
		    throw new TestException(
			      "verifyEventItems: event item is null, lookup returns non-null");
	    } else {
		if (matches.items.length == 0)
		    throw new TestException(
			      "verifyEventItems: event item is non-null, lookup returns null");
		ServiceItem litem = matches.items[0];
		if (!item.service.equals(litem.service))
		    throw new TestException(
			      "verifyEventItems: event item service does not equal lookup value");
		if (!attrsEqual(item.attributeSets, litem.attributeSets))
		     throw new TestException(
			      "verifyEventItems: event item attrs do not equal lookup value");
	    }
	}
    }

    /** Tests if two arrays of attribute sets are the same length and
     *  have equal elements, ignoring element ordering differences.
     *
     *  @param attrs1 an array of attribute sets
     *  @param attrs2 an array of attribute sets
     */
    public static boolean attrsEqual(Entry[] attrs1, Entry[] attrs2) {
	if (attrs1.length != attrs2.length)
	    return false;
    outer:
	for (int i = 0; i < attrs1.length; i++) {
	    for (int j = 0; j < attrs2.length; j++) {
		if (attrs1[i].equals(attrs2[j]))
		    continue outer;
	    }
	    return false;
	}
	return true;
    }

    /** Performs a simple lookup and a match lookup using only the serviceID
     *  @param srvcItems array of registered service items 
     *  @param templates array of used input to lookup()
     *  @param proxy proxy of Registrar through which lookup is performed
     *  @exception TestException usually indicates a failure
     */
    public static void doLookup(ServiceItem[] srvcItems,
                                ServiceTemplate[] templates,
				ServiceRegistrar proxy)  throws Exception
    {
        Object serviceObj = null;
        ServiceMatches matches = null;
        for (int i=0; i<templates.length; i++) {
	    serviceObj = proxy.lookup(templates[i]);
            if (!srvcItems[i].service.equals(serviceObj)) {
                throw new TestException("srvcItems["+i+
					"] != serviceObj returned by lookup()");
	    }

	    matches = proxy.lookup(templates[i],Integer.MAX_VALUE);
            if (matches.totalMatches != 1) {
                throw new TestException
		    ("totalMatches != EXPECTED_N_MATCHES");
	    } else {
                if (!srvcItems[i].service.equals(matches.items[0].service)) {
                    throw new TestException("srvcItems["+i+
					    "] != items[0].service returned by lookup()");
		}
	    }
	}
    }

    /** Steps through the given array of leases, comparing each element's
     *  lease expiration against the given minimum lease expiration; a
     *  failure exception will be thrown if any of the lease expirations
     *  is less than the minimum expiration 
     *  @param leases array of lease objects corresponding to each service 
     *  @param minExpiration minimum lease expiration time
     *  @exception TestException usually indicates a failure
     *  @see net.jini.core.lease.Lease
     */
    public static void verifyLeases(Lease[] leases,
                                    long minExpiration)  throws Exception
    {
	for(int i=0; i<leases.length; i++) {
	    if(leases[i].getExpiration() < minExpiration) {
                throw new TestException("verifyLeases: expiration of lease ["
					+i+"] ("+leases[i].getExpiration()+
					" ms) < expected min expiration ("
					+minExpiration+" ms)");
	    } else {
		minExpiration = leases[i].getExpiration();
	    }
	}
    }

    /** Steps through the given array of leases, renewing each element's
     *  lease with the given lease duration value
     *  @param leases array of lease objects corresponding to each service 
     *  @param leaseDuration lease duration to request in renewal
     *  @exception TestException usually indicates a failure
     *  @see net.jini.core.lease.Lease
     */
    public static void doRenewLease(Lease[] leases,
                                    long leaseDuration) throws Exception
    {
        for(int i=0; i< leases.length; i++ ) {
	    leases[i].renew(leaseDuration);
	}
    }

    /** Steps through the given array of service items, searching for the
     *  element that equals the given Object; returning the index of the
     *  matching element or -1 if no match is found
     *  @param srvcObj service item to search for 
     *  @param srvcItems array of service items to search through 
     *  @see net.jini.core.lookup.ServiceItem
     */
    public static int srvcIndxFrmSimpleLookup( Object        srvcObj,
                                               ServiceItem[] srvcItems ) {
        int indx = -1;
        for (int j=0;((indx==-1)&&(j<srvcItems.length)); j++) {
            if ((srvcItems[j].service.equals(srvcObj))) {
               indx  = j;
	    }
	}
        return indx;
    }

    /** Inserts the given value into the given histogram.
     *  
     *  Note that the method that calls this method must create the 
     *  histogram. If the histogram is null, then this method will throw
     *  a NullPointerException
     *  @param value value to place in the histogram
     *  @param histogram HashMap representing the histogram 
     */
    public static void srvcHistogram( int value,
                                      HashMap histogram ) {
        Integer histKey = new Integer(value);
        Integer histVal = (Integer)histogram.get(histKey);
        int newHistVal = ( (histVal==null) ? 1 : 1+histVal.intValue() );
        Integer newValue = new Integer(newHistVal);
        histogram.put(histKey,newValue);
    }

    /** Returns the value of the largest key index in the given histogram 
     *  @param histogram HashMap representing the histogram 
     *  @return int
     */
    public static int getMaxKeyHistogram( HashMap histogram ) {
        int maxKey = 0;
        int counter = 0;
        if ( histogram != null ) {
            int hSize = histogram.size();
	    for ( int i=0; counter < hSize; i++ ) {
	        if (histogram.get(new Integer(i)) != null) {
                    counter++;
                    if (counter >= hSize) {
                        maxKey = i;
		    }
		}
	    }
	}
        return maxKey;
    }

    /** Prints the values of the given histogram to stdout (for debugging) 
     *  @param histogram HashMap representing the histogram 
     */
    public static void displayHistogram( HashMap histogram ) {
	if ( histogram != null ) {
	    for ( int i=0; i <= getMaxKeyHistogram(histogram); i++ ) {
                System.out.println("histogram("+i+") = "
                                          +histogram.get(new Integer(i)));
	    }
	}
    }

    /** Disregarding order, verify that the given set of class types is
     *  equal to the given set of type descriptors. For the case where
     *  the classType is assignable to net.jini.lookup.entry.ServiceType,
     *  it is only required that the class associated with the typeDescriptor
     *  also is assignable to net.jini.lookup.entry. This is to handle
     *  the case where the actual class is 
     *  com.sun.jini.lookup.entry.BasicServiceType,
     *  which may be preferred. This would case an 'equals' comparison to fail.
     *  In addition, a special case involves a check for the reggie proxy class.
     *  Since the proxy used is configuration dependent, if a typeDescriptor
     *  has the value "com.sun.jini.reggie.RegistrarProxy", then equality
     *  will be satisfied if a classType can be found whose getName method
     *  returns either "com.sun.jini.reggie.RegistrarProxy" or
     *  "com.sun.jini.reggie.ConstrainableRegistrarProxy"
     *
     *  This method is currently employed by the following test classes:
     *
     *                  GetServiceTypesEmpty
     *                  GetServiceTypesClass
     *                  GetServiceTypesAttr
     *
     *  @param classTypes array of class types
     *  @param typeDescriptors array of class name descriptors 
     *  @exception TestException usually indicates a failure
     */
    public static boolean classTypesEqualTypeDescriptors
                                            ( Class[] classTypes,
                                              String[] typeDescriptors )
    {
        if (typeDescriptors == null) {
            return (classTypes == null);
	}
	if (classTypes == null) {
	    logger.log(Level.INFO, "classTypes is null");
	}
        if (classTypes.length != typeDescriptors.length) {
	    logger.log(Level.INFO, "classTypes != typeDescriptors");
	    for (int i = 0; i < classTypes.length; i++) {
		logger.log(Level.INFO, 
			   "classTypes[ " + i + "] = " + classTypes[i]);
	    }
	    for (int i = 0; i < typeDescriptors.length; i++) {
		logger.log(Level.INFO, 
			   "typeDescriptors[ " + i + "] = " 
			   + typeDescriptors[i]);
	    }
            return false;
	}
	String reggieProxy = "com.sun.jini.reggie.RegistrarProxy";
	String cReggieProxy = "com.sun.jini.reggie.ConstrainableRegistrarProxy";
        iLoop:
            for (int i=0; i<classTypes.length; i++) {
                for (int j=0;(j<typeDescriptors.length); j++) {
		    try {
			Class c = Class.forName(typeDescriptors[j]);
                        if (classTypes[i].equals(c)) {
                            continue iLoop;
	                }
			Class serviceTypeClass = ServiceType.class;
                        if (serviceTypeClass.isAssignableFrom(classTypes[i])) {
			    if (serviceTypeClass.isAssignableFrom(c)) {
			        continue iLoop;
			    }
			}
			if (typeDescriptors[j].equals(reggieProxy)) {
			    if (classTypes[i].getName().equals(reggieProxy)
			     || classTypes[i].getName().equals(cReggieProxy)) {
				continue iLoop;
			    }
			}
		    } catch (ClassNotFoundException e) {
			if (classTypes[i].getName().equals(typeDescriptors[j])) {
			    continue iLoop;
			}
			if (typeDescriptors[j].equals(reggieProxy)) {
			    if (classTypes[i].getName().equals(reggieProxy)
			     || classTypes[i].getName().equals(cReggieProxy)) {
				continue iLoop;
			    }
			}
			logger.log(Level.INFO, 
				   "Could not find service type class named " 
				   + typeDescriptors[j]);
		    }
	        }
	        logger.log(Level.INFO,
			   "Could not find match for classTypes = " 
			   + classTypes[i]);
                return false;
            }
        return true; /* success */
    }

    /** Returns the number of classes in the chain of super classes of the
     *  given Object; up to, but not including, the Object class itself
     *  @param obj the Object to analyze for super classes
     *  @exception TestException usually indicates a failure
     *  @return int
     */
    public static int getNSuperClasses(Object obj) {
        int n = 0;
        try {
            Class superClass = obj.getClass().getSuperclass();
            while(true) {
                if( !(superClass.getSuperclass() == null) ) {
                    n++;
                    superClass = superClass.getSuperclass();
   	        } else {
                    return n;
	        }
	    }
        } catch (NullPointerException e) {
            return 0;
        }
    }

    /** Returns the nth super class in the chain of super classes of the
     *  given Object; returns null if the given value of n is invalid.
     *  @param obj the Object to analyze for super classes
     *  @param n super class "index" to return
     *  @exception TestException usually indicates a failure
     *  @return int
     */
    public static Object getNthSuperClass(Object obj, int n) {
        int totalN;
        Class superClass = null;
        try {
            totalN = getNSuperClasses(obj);
            if ((totalN > 0)&&(n>0)&&(n<=totalN)) {
                superClass = obj.getClass().getSuperclass();
                for(int i=1;i<n;i++) {
                    superClass = superClass.getSuperclass();
                }
                return (Object)superClass;
	    } else {
                return null;
	    }
        } catch (NullPointerException e) {
            return null;
        }
    }

    /** Returns true if the given attribute objects match, else returns false
     *  Note that the order of the input Objects is NOT important
     *  @param attrObj0 first attribute object to compare
     *  @param attrObj1 second attribute object to compare
     *  @exception TestException usually indicates a failure
     *  @return boolean
     */
    public static boolean attrsMatch(Object attrObj0, Object attrObj1) {
        Object sObj = null;
        Object cObj  = null;
        if ( attrObj0.equals(attrObj1) ) {
            return true;
	} else if ( (attrObj0.getClass()).equals((attrObj1).getClass()) ) {
            return false;
	} else {
	    if ((attrObj0.getClass()).isAssignableFrom(attrObj1.getClass())) {
                /* cast attrObj1 to super class attrObj0 */
                sObj = attrObj0;
                cObj = attrObj1;
	    } else if
               ((attrObj1.getClass()).isAssignableFrom(attrObj0.getClass())) {
                /* cast attrObj0 to super class attrObj1 */
                sObj = attrObj1;
                cObj = attrObj0;
	    } else {
                return false;
	    }
            return ((Attr)sObj).matches(cObj);
	}
    }

    /** Returns true if the given attribute objects match, else returns false
     *  Note that the order of the input Objects IS important
     *  @param attrObj0 first attribute object to compare
     *  @param attrObj1 second attribute object to compare
     *  @param orderImportant true or false
     *  @exception TestException usually indicates a failure
     *  @return boolean
     */
    public static boolean attrsMatch(Object attrObj0,
                                     Object attrObj1,
                                     boolean orderImportant)
    {
        if (!orderImportant) {
            return attrsMatch(attrObj0,attrObj1);
	} else {
            if ( attrObj0.equals(attrObj1) ) {
                return true;
	    } else if ( (attrObj0.getClass()).equals((attrObj1).getClass()) ) {
                return true;
   	    } else if((attrObj0.getClass()).isAssignableFrom
                                                        (attrObj1.getClass())){
                return ((Attr)attrObj0).matches(attrObj1);
	    } else {
                return false;
	    }
	}
    }

    /** Initializes the given SrvcAttrTuple state array element at the two
     *  given index values to the corresponding values contained in the 
     *  arguments named srvcItems and attrs
     *
     *  This method is currently employed by the following test classes:
     *
     *                  NotifyOnComboAttrAddNull
     *                  NotifyOnComboAttrAddNonNull
     *                  NotifyOnComboAttrModNull
     *                  NotifyOnComboAttrModNonNull
     *                  NotifyOnComboAttrDelNull
     *                  NotifyOnComboAttrDelNonNull
     *                  NotifyOnComboSrvcLeaseExp
     *
     *  @param srvcIndx index of the service item
     *  @param attrIndx index of the attribute
     *  @param superChainLen length of the service super class "chain"
     *  @param srvcItems array of registered services
     *  @param srvcsForEquals array of instantiated services for comparison
     *  @param attrs array-of-arrays of attribute objects for matching
     *  @param state the state array of tuples
     *  @exception TestException usually indicates a failure
     *  @see net.jini.core.lookup.ServiceItem
     */
    public static void initStateTupleArray
                                        (int srvcIndx,
                                         int attrIndx,
                                         int superChainLen,
                                         ServiceItem[] srvcItems,
                                         ServiceItem[] srvcsForEquals,
                                         Entry[][] attrs,
                                         SrvcAttrTuple[][][] state)
	throws Exception
    {
        state[srvcIndx][attrIndx][0]
                               = new SrvcAttrTuple(srvcsForEquals,
                                                   attrs,
                                                   srvcItems[srvcIndx].service,
                                                   attrs[attrIndx][0]);

        Class superClass = null;
        superClass = (srvcItems[srvcIndx].service).getClass().getSuperclass();
        for(int i=1;i<superChainLen;i++) {
            if( !(superClass.getSuperclass() == null) ) {
                state[srvcIndx][attrIndx][i] =
                         new SrvcAttrTuple(srvcsForEquals,attrs,
                                           createInstance(superClass,srvcIndx),
                                           attrs[attrIndx][0]);
                superClass = superClass.getSuperclass();
	    } else {
                state[srvcIndx][attrIndx][i] =
                             new SrvcAttrTuple(srvcsForEquals,attrs,
                                               createInstance(superClass,null),
                                               attrs[attrIndx][0]);
                return;
	    }
	}
    }

    /** Initializes all of the elements in the given SrvcAttrTuple array to 
     *  null for the attribute component of the Tuple; and to the 
     *  corresponding service item for the service component
     *
     *  This method is currently employed by the following test classes:
     *
     *                  NotifyOnComboAttrAddNull
     *                  NotifyOnComboAttrAddNonNull
     *                  NotifyOnComboSrvcReg
     *
     *  @param srvcItems array of registered services
     *  @param srvcsForEquals array of instantiated services for comparison
     *  @param attrs array-of-arrays of attribute objects for matching
     *  @param state the state array of tuples
     *  @exception TestException usually indicates a failure
     *  @see net.jini.core.lookup.ServiceItem
     *  @see net.jini.core.entry.Entry
     */
    public static void initStateTupleArray
                                        (ServiceItem[] srvcItems,
                                         ServiceItem[] srvcsForEquals,
                                         Entry[][] attrs,
                                         SrvcAttrTuple[][][] state)
	throws Exception
    {
        for(int i=0;i<state.length;i++) {
            jLoop:
            for(int j=0;j<state[i].length;j++) {
                state[i][j][0] = new SrvcAttrTuple(srvcsForEquals,attrs,
                                                   srvcItems[i].service,null);
                Class superClass = null;
                superClass = (srvcItems[i].service).getClass().getSuperclass();
                for(int k=1;k<state[i][j].length;k++) {
                    if( !(superClass.getSuperclass() == null) ) {
                        state[i][j][k] = new SrvcAttrTuple
                                                 (srvcsForEquals,attrs,
                                                  createInstance(superClass,i),
                                                  null);
                            superClass = superClass.getSuperclass();
		    } else {
                        state[i][j][k] = new SrvcAttrTuple
                                              (srvcsForEquals,attrs,
                                               createInstance(superClass,null),
                                               null);
                        continue jLoop;
		    }
		}
	    }
	}
    }

    /** Retrieves the chain of super classes of the given object; up to, but
     *  not including, the Object class itself. Returns a vector containing 
     *  initialized instances of each class in the chain (including
     *  the instance of the given object itself).
     *
     *  Note: this method is specific to the set of test service classes
     *        that are registered during any typical lookup test. Thus,
     *        this method should be invoked only on objects that belong to
     *        a chain of classes contained in the set of test services; 
     *        that is, the input object, as well as each of the super 
     *        classes in the chain have a constructor that requires a 
     *        single argument of type int.
     *
     *  @param obj object that is analyzed for its super classes
     *  @param srvcItems array of registered services
     *  @exception TestException usually indicates a failure
     *  @return java.util.Vector 
     *  @see net.jini.core.lookup.ServiceItem
     */
    public static Vector getSrvcSupersVector(Object        obj,
                                             ServiceItem[] srvcItems)
	throws Exception
    {
        int n;
        Class superClass = null;
        Vector objSupers = new Vector();
        objSupers.addElement(obj);
        superClass = obj.getClass().getSuperclass();
        while(true) {
            if( !(superClass.getSuperclass() == null) ) {
                for(n=0;n<srvcItems.length;n++) {
                    if (superClass.isInstance(srvcItems[n].service) ) {
                        break;
		    }
		}
                objSupers.addElement(createInstance(superClass,n));
                superClass = superClass.getSuperclass();
	    } else {
                return objSupers;
	    }
	}
    }

    /** Retrieves the chain of super classes of the input object; up to, but
     *  not including, the Object class itself. Returns a vector containing 
     *  non-initialized instances of each class in the chain (including
     *  the instance of the input object itself).
     *
     *  Note: this method should be invoked only on objects that have 
     *        super classes containing no-arg constructors.
     *
     *  @param obj object that is analyzed for its super classes
     *  @exception TestException usually indicates a failure
     *  @return java.util.Vector 
     */
    public static Vector getNoArgSupersVector(Object obj) throws Exception {
        Class superClass = null;
        Vector objSupers = new Vector();
        objSupers.addElement(obj);
        superClass = obj.getClass().getSuperclass();
        while(true) {
            if( !(superClass.getSuperclass() == null) ) {
		objSupers.addElement( superClass.newInstance() );
                superClass = superClass.getSuperclass();
	    } else {
                return objSupers;
	    }
	}
    }

    /** From the assumption that the addition, modification or deletion 
     *  of an attribute of one of the registered service classes will 
     *  result in one or more events being generated by the lookup service, 
     *  this method "predicts" all of the service/attribute "pairs" 
     *  corresponding to the set of events that will be generated from 
     *  the "base" pair represented by the input srvcObj/attrObj pair.
     *  The number and "cause" of these events is dependent on the 
     *  contents of the template or templates used to register the event 
     *  notification requests with lookup. This method assumes that an 
     *  event was registered using a template containing the class of 
     *  both the input srvcObj argument and the attrObj argument.  
     *
     *  That is, given the template/event-registration-request assumption
     *  just described, if attrObj is added to, modified on, or deleted from 
     *  srvcObj, then an event will be generated for each service/attribute
     *  pair in the lookup service which also matches one of the templates.
     *
     *  This method will populate the given tuples vector with the set of
     *  service/attribute/transition "tuples" corresponding to the events
     *  expected to be generated when the srvcObj's state is modified 
     *  The tuples vector will contain all of the tuples that have accumulated
     *  over the life of the current test run. Thus, because that vector
     *  contains all of the expected pairs generated by previous calls to
     *  this method, the tuples vector must be created and maintained --
     *  outside of this method -- by the invoking class.
     *
     *  This method is currently employed by the following test classes:
     *
     *                  NotifyOnComboAttrAddNull
     *                  NotifyOnComboAttrAddNonNull
     *                  NotifyOnComboAttrModNull
     *                  NotifyOnComboAttrModNonNull
     *                  NotifyOnComboAttrDelNull
     *                  NotifyOnComboAttrDelNonNull
     *                  NotifyOnComboSrvcReg
     *                  NotifyOnComboSrvcLeaseExp
     *
     *  @param srvcObj object whose state has changed
     *  @param srvcItems array of registered services
     *  @param srvcsForEquals array of instantiated services for comparison
     *  @param attrs array-of-arrays of attribute objects for matching
     *  @param nSrvcsPerClass number of service instances per class type
     *  @param template template to use for matching
     *  @param preEventState the state array of tuples prior to the event
     *  @param postEventState the state array of tuples after the event
     *  @param tuples vector in which the expected tuples are accumulated
     *  @exception TestException usually indicates a failure
     *  @see net.jini.core.lookup.ServiceItem
     *  @see net.jini.core.entry.Entry
     */
    public static void setExpectedEvents(Object srvcObj,
                                         ServiceItem[] srvcItems,
                                         ServiceItem[] srvcsForEquals,
                                         Entry[][] attrs,
                                         int nSrvcsPerClass,
                                         ServiceTemplate[][] template,
                                         SrvcAttrTuple[][][] preEventState,
                                         SrvcAttrTuple[][][] postEventState,
                                         Vector tuples)
                                                        throws Exception
    {
        int i=0;
        int j=0;
        int n=0;
        SrvcAttrTuple tmplTuple;
        Vector srvcSupers = new Vector();
	int s0 = getSrvcIndx(srvcObj,srvcItems);
	int nAttrs = preEventState[0].length;
	int trans;
	int newSrvcIndx;
	ServiceTemplate srvcTmpl;
	for(j=0;j<nAttrs;j++) {
	    srvcTmpl = template[s0/nSrvcsPerClass][j];
	    for(i=0;i<(srvcTmpl.serviceTypes).length;i++) {
		tmplTuple = new SrvcAttrTuple
		    ( srvcsForEquals,attrs,
		      createInstance(srvcTmpl.serviceTypes[i],s0),
		      (srvcTmpl).attributeSetTemplates[0] );

		if (  ((preEventState[s0][j][i]).getAttrObj() == null)
		      &&((postEventState[s0][j][i]).getAttrObj()!= null))
		    {
		        /* attribute addition */
                        if ( (postEventState[s0][j][i]).objEquals(tmplTuple) )
			    {
				trans = ServiceRegistrar.TRANSITION_NOMATCH_MATCH;

				(preEventState[s0][j][i]).setEqualTo
				    (postEventState[s0][j][i]);
				newSrvcIndx = ((s0/nSrvcsPerClass)*nSrvcsPerClass)
				    -(nSrvcsPerClass*i);
				tuples.addElement(new SrvcAttrTuple
				    (srvcsForEquals,attrs,
				     srvcItems[newSrvcIndx].service,
				     attrs[j][0],
				     trans));
			    } else {
				break;
			    }
		    } else if( ((preEventState[s0][j][i]).getAttrObj() != null)
			       &&((postEventState[s0][j][i]).getAttrObj()== null))
			{
			    /* attribute deletion */
			    if ( (preEventState[s0][j][i]).objEquals(tmplTuple) ) {
				trans = ServiceRegistrar.TRANSITION_MATCH_NOMATCH;

				(preEventState[s0][j][i]).setEqualTo
				    (postEventState[s0][j][i]);
				newSrvcIndx = ((s0/nSrvcsPerClass)*nSrvcsPerClass)
				    -(nSrvcsPerClass*i);

				tuples.addElement(new SrvcAttrTuple
				    (srvcsForEquals,attrs,
				     srvcItems[newSrvcIndx].service,
				     attrs[j][0],
				     trans));
			    } else {
				break;
			    }
			} else if( ((preEventState[s0][j][i]).getAttrObj() != null)
				   &&((postEventState[s0][j][i]).getAttrObj()!= null))
			    {
				/* attribute modification */
				if ((postEventState[s0][j][i]).objEquals(tmplTuple)) {

				    if ((postEventState[s0][j][i]).objEquals
					(preEventState[s0][j][i]))
					{
					    trans
						= ServiceRegistrar.TRANSITION_MATCH_MATCH;
					} else {
					    trans
						= ServiceRegistrar.TRANSITION_NOMATCH_MATCH;

					    (preEventState[s0][j][i]).setEqualTo
						(postEventState[s0][j][i]);
					}
				    newSrvcIndx =((s0/nSrvcsPerClass)*nSrvcsPerClass)
					-(nSrvcsPerClass*i);

				    tuples.addElement(new SrvcAttrTuple
					(srvcsForEquals,attrs,
					 srvcItems[newSrvcIndx].service,
					 attrs[j][0],
					 trans));
				} else {
				    break;
				}
			    }
	    }
	}

    }

    /** From the assumption that an event was registered using a template
     *  containing the class of both the input srvcObj argument and the
     *  attrObj argument, this method "predicts" all of the service/attribute
     *  "pairs" corresponding to the set of events that will be generated 
     *  from the "base" pair represented by the input srvcObj/attrObj pair. 
     *
     *  This method will populate the given tuples vector with the set of
     *  service/attribute/transition "tuples" corresponding to the events
     *  expected to be generated when the srvcObj's state is modified 
     *  The tuples vector will contain all of the tuples that have accumulated
     *  over the life of the current test run. Thus, because that vector
     *  contains all of the expected pairs generated by previous calls to
     *  this method, the tuples vector must be created and maintained --
     *  outside of this method -- by the invoking class.
     *
     *  This method is currently employed by the following test classes:
     *
     *                  NotifyOnEntryAttrSrvcReg
     *                  NotifyOnEntryAttrAddNull
     *                  NotifyOnEntryAttrAddNonNull
     *
     *  @param srvcIndx index into the template corresponding to the service
     *  @param srvcItems array of registered services
     *  @param srvcsForEquals array of instantiated services for comparison
     *  @param attrs array-of-arrays of attribute objects for matching
     *  @param template template to use for matching
     *  @param state the state array of tuples after the event
     *  @param tuples vector in which the expected tuples are accumulated
     *  @exception TestException usually indicates a failure
     *  @see net.jini.core.lookup.ServiceItem
     *  @see net.jini.core.entry.Entry
     */
    public static void setExpectedEvents(int srvcIndx,
                                         ServiceItem[] srvcItems,
                                         ServiceItem[] srvcsForEquals,
                                         Entry[][] attrs,
                                         ServiceTemplate[] template,
                                         SrvcAttrTuple[][][] state,
                                         Vector tuples)
                                                        throws Exception
    {
        int trans = ServiceRegistrar.TRANSITION_NOMATCH_MATCH;
        Object tmplAttr = (template[srvcIndx]).attributeSetTemplates[0];

        for(int i=0;i<state.length;i++) {
            for(int j=0;j<state[i].length;j++) {
                if (state[i][j][0] != null) {
                    Object stateAttr = (state[i][j][0]).getAttrObj();
                    if (stateAttr != null) {
                        if (attrsMatch(stateAttr,tmplAttr,true)) {
                            tuples.addElement(new SrvcAttrTuple
                                               (srvcsForEquals,attrs,
                                                (state[i][j][0]).getSrvcObj(),
                                                stateAttr,
                                                trans));
		        }
		    }
		}
	    }
	}
    }

    /** Compares the number and content of the given set of expected events
     *  to the given set of received events; where the event information 
     *  is stored in the given vectors as objects of class type SrvcAttrTuple
     *  @param receivedTuples array of received event tuples
     *  @param expectedTuples array of expected event tuples
     *  @param maxWaitTime maximum number of milliseconds to wait for events
     *  @param showTime true/false: write elapsed time to standard output
     *  @exception TestException usually indicates a failure
     */
    public static void verifyEventTuples(Vector receivedTuples, Vector expectedTuples, long maxWaitTime, boolean showTime, Object lock)
                                                         throws Exception
    {
        int i,j;
        long waitDeltaT = N_MS_PER_MIN;
        long nMsWaited  = waitDeltaT;
        /* give the Listener a chance to collect all events */
        while(true) {
	    try {
                synchronized (lock){
                    lock.wait(waitDeltaT);
                }
	    } catch (InterruptedException e) { }
            if (showTime) {
                System.out.println("Total Time: "
                                   +(nMsWaited/N_MS_PER_MIN)+
                                   " mins");
	    }
            if (receivedTuples.size() != expectedTuples.size()) {
	        if (nMsWaited < maxWaitTime) {
                    nMsWaited = nMsWaited + waitDeltaT;
		} else {
                    throw new TestException
                     ("# of Events Received ("+receivedTuples.size()+
                      ") != # of Events Expected ("+expectedTuples.size()+")");
		}
	    } else {
                break;
	    }
	}
        if (showTime) {
            System.out.println("\n# of Events Expected = "
                               +expectedTuples.size());
            System.out.println("# of Events Received = "
                               +receivedTuples.size()+"\n");
            System.out.println
                           ("# of seconds each event took to arrive (approx): "
                   +(N_MS_PER_SEC*receivedTuples.size())/nMsWaited+
                            " secs\n");
            System.out.println
                       ("Comparing Received Events to Expected Events ...\n");
	}
        verifyEventTupleContent(receivedTuples,expectedTuples);
    }

    /** Compares the number and content of the given set of expected events
     *  to the given set of received events; where the event information 
     *  is stored in the given vectors as objects of class type SrvcAttrTuple
     *  @param receivedTuples array of received event tuples
     *  @param expectedTuples array of expected event tuples
     *  @param maxWaitTime maximum number of milliseconds to wait for events
     *  @exception TestException usually indicates a failure
     */
    public static void verifyEventTuples(Vector  receivedTuples,
                                         Vector  expectedTuples,
                                         long    maxWaitTime)
                                                         throws Exception
    {
        int i,j;
        long waitDeltaT = N_MS_PER_MIN;
        long nMsWaited  = waitDeltaT;
        /* give the Listener a chance to collect all events */
        while(true) {
	    try {
                Thread.sleep(waitDeltaT);
	    } catch (InterruptedException e) { }
            if (receivedTuples.size() != expectedTuples.size()) {
	        if (nMsWaited < maxWaitTime) {
                    nMsWaited = nMsWaited + waitDeltaT;
		} else {
                    throw new TestException
                     ("# of Events Received ("+receivedTuples.size()+
                      ") != # of Events Expected ("+expectedTuples.size()+")");
		}
	    } else {
                break;
	    }
	}
        verifyEventTupleContent(receivedTuples,expectedTuples);
    }

    /** Based on the number of service class instances and the number of
     *  attributes per service participating in the current test run, 
     *  this method computes and returns the maximum number of milliseconds
     *  to wait for all events to arrive from the lookup service before 
     *  attempting to verify the events.
     *  @param nSrvcs total number of service instances registered
     *  @param nAttrs total number of attribute instances
     *  @return long 
     */
    public static long getMaxNMsToWaitForEvents(int nSrvcs,
                                                int nAttrs) {
        long maxTime = 10*nSrvcs*nAttrs*N_MS_PER_SEC;
        if (maxTime < N_MS_PER_MIN) {
            maxTime = N_MS_PER_MIN;
	}
        return maxTime;
    }

    /** Based on the number of service class instances and the number of
     *  attributes per service participating in the current test run, 
     *  this method computes and returns the maximum number of milliseconds
     *  to wait for all events to arrive from the lookup service before 
     *  attempting to verify the events.
     *  @param nSrvcs total number of service instances registered
     *  @param nAttrs total number of attribute instances
     *  @param showTime true/false: write elapsed time to standard output
     *  @return long 
     */
    public static long getMaxNMsToWaitForEvents(int nSrvcs,
                                                int nAttrs,
                                                boolean showTime) {
        long maxTime = getMaxNMsToWaitForEvents(nSrvcs,nAttrs);
        if (showTime) {
            System.out.println("\nmaxNMsToWaitForEvents = "
                               +(maxTime/N_MS_PER_MIN)+" mins");
	}
        return maxTime;
    }

    /** This method sets values for the maximum duration of all service
     *  and event leases.
     *  @param sysConfig the test config object
     *  @param leaseDuration maximum service lease duration and event lease
     *         duration in milliseconds
     */
    public static void setLeaseDuration(QAConfig sysConfig,
                                        long leaseDuration) 
    {
        setLeaseDuration(sysConfig,leaseDuration,leaseDuration);
    }

    /** This method sets values for the maximum duration of all service
     *  and event leases.
     *  @param sysConfig the test config object
     *  @param serviceLeaseDuration maximum service lease duration 
     *         in milliseconds
     *  @param eventLeaseDuration maximum event lease duration 
     *         in milliseconds
     */
    public static void setLeaseDuration(QAConfig sysConfig,
                                        long serviceLeaseDuration,
                                        long eventLeaseDuration) 
    {
        sysConfig.addOverrideProvider(new ServiceLeaseOverrideProvider(
            sysConfig, serviceLeaseDuration, eventLeaseDuration));
    }

    /** Prints the fields of each element (tuple) of the given tuples vector. 
     *  Each element of the vector must be an instance of the class 
     *  SrvcAttrTuple. This method is intended to be used only for
     *  debugging.
     *  @param tuples vector in which the expected tuples are accumulated
     */
    public static void displayTuples(Vector tuples) {
        System.out.println
          ("\n--------------------------------------------------------------");
        int i=0;
        Object tupleSrvc;
        Object tupleAttr;
        int trans;
	try {
            for(i=0;i<tuples.size();i++) {
                tupleSrvc
                     = ((SrvcAttrTuple)tuples.get(i)).getSrvcObj();
                tupleAttr
                     = ((SrvcAttrTuple)tuples.get(i)).getAttrObj();
                trans
                  = ((SrvcAttrTuple)tuples.get(i)).getTransition();
                System.out.println("srvcObj    = "+tupleSrvc);
                System.out.println("attrObj    = "+tupleAttr);
                System.out.println("Transition = "+trans_str[trans]);
                if(i<(tuples.size()-1))System.out.println(" ");
            }
	} catch (ClassCastException e) {
            System.out.println
                   ("displayTuples: ClassCastException (element at index i="+i+
                    " can NOT be cast to type SrvcAttrTuple)");
	} catch (ArrayIndexOutOfBoundsException e) {
            System.out.println
                   ("displayTuples: ArrayIndexOutOfBoundsException (i="+i+")");
	}
        System.out.println
          ("--------------------------------------------------------------\n");
    }

    /** Check if an array contains the object. return true if it contains */
    public static boolean isArrayContain(Object a[], Object o) {
	for(int i=0; i<a.length; i++) 
	    if (a[i].equals(o) )
		return true;
	return false;
    }
 

    /** Prints the fields of each element (tuple) of the given state array. 
     *  Each element of the array must be an instance of the class 
     *  SrvcAttrTuple. This method is intended to be used only for
     *  debugging.
     *  @param state array of state tuples
     */
    private static void displayTuples(SrvcAttrTuple[][][] state) {
        System.out.println
          ("\n--------------------------------------------------------------");
        int i=0;
        int j=0;
        int k=0;
        Object tupleSrvc;
        Object tupleAttr;
        int trans;
	try {
            for(i=0;i<state.length;i++) {
                for(j=0;j<(state[i]).length;j++) {
                    for(k=0;k<(state[i][j]).length;k++) {
                        tupleSrvc 
                    = ((SrvcAttrTuple)state[i][j][k]).getSrvcObj();
                        tupleAttr
                    = ((SrvcAttrTuple)state[i][j][k]).getAttrObj();
                        trans
                    = ((SrvcAttrTuple)state[i][j][k]).getTransition();
                        System.out.println("srvcObj    = "+tupleSrvc);
                        System.out.println("attrObj    = "+tupleAttr);
                        System.out.println("Transition = "+trans_str[trans]);
                    if(k<(state[i][j]).length-1)System.out.println(" ");
		    }
                    if(j<(state[i]).length-1)System.out.println(" ");
                }
                if(i<state.length-1)System.out.println(" ");
            }
	} catch (ClassCastException e) {
            System.out.println
                        ("displayTuples: ClassCastException (element at i="+i+
                         ",j="+j+" can NOT be cast to type SrvcAttrTuple)");
	} catch (ArrayIndexOutOfBoundsException e) {
            System.out.println
                   ("displayTuples: ArrayIndexOutOfBoundsException (i="+i+
                                                                  ",j="+j+")");
	}
        System.out.println
          ("--------------------------------------------------------------\n");
    }

    /*  Compares the content of the given set of expected events to the given
     *  set of received events (this method is shared by the overloaded 
     *  versions of the method verifyEventTuples)
     */
    private static void verifyEventTupleContent(Vector  receivedTuples,
                                                Vector  expectedTuples)
                                                         throws Exception
    {
        int i,j;
        iLoop:
        for(i=0; i<receivedTuples.size(); i++) {
	    /*  step through the vector containing the expected tuples
             *  if the current received tuple is not in the set of 
             *  expected tuples, declare failure
             */
           for(j=0;j<expectedTuples.size();j++) {
                if((receivedTuples.get(i)).equals(expectedTuples.get(j))) {
                    expectedTuples.removeElementAt(j);
                    continue iLoop;
		}
	    }
            throw new TestException
                   ("Received an UNEXPECTED Event\nSrvc  = "
                    +(((SrvcAttrTuple)receivedTuples.get(i)).getSrvcObj())+
                    ",\nAttr  = "
                    +(((SrvcAttrTuple)receivedTuples.get(i)).getAttrObj())+
                    ",\nTrans = "
                    +(((SrvcAttrTuple)receivedTuples.get(i)).getTransition()));
        }
    }

    /** Removes any repeated elements from the ArrayList and then compares
     *  the String elements in the array to the String elements in ArrayList.
     *  Returns true if the sets are equal in size and if each element in
     *  one set is equal to one element in the other set; returns false
     *  otherwise.
     *
     *  This method is currently employed by the following test classes:
     *
     *                  SimpleAddMemberGroups
     *                  SimpleSetMemberGroups
     *                  SimpleRemoveMemberGroups
     *
     *  @param returnedGroups array of String elements from getMemberGroups
     *  @param expectedGroups ArrayList of expected String elements
     */
    public static boolean groupsAreEqual(String[] returnedGroups,
                                         ArrayList expectedGroups) {
        ArrayList filteredList = removeRepeatedElems(expectedGroups);
        if (returnedGroups.length != filteredList.size()) {
            return false;
	}
	iLoop:
        for(int i=0;i<filteredList.size();i++) {
            for(int j=0;j<returnedGroups.length;j++) {
                if (returnedGroups[j].equals((String)(filteredList.get(i)))) {
                    continue iLoop;
		}
	    }
            return false;
        }
        return true;
    }

    /** Removes any repeated elements from the input ArrayList; returns
     *  a new ArrayList containing all unique elements from the input list.
     *
     *  This method is currently employed by the following test classes:
     *
     *                  SimpleAddMemberGroups
     *                  SimpleSetMemberGroups
     *                  SimpleRemoveMemberGroups
     *
     *  @param list ArrayList of String elements
     */
    public static ArrayList removeRepeatedElems(ArrayList list) {
        ArrayList newList = new ArrayList();
        iLoop:
        for(int i=0;i<list.size();i++) {
            String str = (String)(list.get(i));
            for(int j=0; j<newList.size();j++) {
                if (str.equals((String)(newList.get(j)))) {
                    continue iLoop;
		}
	    }
            newList.add(str);
        }
        return newList;
    }

    /** Removes all elements of the String array from the ArrayList; returns
     *  a new ArrayList containing the elements of the original ArrayList 
     *  minus elements from the String array.
     *
     *  This method is currently employed by the following test classes:
     *
     *                  SimpleRemoveMemberGroups
     *
     *  @param list ArrayList of String elements
     *  @param strArray array of String elements
     */
    public static ArrayList removeListFromArray(ArrayList list,
                                                String[] strArray) {
        ArrayList newList = new ArrayList();
        iLoop:
        for(int i=0;i<list.size();i++) {
            String str = (String)(list.get(i));
            for(int j=0;j<strArray.length;j++) {
                if (str.equals(strArray[j])) {
                    continue iLoop;
		}
	    }
            newList.add(str);
        }
        return newList;
    }

    /** Prints the elements of the input String array.
     *  
     *  This method is intended to be used only for debugging.
     *  @param strArray array of String elements
     */
    public static void displayStringArray(String[] strArray) {
        System.out.println(" ");
        for(int i=0;i<strArray.length;i++) {
	    if ((i>=0)&&(i<=9)) {
                System.out.println("["+i+"]   = "+strArray[i]);
            } else if ((i>=10)&&(i<=99)) {
                System.out.println("["+i+"]  = "+strArray[i]);
   	    } else {
                System.out.println("["+i+"] = "+strArray[i]);
            }
	}
    }

    /** Prints the elements of the input ArrayList.
     *  
     *  Each element of the ArrayList must be of type String. This method 
     *  is intended to be used only for debugging.
     *  @param list ArrayList of String elements
     */
    public static void displayStringArrayList(ArrayList list) {
        System.out.println(" ");
        for(int i=0;i<list.size();i++) {
	    if ((i>=0)&&(i<=9)) {
                System.out.println("["+i+"]   = "+(String)(list.get(i)));
            } else if ((i>=10)&&(i<=99)) {
                System.out.println("["+i+"]  = "+(String)(list.get(i)));
   	    } else {
                System.out.println("["+i+"] = "+(String)(list.get(i)));
            }
	}
    }

    /** Determines if the input Objects are equal; where equality is defined
     *  by Class equality and field equality. A special case is subclasses
     *  of net.jini.lookup.entry.ServiceType. If both objects are assignable
     *  to ServiceType, then class equality is not required. This is necessary
     *  to handle the case where com.sun.jini.lookup.entry.BasicServiceType
     *  is being compared, and one of the object was obtained from a preferred
     *  class loader.
     *
     *  @param obj0 the to compare to obj1 for equality
     *  @param obj1 the to compare to obj0 for equality
     *  @return boolean
     */
    public static boolean objsAreEqual(Object obj0, Object obj1) {
        Class obj0Class = (obj0).getClass();
        Class obj1Class = (obj1).getClass();
        if (!(obj1Class.equals(obj0Class))) {
            Class st = net.jini.lookup.entry.ServiceType.class;
	    if (!st.isAssignableFrom(obj0Class)
                || !st.isAssignableFrom(obj1Class)) {
                return false;
            }
        }
        Field[] obj0Fields = obj0Class.getFields();
        Field[] obj1Fields = obj1Class.getFields();
        try {
	    jLoop:
            for(int j=0;j<obj0Fields.length;j++) {
                String name0 = (obj0Fields[j]).getName();
                for(int k=0;k<obj1Fields.length;k++) {
                    String name1 = (obj1Fields[k]).getName();
                    if (name0.equals(name1)) {
                        Object field0 = (obj0Fields[j]).get(obj0);
                        Object field1 = (obj1Fields[k]).get(obj1);
			if (field0 == null) {
			    if (field1 != null)
				return false;
			    continue jLoop;
			} else if (field0.equals(field1)) {
                            continue jLoop;
                        } else {
                            return false;
                        }
		    }
		}
                return false;
            }
        } catch (IllegalAccessException e) {
            return false;
	}
        return true;
    }
}
