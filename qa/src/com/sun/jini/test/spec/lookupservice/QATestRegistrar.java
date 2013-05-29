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

import java.util.ArrayList;
import com.sun.jini.test.spec.lookupservice.QATestUtils;
import net.jini.core.entry.Entry;
import net.jini.core.lease.*;
import net.jini.core.lookup.*;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.event.EventRegistration;
import net.jini.admin.Administrable;
import com.sun.jini.start.ServiceStarter;
import java.util.Properties;
import java.io.File;
import java.io.Serializable;
import java.io.IOException;
import java.io.ObjectStreamException;
import java.rmi.activation.ActivationGroup;
import java.rmi.activation.ActivationGroupID;
import java.rmi.activation.ActivationException;
import java.rmi.activation.UnknownGroupException;
import java.rmi.RMISecurityManager;
import java.rmi.MarshalledObject;
import java.rmi.RemoteException;
import java.util.List;
import java.util.HashMap;
import java.util.logging.Level;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.QATestEnvironment;
import com.sun.jini.qa.harness.ActivatableServiceStarterAdmin;

import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.security.ProxyPreparer;
import net.jini.export.Exporter;
import net.jini.security.TrustVerifier;
import net.jini.security.proxytrust.ServerProxyTrust;

import com.sun.jini.proxy.BasicProxyTrustVerifier;
import com.sun.jini.qa.harness.Test;

import com.sun.jini.qa.harness.TestException;

/** Besides providing implementations for the abstract methods of the 
 *  super class (QATestEnvironment), this class is the common super class of 
 *  all QA test classes of the Lookup component of the Jini System. 
 *  This class encapsulates shared data and methods on behalf of each 
 *  of those test classes.
 *
 *  @see com.sun.jini.qa.harness.QATestEnvironment
 */
public abstract class QATestRegistrar extends QATestEnvironment implements Test {

    /** The default codebase for the RMI sub-system */
    private final static String DEF_RMI_CODEBASE 
                                       = "http://localhost:8080/qa1.jar";
    /** The default codebase for the Activation daemon */
    private final static String DEF_ACT_CODEBASE
                                       = "http://localhost:8080/reggie-dl.jar http://localhost:8080/jsk-dl.jar";
    /** The default classpath for the Activation daemon */
    private final static String DEF_ACT_CLASSPATH
                                       = "/vob/jive/lib/reggie.jar";
    /** The default policy filename and path */
    private final static String DEF_POLICY     
	             = "/vob/qa/src/com/sun/jini/test/spec/lookupservice/policy";
    /** The number of milliseconds to wait after the service lease has
     *  expired to guarantee that the next lookup is performed after
     *  service lease expiration has occurred
     */
    public long deltaTSrvcLeaseExp = 10;

    /** The number of milliseconds to wait after the event lease has
     *  expired to guarantee that the next event-generating operation is
     *  performed after event lease expiration has occurred
     */
    public long deltaTEvntLeaseExp = 10;

    /** The number of milliseconds to wait after a notification-generating
     *  event has occurred; in order to guarantee that the 
     *  notification-generating event has completed its operations before
     *  attempting any further testing
     */
    public long deltaTEvntNotify = 10;

    /** The number of milliseconds to wait to allow an event listener to
     *  perform its duties after events have been sent by the Registrar
     */
    public long deltaTListener = 10;

    /** The set of possible interfaces implemented by the service classes that
     *  will be registered for testing
     */
    protected static final String INTERFACES[]
	= {"com.sun.jini.test.spec.lookupservice.service.Interface00",
           "com.sun.jini.test.spec.lookupservice.service.Interface01",
           "com.sun.jini.test.spec.lookupservice.service.Interface02",
           "com.sun.jini.test.spec.lookupservice.service.Interface03",
           "com.sun.jini.test.spec.lookupservice.service.Interface04"};

    /** The set of service classes to register for testing */
    protected static final String TEST_SRVC_CLASSES[]
	= {"com.sun.jini.test.spec.lookupservice.service.Service00",
           "com.sun.jini.test.spec.lookupservice.service.Service01",
           "com.sun.jini.test.spec.lookupservice.service.Service02",
           "com.sun.jini.test.spec.lookupservice.service.Service03",
           "com.sun.jini.test.spec.lookupservice.service.Service04",
           "com.sun.jini.test.spec.lookupservice.service.Service05",
           "com.sun.jini.test.spec.lookupservice.service.Service06",
           "com.sun.jini.test.spec.lookupservice.service.Service07",
           "com.sun.jini.test.spec.lookupservice.service.Service08",
           "com.sun.jini.test.spec.lookupservice.service.Service09"};

    /** The maximum number of classes in a "super class chain" of services */
    protected int MAX_SRVC_CHAIN_LEN = 2;
    /** The number of different service classes to test per run */
    private int nTestClasses = TEST_SRVC_CLASSES.length;
    /** The maximum number of different instances of each test class */
    private final static int MAX_N_INSTANCES_PER_CLASS = 10;
    /** The number of different instances of each test class */
    private int nInstancesPerClass = MAX_N_INSTANCES_PER_CLASS;
    /** The total number of different instances created */
    private int nInstances = nTestClasses*nInstancesPerClass;
    /** The set of expected matches when testing lookup by exact class 
     *  Note: the values in this set are dependent on the relationships
     *        among the test classes in TEST_SRVC_CLASSES[].
     */
    protected int[] expectedNMatchesExact;
    /** The maximum number of super classes in any "chain" of test classes
     *  (the class Object will never be counted).
     */
    public final static int MAX_N_SUPER_CHAIN_LEN = TEST_SRVC_CLASSES.length;
    /** The set of expected matches when testing lookup by a test class'
     *  super class. 
     *  Note: the values in this set are dependent on the relationships
     *        among the test classes in TEST_SRVC_CLASSES[]
     */
    protected int[][] expectedNMatchesSuper;
    /** The maximum number of interfaces any test class will implement (add 1
     *  because the classes must implement java.io.Serializable -- but
     *  Serializable will be excluded from the analysis).
     */
    public final static int MAX_N_INTFC_PER_CLASS = 1+INTERFACES.length;
    /** The set of expected matches when testing lookup by interfaces 
     *  implemented by the registered services instances.
     *  Note: the values in this set are dependent on the relationships
     *        among the test classes in QATestRegistrar.TEST_SRVC_CLASSES[]
     */
    protected int[][] expectedNMatchesIntfc;
    /** For each interface defined in the test set, there are a N classes 
     *  from the set of test classes which will implement that interface.  
     *  This array reflects not only the number of classes that implement 
     *  an interface, but also any classes which extend a class that 
     *  implements the interface. That is, if interface i02 is implemted 
     *  by classes t1,t3 and t4, and if classes t7 and t9 do not implement 
     *  i02 but each is a sub-class of a class that implements i02, then 
     *  n = 5 for interface i02 in this array.
     */
    protected static final int N_CLASSES_THAT_IMPL_INTFC[] = {4,5,4,4,1};
    /** This matrix indicates which classes implement which interfaces. 
     *  This matrix, together with the array N_CLASSES_THAT_IMPL_INTFC will 
     *  be used to determine how many matches one can expect when performing 
     *  a lookup-by-interface. That is, these two data structures will be
     *  used to populate the matrix expectedNMatchesIntfc[][].
     */
    protected static final int INTFC_IMPL_MATRIX[][]       = { {0, 0, 0, 0, 0},
                                                               {0, 0, 0, 0, 0},
                                                               {1, 0, 0, 0, 0},
                                                               {0, 1, 0, 0, 0},
                                                               {0, 1, 0, 0, 0},
                                                               {0, 0, 1, 1, 0},
                                                               {0, 0, 1, 0, 0},
                                                               {1, 1, 0, 1, 0},
                                                               {0, 0, 0, 1, 0},
                                                               {1, 1, 1, 0, 1}
                                                             };
    /** This matrix is used to map the interfaces to the set of services
     *  that will be returned by lookup() when performing a lookup-by-
     *  interface. This matrix is then used to compute a start index into
     *  into the array of registered srvcItems[]. It is from this index that
     *  stored services are retrieved and compared to the services returned
     *  by lookup() when testing lookup-by-interfaces. The values in this
     *  matrix correspond to the indices of the array TEST_SRVC_CLASSES[]
     */
    protected static final int INTFC_TO_SI[][] = { {2,3,7,9},
                                                   {3,4,5,7,9},
                                                   {5,6,7,9},
                                                   {5,7,8,9},
                                                   {9}
                                                 };
    /** The set of service classes to register for testing */
    protected static final String ATTR_CLASSES[]
	= {"com.sun.jini.test.spec.lookupservice.attribute.Attr00",
           "com.sun.jini.test.spec.lookupservice.attribute.Attr01",
           "com.sun.jini.test.spec.lookupservice.attribute.Attr02",
           "com.sun.jini.test.spec.lookupservice.attribute.Attr03",
           "com.sun.jini.test.spec.lookupservice.attribute.Attr04",
           "com.sun.jini.test.spec.lookupservice.attribute.Attr05",
           "com.sun.jini.test.spec.lookupservice.attribute.Attr06",
           "com.sun.jini.test.spec.lookupservice.attribute.Attr07",
           "com.sun.jini.test.spec.lookupservice.attribute.Attr08",
           "com.sun.jini.test.spec.lookupservice.attribute.Attr09",
           "com.sun.jini.test.spec.lookupservice.attribute.Attr10",
           "com.sun.jini.test.spec.lookupservice.attribute.Attr11",
           "com.sun.jini.test.spec.lookupservice.attribute.Attr12"};

    /** current OS's file separator */
    public static String separator = File.separator;

    /** The default outputRoot */
    private final static String DEF_OUTPUT_ROOT = ".";
    /** root directory where the report sub-directory should be created */
    private String outputRoot;
    /** The number of different attribute classes to test per run */
    private int nAttrClasses = ATTR_CLASSES.length;
    /** The max number of different instances of each attribute class */
    private final static int MAX_N_INSTANCES_PER_ATTR_CLASS = 1;
    /** The number of different instances of each test class */
    private int nAttrInstancesPerClass = MAX_N_INSTANCES_PER_ATTR_CLASS;
    /** The total number of different instances created */
    private int nAttrInstances = nAttrClasses*nAttrInstancesPerClass;
    /** Column 0 contains the number of "sets" of attribute fields in the
     *  attribute class. Column 1 contains the number of arguments expected
     *  by the attributes' constructor. For example, if attribute class 3
     *  contains the following fields:   int     i0;
     *                                   boolean b0;
     *                                   String  s0;
     *  then N_SETS_FIELDS_ATTR[3] = {1,3}  -- (1 "set" of fields, 3 fields
     *                                          in that set)
     *  If attribute class 4 contains the fields:  int     i0;
     *                                             boolean b0;
     *                                             String  s0;
     *                                             long    l0;
     *                                             int     i1;
     *                                             boolean b1;
     *                                             String  s1;
     *                                             long    l1;
     *  then N_SETS_FIELDS_ATTR[4] = {2,4}  -- (2 "sets" of fields, 4 fields
     *                                          in that set)
     *  The methods defined in this class that are related to instantiating 
     *  the test attribute classes reflected in the array ATTR_CLASSES[] 
     *  assume that all of the attribute classes will be structured in the 
     *  way just described; that is, order and field type are important. This
     *  fact must be taken into account when and if more test attribute 
     *  classes are added and/or more fields are added to existing or new
     *  test attribute classes,  
     */
    protected static final int N_SETS_FIELDS_ATTR[][] = { {0,0},
                                                          {0,0},
                                                          {0,0},
                                                          {1,3},
                                                          {1,3},
                                                          {1,4},
                                                          {1,3},
                                                          {2,4},
                                                          {3,4},
                                                          {1,4},
                                                          {2,3},
                                                          {3,4},
                                                          {4,4}
                                                        };
    /** Constant indicating an Integer argument type in N_SETS_FIELDS_ATTR */
    private static final int ARG_IS_INT     = 0;
    /** Constant indicating a Boolean argument type in N_SETS_FIELDS_ATTR */
    private static final int ARG_IS_BOOLEAN = 1;
    /** Constant indicating a String argument type in N_SETS_FIELDS_ATTR */
    private static final int ARG_IS_STRING  = 2;
    /** Constant indicating a Long argument type in N_SETS_FIELDS_ATTR */
    private static final int ARG_IS_LONG    = 3;
    /** Flag indicating attributes will be created for the first time */
    private static final int INITIAL_ATTRS  = 0;
    /** Flag indicating existing attributes will be modified */
    private static final int MODIFIED_ATTRS = 1;
    /** RMI codebase: the download URL for QA classes */
    private String rmiCodebase = null;
    /** Lookup codebase: the download URL for lookup classes */
    private String actCodebase = null;
    /** Lookup classpath: the classpath for the lookup service */
    private String actClasspath = null;
    /** The path and filename of the policy file */
    private String policy = null;
    /** Array to store all created service items */
    private ServiceItem[] srvcItems;
    /** Array to store the ServiceRegistration returned by register() */
    private ServiceRegistration[] srvcRegs;
    /** The proxy for the lookup service Registrar */
    private ServiceRegistrar proxy;
    /** The proxy for the lookup service Registrar Administrator */
    private Object adminProxy;
    /** Constant indicating non-random service selection during lookup */
    public final static int SELECT_METHOD_SAME_ONE = 0;
    /** Constant indicating random service selection during lookup */
    public final static int SELECT_METHOD_RANDOM   = 1;
    /** A number retrieved from the input arg list representing the
     *  method used by the Registrar to select a service being looked up
     */
    private int selectMethod;
    /** listener to exporter map */
    private HashMap listenerMap = new HashMap();
    /**
     * the name of service for which these test are written
     */
    protected final String serviceName = 
	"net.jini.core.lookup.ServiceRegistrar";

    /**
     * Append two String arrays and return the result.
     * 
     * @param strArray1  String array to be appended first.
     * @param strArray2  String array to be appended next.
     * 
     * @return a String[] that is strArray2 appended to strArray1
     * 
     */
    public String[] appendStringArray(String[] strArray1, String[] strArray2) {

        ArrayList stringList  = new ArrayList();
        for(int i=0;i<strArray1.length;i++) stringList.add(strArray1[i]);
        for(int i=0;i<strArray2.length;i++)  stringList.add(strArray2[i]);
        return (String[])(stringList.toArray(new String[stringList.size()]));
    }

    /** After performing high-level construct by invoking the construct() method
     *  in the super class (QATestEnvironment), this method will perform mid-level,
     *  general-purpose construct actions necessary to prepare for execution of 
     *  the current QA test.
     *
     *  This method parses the argument list to retrieve and process the
     *  following arguments:
     *    -- the number of instances per class that should be created
     *    -- the selection method to employ when performing a lookup
     *    -- the RMI codebase to use
     *    -- the codebase to register with the Activation daemon
     *    -- the name and path of the policy file
     *    -- whether or not to use the transient form of the Registrar
     *
     *  After processing the appropriate data in the argument list, the values
     *  of a number of this class' fields are set, the codebase system property
     *  is set, and the RMI security manager is set.
     *  @param config QAConfig for the current QA test
     *  @exception TestException will usually indicate an "unresolved"
     *  condition because at this point the test has not yet begun.
     */
    public synchronized Test construct(QAConfig config) throws Exception {
	int i,j;
	int index;

	super.construct(config);

	logger.log(Level.FINE, "QATestRegistrar : in setup() method.");

	/** The number of milliseconds to wait after the service lease has
	 *  expired to guarantee that the next lookup is performed after
	 *  service lease expiration has occurred
	 */
	deltaTSrvcLeaseExp = QATestUtils.N_MS_PER_SEC*config.getIntConfigVal
	    ("com.sun.jini.test.spec.lookupservice.deltaTSrvcLeaseExp",10);

	/** The number of milliseconds to wait after the event lease has
	 *  expired to guarantee that the next event-generating operation is
	 *  performed after event lease expiration has occurred
	 */
	deltaTEvntLeaseExp = QATestUtils.N_MS_PER_SEC*config.getIntConfigVal
	    ("com.sun.jini.test.spec.lookupservice.deltaTEvntLeaseExp",10);

	/** The number of milliseconds to wait after a notification-generating
	 *  event has occurred; in order to guarantee that the 
	 *  notification-generating event has completed its operations before
	 *  attempting any further testing
	 */
	deltaTEvntNotify = QATestUtils.N_MS_PER_SEC*config.getIntConfigVal
	    ("com.sun.jini.test.spec.lookupservice.deltaTEvntNotify",10);

	/** The number of milliseconds to wait to allow an event listener to
	 *  perform its duties after events have been sent by the Registrar
	 */
	deltaTListener = QATestUtils.N_MS_PER_SEC*config.getIntConfigVal
	    ("com.sun.jini.test.spec.lookupservice.deltaTListener",10);

        outputRoot = config.getStringConfigVal
	    ("com.sun.jini.test.spec.lookupservice.outputRoot", DEF_OUTPUT_ROOT);
	if (outputRoot.length() > 0 && !outputRoot.endsWith(separator)) {
	    outputRoot = outputRoot + separator;
	}

        nInstancesPerClass = config.getIntConfigVal
                  ("com.sun.jini.test.spec.lookupservice.nInstancesPerClass",10);

        selectMethod = config.getIntConfigVal
                  ("com.sun.jini.test.spec.lookupservice.selectMethod",
		   SELECT_METHOD_RANDOM);

	rmiCodebase = config.getStringConfigVal
	    ("com.sun.jini.test.spec.lookupservice.rmiCodebase", 
	     DEF_RMI_CODEBASE);

        actCodebase = config.getStringConfigVal
	    ("com.sun.jini.test.spec.lookupservice.actCodebase",
	     DEF_ACT_CODEBASE);

	actClasspath = config.getStringConfigVal
	    ("com.sun.jini.test.spec.lookupservice.actClasspath",
	     DEF_ACT_CLASSPATH);

	policy = config.getStringConfigVal
	    ("com.sun.jini.test.spec.lookupservice.policy",
	     DEF_POLICY);

	if(nInstancesPerClass > MAX_N_INSTANCES_PER_CLASS)
            nInstancesPerClass = MAX_N_INSTANCES_PER_CLASS;
        nInstances = nTestClasses * nInstancesPerClass;
        logger.log(Level.FINE, "# of Test Classes        = "+nTestClasses);
        logger.log(Level.FINE, "# of Instances Per Class = "+nInstancesPerClass);
        logger.log(Level.FINE, "# of Instances (total)   = "+nInstances);
        expectedNMatchesExact = new int[nTestClasses];
        for (i = 0; i < (nTestClasses/2); i++) {
            expectedNMatchesExact[2*i]   = 2*nInstancesPerClass;
            expectedNMatchesExact[2*i+1] = nInstancesPerClass;
        }
        expectedNMatchesSuper = new int[nTestClasses][MAX_N_SUPER_CHAIN_LEN];
        /* the following initialization must change when the chain grows */
        for (i = 0; i < (nTestClasses/2); i++) {
            for (j = 0; j < MAX_N_SUPER_CHAIN_LEN; j++) {
                expectedNMatchesSuper[2*i][j]   = 0;
                expectedNMatchesSuper[2*i+1][j] =(j==1?2*nInstancesPerClass:0);
            }
        }
        expectedNMatchesIntfc = new int[nTestClasses][INTERFACES.length];
        for (i = 0; i < nTestClasses; i++) {
            for (j = 0; j < INTERFACES.length; j++) {
                expectedNMatchesIntfc[i][j]
     = nInstancesPerClass*N_CLASSES_THAT_IMPL_INTFC[j]*INTFC_IMPL_MATRIX[i][j];
            }
        }

        /* Currently, it is expected that the rmiCodebase is set OUTSIDE of
         * this program by including a properties-setting argument when the 
         * command "java -Djava.rmi.server.codebase=*** programName" is
         * executed. If that property is not set, or if the rmiCodebase value
         * is passed into this program in a normal argument, then the following
         * code will be executed to set the RMI codebase property from
         * program control.
         */
        String codebase = System.getProperty("java.rmi.server.codebase");
        if (codebase == null) {
            System.setProperty("java.rmi.server.codebase",rmiCodebase);
            /* Note: above, the rmiCodebase was set to either a passed-in 
             *       argument or to the default value (DEF_RMI_CODEBASE).
             */
	}
        logger.log(Level.FINE, "rmiCodebase = "
                      +System.getProperty("java.rmi.server.codebase")+"\n");
        getSetupInfo();

	// Receive Lookup proxy
	proxy = getManager().startLookupService();
	// Receive admin proxy fro Lookup
	adminProxy = ((Administrable)proxy).getAdmin();
	adminProxy = config.prepare("test.reggieAdminPreparer",adminProxy);
        return this;
    }
 
    /* Retrieve (and display) configuration values for the current test */
    private synchronized void getSetupInfo() {
        /* begin harness info */
        logger.log(Level.FINE, "----- Harness Info ----- ");
        String harnessCodebase = System.getProperty("java.rmi.server.codebase",
                                                    "no codebase");
        logger.log(Level.FINE, "generator codebase    -- "
                                        +harnessCodebase);

        String harnessClasspath = System.getProperty("java.class.path",
                                                    "no classpath");
        logger.log(Level.FINE, "harness classpath     -- "
                                        +harnessClasspath);
        /* end harness info */

        /* begin service info */
        logger.log(Level.FINE,
                          "----- ServiceRegistrar Service Info ----- ");
        String serviceImplClassname = getConfig().getStringConfigVal
                                                         (serviceName+".impl",
                                                          "no implClassname");
        logger.log(Level.FINE, "service impl class     -- "
                                        +serviceImplClassname);

        String serviceCobaseArg = getConfig().getStringConfigVal
                                                     (serviceName+".codebase",
                                                      "no codebase");
        String serviceCodebase = serviceCobaseArg;
        logger.log(Level.FINE, "service codebase       -- "
                                        +serviceCodebase);

        String serviceClasspath = getConfig().getStringConfigVal
                                                   (serviceName+".classpath",
                                                    "no classpath");
        logger.log(Level.FINE, "service classpath      -- "
                                        +serviceClasspath);

        String servicePolicyFile = getConfig().getStringConfigVal
                                                 (serviceName+".policyfile",
                                                  "no policyFile");
        logger.log(Level.FINE, "service policy file    -- "
                                        +servicePolicyFile);

        int nInstances = getConfig().getIntConfigVal(serviceName+".instances", 1);
        logger.log(Level.FINE, "# of service instances -- "
                                        +nInstances);
        /* end service info */

    }//end getSetupInfo

    /** Returns the number of different service classes to test per run
     *  @return int
     */
    protected synchronized int getNTestClasses() {
	return   nTestClasses;
    }

    /** Returns the number of different instances of each test class
     *  @return int
     */
    protected synchronized int getNInstancesPerClass() {
	return   nInstancesPerClass;
    }

    /** Returns the total number of different test class instances created
     *  @return int
     */
    protected synchronized int getNInstances() {
	return   nInstances;
    }

    /** Returns the number of different attribute classes to test per run
     *  @return int
     */
    protected synchronized int getNAttrClasses() {
	return   nAttrClasses;
    }

    /** Returns the number of different instances of each attribute class
     *  @return int
     */
    protected synchronized int getNAttrInstancesPerClass() {
	return   nAttrInstancesPerClass;
    }

    /** Returns total number of different attribute class instances created
     *  @return int
     */
    protected synchronized int getNAttrInstances() {
	return   nAttrInstances;
    }

    /** Returns the proxy of the lookup service Registrar
     *  @return ServiceRegistrar object as defined in 
     *  net.jini.core.lookup.ServiceRegistrar
     *  @see net.jini.core.lookup.ServiceRegistrar
     */
    protected synchronized ServiceRegistrar getProxy() { 
	return proxy;
    }
    
    /** Returns the lookup service admin object
     *  @return lookup service admin object
     */
    protected synchronized Object getAdminProxy() { 
	return adminProxy;
    }

    /** Returns the value indicating the method employed by the Registrar to
     *  select a service being looked up
     *  @return int
     */
    protected synchronized int getSelectMethod() {
	return   selectMethod;
    }

    /** Creates service items for each class in the given list of class names
     *  @param classNames array of class name Strings
     *  @exception TestException usually indicates a failure
     *  @return ServiceItem an array of objects as defined in 
     *  net.jini.core.lookup.ServiceItem
     *  @see net.jini.core.lookup.ServiceItem
     */
    protected ServiceItem[] createServiceItems(String[] classNames)
                                                         throws Exception
    {
	int k = 0;
	srvcItems = new ServiceItem[nTestClasses*nInstancesPerClass];
	for (int i = 0; i < nTestClasses; i++) {
	    Class c = Class.forName(classNames[i]);
	    for (int j = 0; j < nInstancesPerClass; j++) {
		srvcItems[k] = createServiceItem(c,k);
		k++;
	    }
	}
	return srvcItems;
    }

    /** Creates service items, containing the given set of attributes, for 
     *  each class in the given list of class names
     *  @param classNames array of class name Strings
     *  @param attrs array of Entry type elements containing attributes
     *  @exception TestException usually indicates a failure
     *  @return ServiceItem an array of objects as defined in 
     *  net.jini.core.lookup.ServiceItem
     *  @see net.jini.core.lookup.ServiceItem
     */
    protected ServiceItem[] createServiceItems(String[] classNames,
                                               Entry[] attrs)
                                                         throws Exception
    {
	int k = 0;
	srvcItems = new ServiceItem[nTestClasses*nInstancesPerClass];
	for (int i = 0; i < nTestClasses; i++) {
	    Class c = Class.forName(classNames[i]);
	    for (int j = 0; j < nInstancesPerClass; j++) {
		srvcItems[k] = createServiceItem(c,k,attrs);
		k++;
	    }
	}
	return srvcItems;
    }

    /** Creates service items for each class and each possible field value
     *  @param nServiceTypes number of service class types to create
     *  @param nServices number of services to create
     *  @exception TestException usually indicates a failure
     *  @return ServiceItem an array of objects as defined in 
     *  net.jini.core.lookup.ServiceItem
     *  @see net.jini.core.lookup.ServiceItem
     */
    protected ServiceItem[] createSrvcsForEquals(int nServiceTypes,
                                                 int nServices) 
                                                         throws Exception
    {
        ServiceItem[] srvcsForEquals =
	    new ServiceItem[nServiceTypes*nServices];
        for (int i=0, k=0;i<nServiceTypes;i++) {
            for (int j=0;j<nServices;j++) {
		Class srvcClass = Class.forName(TEST_SRVC_CLASSES[i]);
		srvcsForEquals[k] = createServiceItem(srvcClass,j);
		k++;
	    }
	}
        return srvcsForEquals;
    }

    /** Creates one service item of the given class type, having the given
     *  field value
     *  @param classObj the class type of the service to create
     *  @param instanceIndx the value to initialize the class field to
     *  @exception TestException usually indicates a failure
     *  @return ServiceItem object as defined in 
     *                      net.jini.core.lookup.ServiceItem
     *  @see net.jini.core.lookup.ServiceItem
     */
    protected ServiceItem createServiceItem(Class classObj,
                                            int   instanceIndx)
                                                         throws Exception
    {
        return new ServiceItem(null,
                               QATestUtils.createInstance
                                                       (classObj,instanceIndx),
                               null);
    }

    /** Creates one service item of the given class type; and which contains
     *  the given set of attributes and has the given field value
     *  @param classObj the class type of the service to create
     *  @param instanceIndx the value to initialize the class field to
     *  @param attrs array of Entry type elements containing attributes
     *  @exception TestException usually indicates a failure
     *  @return ServiceItem object as defined in
     *                       net.jini.core.lookup.ServiceItem
     *  @see net.jini.core.lookup.ServiceItem
     */
    protected ServiceItem createServiceItem(Class   classObj,
                                            int     instanceIndx,
                                            Entry[] attrs)
                                                         throws Exception
    {
        return new ServiceItem(null,
                               QATestUtils.createInstance
                                                       (classObj,instanceIndx),
                               attrs);
    }

    /** Creates one service item having the given class name, having the given
     *  field value
     *  @param className the name of the class type to create
     *  @param instanceIndx the value to initialize the class field to
     *  @exception TestException usually indicates a failure
     *  @return ServiceItem object as defined in
     *                      net.jini.core.lookup.ServiceItem
     *  @see net.jini.core.lookup.ServiceItem
     */
    protected ServiceItem createServiceItem(String className,
                                            int    instanceIndx)
                                                         throws Exception
    {
            return createServiceItem(Class.forName(className),instanceIndx);
    }

    /** Creates one service item having the given class name; and which
     *  contains the given set of attributes and has the given field value
     *  @param className the name of the class type to create
     *  @param instanceIndx the value to initialize the class field to
     *  @param attrs array of Entry type elements containing attributes
     *  @exception TestException usually indicates a failure
     *  @return ServiceItem object as defined in
     *                      net.jini.core.lookup.ServiceItem
     *  @see net.jini.core.lookup.ServiceItem
     */
    protected ServiceItem createServiceItem(String  className,
                                            int     instanceIndx,
                                            Entry[] attrs)
                                                         throws Exception
    {
            return createServiceItem(Class.forName(className),instanceIndx,
                                     attrs);
    }

    /** Creates initialized attributes for each attribute in the list defined
     *  in this class
     *  @exception TestException usually indicates a failure
     *  @return Entry an array of objects as defined in
     *                net.jini.core.entry.Entry
     *  @see net.jini.core.entry.Entry
     */
    protected Entry[] createNonNullFieldAttributes() throws Exception {
        return createAttributes(ATTR_CLASSES);
    }

    /** Creates non-initialized attributes for each attribute in the list
     *  defined in this class
     *  @exception TestException usually indicates a failure
     *  @return Entry an array of objects as defined in
     *                net.jini.core.entry.Entry
     *  @see net.jini.core.entry.Entry
     */
    protected Entry[] createNullFieldAttributes() throws Exception {
        int i=0;
        Entry[] attrs = new Entry[ATTR_CLASSES.length];
        for (i=0;i<nAttrClasses;i++) {
	        Class loadedAttrObj = Class.forName(ATTR_CLASSES[i]);
                attrs[i] = (Entry)loadedAttrObj.newInstance();
        }
        return attrs;
    }

    /** Creates initialized attributes for each attribute class having a
     *  class name contained in the given set of class names
     *  @param classNames array of attribute class names to create
     *  @exception TestException usually indicates a failure
     *  @return Entry an array of objects as defined in
     *                net.jini.core.entry.Entry
     *  @see net.jini.core.entry.Entry
     */
    protected Entry[] createAttributes(String[] classNames)
                                                         throws Exception
    {
	    int k = 0;
	    Entry attrEntries[]
                              = new Entry[nAttrClasses*nAttrInstancesPerClass];
	    for (int i = 0; i < nAttrClasses; i++) {
	        Class c = Class.forName(classNames[i]);
	        for (int j = 0; j < nAttrInstancesPerClass; j++) {
                    attrEntries[k] = createAttribute(c,i,INITIAL_ATTRS);
                    k++;
	        }
	    }
	    return attrEntries;
    }

    /** Creates a set of initialized attributes for each attribute class 
     *  having a class name contained in the given set of class names;
     *  and in which each attribute class is guaranteed to not equal 
     *  corresponding classes created initially.
     * 
     *  This method should be used to create a set of attributes that can
     *  then be used to modify an existing set of attributes belonging
     *  to registered services
     *  @param classNames array of attribute class names to create
     *  @exception TestException usually indicates a failure
     *  @return Entry an array of objects as defined in
     *                net.jini.core.entry.Entry
     *  @see net.jini.core.entry.Entry
     */
    protected Entry[] createModifiedAttributes(String[] classNames)
                                                         throws Exception
    {
	int k = 0;
	Entry attrEntries[]
	    = new Entry[nAttrClasses*nAttrInstancesPerClass];
	for (int i = 0; i < nAttrClasses; i++) {
	    Class c = Class.forName(classNames[i]);
	    for (int j = 0; j < nAttrInstancesPerClass; j++) {
		attrEntries[k] = createAttribute(c,i,MODIFIED_ATTRS);
		k++;
	    }
	}
	return attrEntries;
    }

    /** Creates one attribute of the given attribute class type corresponding
     *  to the given class index; if the createFlag argument equals 
     *  MODIFIED_ATTRS, the field values of the attribute class will be
     *  set to values that differ from the field values created for 
     *  the initially created attribute set
     *  @param classObj class type of the attribute to create
     *  @param classIndx index of the attribute class (for initialization)
     *  @param createFlag MODIFIED_ATTRS or INITIAL_ATTRS
     *  @exception TestException usually indicates a failure
     *  @return Entry object as defined in net.jini.core.entry.Entry
     *  @see net.jini.core.entry.Entry
     */
    protected Entry createAttribute(Class classObj,
                                    int   classIndx,
                                    int   createFlag)  throws Exception
    {
        /* create an array containing the VALUES of the arguments that the
         * class' constructor expects to be input when the class is
         * instantiated 
         */
        Object[] constructorArgs = null;
        int delta = (createFlag == MODIFIED_ATTRS ? 1:0);

        int nSets = N_SETS_FIELDS_ATTR[classIndx][0];
        int nArgsPerSet = N_SETS_FIELDS_ATTR[classIndx][1];
        int nArgs = nSets*nArgsPerSet;
        if (nArgs > 0) {
            constructorArgs = new Object[nArgs];
            int k = 0;
            for (int i=0;i<nSets;i++) {
	        for (int j=0;j<nArgsPerSet;j++) {
                    int val = delta + 100*classIndx + 10*i + j;
                    Integer iVal = new Integer(val);
                    int argIs = j%nArgsPerSet;
                    switch ( argIs ) {
	                case ARG_IS_INT:
                            constructorArgs[k] = iVal;
                            break;
	                case ARG_IS_BOOLEAN:
                            constructorArgs[k] = new Boolean(true);
                            break;
		        case ARG_IS_STRING:
                            constructorArgs[k]
                                         = "attribute string "+iVal.toString();
                            break;
		        case ARG_IS_LONG:
                            constructorArgs[k] = new Long(val);
                            break;
		        default:
                            return null;
		    }
                    k++;
	        }
	    }
        }
        return (Entry)QATestUtils.createInstance(classObj,constructorArgs);
    }

    /** Requesting the maximum duration for the service lease, registers all
     *  service items from the list defined in this class and returns the
     *  resulting ServiceRegistration objects
     *  @exception TestException usually indicates a failure
     *  @return ServiceRegistration an array of objects as defined in
     *  net.jini.core.lookup.ServiceRegistration
     *  @see net.jini.core.lookup.ServiceRegistration
     */
    protected ServiceRegistration[] registerAll() throws Exception {
        return registerNSrvcsPerClass(nInstancesPerClass);
    }

    /** Requesting the given duration for the service lease, registers all
     *  service items from the list defined in this class and returns the
     *  resulting ServiceRegistration objects
     *  @param srvcLeaseDurMS number of milliseconds for lease duration
     *  @exception TestException usually indicates a failure
     *  @return ServiceRegistration an array of objects as defined in
     *  net.jini.core.lookup.ServiceRegistration
     *  @see net.jini.core.lookup.ServiceRegistration
     */
    protected ServiceRegistration[] registerAll(long srvcLeaseDurMS)
                                                         throws Exception
    {
        return registerNSrvcsPerClass(nInstancesPerClass,srvcLeaseDurMS);
    }

    /** Requesting the maximum duration for the service lease, registers
     *  n instances of each of the service items from the list defined 
     *  in this class and returns the resulting ServiceRegistration objects
     *  @param n number of instances of each class to register
     *  @exception TestException usually indicates a failure
     *  @return ServiceRegistration an array of objects as defined in
     *  net.jini.core.lookup.ServiceRegistration
     *  @see net.jini.core.lookup.ServiceRegistration
     */
    protected ServiceRegistration[] registerNSrvcsPerClass(int n)
                                                         throws Exception
    {
        int i=0;
        int j=0;
        int k=0;
        int delta = nInstancesPerClass;
        srvcRegs = 
              new ServiceRegistration[(n*srvcItems.length)/nInstancesPerClass];
        for(i=0;i<srvcItems.length;i=i+delta) {
	    for(j=i;j<(i+n);j++) {
	  	srvcRegs[k++] = registerItem(srvcItems[j],Long.MAX_VALUE,
                                             proxy);
	    }
	}
	return srvcRegs;
    }

    /** Requesting the given duration for the service lease, registers
     *  n instances of each of the service items from the list defined 
     *  in this class and returns the resulting ServiceRegistration objects
     *  @param n number of instances of each class to register
     *  @param srvcLeaseDurMS number of milliseconds for lease duration
     *  @exception TestException usually indicates a failure
     *  @return ServiceRegistration an array of objects as defined in
     *  net.jini.core.lookup.ServiceRegistration
     *  @see net.jini.core.lookup.ServiceRegistration
     */
    protected ServiceRegistration[] registerNSrvcsPerClass(int n,
                                                           long srvcLeaseDurMS)
                                                         throws Exception
    {
        int i=0;
        int j=0;
        int k=0;
        int delta = nInstancesPerClass;
        srvcRegs =
              new ServiceRegistration[(n*srvcItems.length)/nInstancesPerClass];
        for(i=0;i<srvcItems.length;i=i+delta) {
	    for(j=i;j<(i+n);j++) {
	        srvcRegs[k++] = registerItem(srvcItems[j],srvcLeaseDurMS,
                                             proxy);
	    }
	}
	return srvcRegs;
    }

    /** Requesting the maximum duration for the service lease, uses the
     *  given Registrar proxy to register the given service item and 
     *  returns the resulting ServiceRegistration object
     *  @param srvcItem the service item to register
     *  @param regProxy Registrar proxy through which registration occurs
     *  @exception TestException usually indicates a failure
     *  @return ServiceRegistration object as defined in
     *  net.jini.core.lookup.ServiceRegistration
     *  @see net.jini.core.lookup.ServiceRegistration
     */
    protected ServiceRegistration registerItem(ServiceItem      srvcItem,
                                               ServiceRegistrar regProxy)
                                                         throws Exception
    {
	Object reg = regProxy.register(srvcItem,Long.MAX_VALUE);
	reg = getConfig().prepare("test.reggieServiceRegistrationPreparer", reg);
	return (ServiceRegistration) reg;
    }

    /** Requesting the given duration for the service lease, uses the
     *  given Registrar proxy to register the given service item and 
     *  returns the resulting ServiceRegistration object
     *  @param srvcItem the service item to register
     *  @param srvcLeaseDurMS number of milliseconds for lease duration
     *  @param regProxy Registrar proxy through which registration occurs
     *  @exception TestException usually indicates a failure
     *  @return ServiceRegistration object as defined in
     *  net.jini.core.lookup.ServiceRegistration
     *  @see net.jini.core.lookup.ServiceRegistration
     */
    protected ServiceRegistration registerItem(ServiceItem      srvcItem,
                                               long             srvcLeaseDurMS,
                                               ServiceRegistrar regProxy)
                                                         throws Exception
    {
	Object reg = regProxy.register(srvcItem,srvcLeaseDurMS);
	reg = getConfig().prepare("test.reggieServiceRegistrationPreparer", reg);
	return (ServiceRegistration) reg;
    }

    //XXX can exception handling be any smarter?
    protected Lease getRegistrationLease(ServiceRegistration reg) throws Exception {
	Lease lease = reg.getLease();
	return prepareRegistrationLease(lease);
    }

    protected Lease prepareRegistrationLease(Lease lease) throws Exception {
	return (Lease) 
	       getConfig().prepare("test.reggieServiceLeasePreparer", lease);
    }

    protected LeaseMap prepareRegistrationLeaseMap(LeaseMap leaseMap) {
	return leaseMap;
    }

    //XXX can exception handling be any smarter?
    protected Lease getEventLease(EventRegistration reg) throws Exception {
	    Lease lease = reg.getLease();
	    return (Lease) 
		   getConfig().prepare("test.reggieEventLeasePreparer", lease);
    }

    //XXX can exception handling be any smarter?
    protected EventRegistration prepareEventRegistration(EventRegistration reg) 
	throws Exception
    {
	return (EventRegistration) 
	       getConfig().prepare("test.reggieEventRegistrationPreparer", reg);
    }

    protected Object exportListener(RemoteEventListener listener) 
	throws RemoteException
    {
	// wrap a configuration exception in a RemoteException to avoid having
	// to redefine the constructors for a million subclasses of BasicListener
	Exporter exporter = QAConfig.getDefaultExporter();
	Configuration c = getConfig().getConfiguration();
	if (c instanceof com.sun.jini.qa.harness.QAConfiguration) {
	    try {
		exporter = (Exporter) c.getEntry("test",
						 "reggieListenerExporter", 
						 Exporter.class);
	    } catch (ConfigurationException e) {
		throw new RemoteException("Configuration problem", e);
	    }
	}
	listenerMap.put(listener, exporter);
	return exporter.export(listener);

    }

    protected void unexportListener(RemoteEventListener l, boolean force) {
	Exporter exporter = (Exporter) listenerMap.get(l);
	if (exporter != null) {
	    exporter.unexport(force);
	    listenerMap.remove(l);
	}
    }

    protected abstract class BasicListener 
	implements RemoteEventListener, ServerProxyTrust, Serializable 
    {
	Object proxy;

        public BasicListener() throws RemoteException {
            super();
	    proxy = exportListener(this);
        }

	public Object writeReplace() throws ObjectStreamException {
	    return proxy;
	}

	public TrustVerifier getProxyVerifier() {
	    return new BasicProxyTrustVerifier(proxy);
	}
    }
}
