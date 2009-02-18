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

package com.sun.jini.test.impl.joinmanager;

import java.util.logging.Level;

import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.test.share.AttributesUtil;

import com.sun.jini.test.spec.joinmanager.AbstractBaseTest;
import com.sun.jini.test.spec.joinmanager.RegisterAttributes;

import net.jini.core.entry.Entry;

/**
 * This class verifies that the current implementation of the 
 * <code>JoinManager</code> utility class executes the tasks which perform
 * attribute replacement and attribute augmentation respectively in the
 * correct order; resulting in an attribute state that is expected, and
 * is consistent across all lookup services with which the service is
 * registered.
 *
 * In particular, a request to replace the service's current attribute is
 * made initially, and is then immediately followed by a request to augment
 * the service's attributes. If the join manager does not guarantee the
 * execution order -- on a 'lookup-service-by-lookup-service' basis -- of the
 * tasks used to perform the attribute replacement and augmentation, then
 * it is possible for augmentation on one or more of the lookup services to
 * occur before the replacement occurs. This test verifies that the attribute
 * replacement always occurs on each lookup service prior to the attribute
 * augmentation.
 * 
 * @see net.jini.lookup.JoinManager
 * @see com.sun.jini.thread.TaskManager
 * @see com.sun.jini.thread.TaskManager.Task
 */
public class SetAddAttributesOrder extends RegisterAttributes {

    protected Entry[] addAttrs;
    protected Entry[] setAttrs;
    protected Entry[] expectedAttrs;

    /** Performs actions necessary to prepare for execution of the 
     *  current test as follows:
     * <p><ul>
     *     <li> starts N lookup service(s) whose member groups are finite and
     *          unique relative to the member groups of all other lookup
     *          services running within the same multicast radius of the new
     *          lookup services
     *     <li> creates an instance of JoinManager inputting an instance of
     *          a test service, a non-null set of attributes to register with
     *          the service, and a non-null instance of a lookup discovery
     *          manager configured to discover the lookup services started in
     *          the previous step
     *     <li> constructs the set of attributes with which to replace the
     *          join manager's current set of attributes
     *     <li> constructs the set of attributes with which to augment the
     *          new, replaced, set of attributes
     *     <li> constructs the set of attributes one would expect if the
     *          initial set of attributes are initially replaced by the first
     *          set of attributes constructed in this method, and then the
     *          set of attributes resulting from the replacement operation
     *          is augmented by the second set attributes constructed in this
     *          method
     *   </ul>
     */
    public void setup(QAConfig sysConfig) throws Exception {
        super.setup(sysConfig);
        /* From some given value, different from any of the current
	 * attribute values, create a set of attributes with which to
	 * initially replace all of the current attributes. 
         * 
         * At least one attribute in this set should inject a delay in its
         * serialization process. This is done so that when testing join
         * manager implementations that are not synchronized to preserve the
         * service's attribute state, the attribute addition that is
         * requested after the attribute replacement will actually complete
         * before the attribute replacement completes; thus, resulting in
         * an un-expected final attribute state. For join manager
         * implementations that are synchronized correctly, such a delay
         * in the serialization process should pose no problem in preserving
         * the service's attribute state.
	 */
        setAttrs = new Entry[newServiceAttrs.length];
        for(int i=0;i<setAttrs.length;i++) {
            int val = 
    (((AbstractBaseTest.TestServiceIntAttr)newServiceAttrs[0]).val).intValue();
            val = val + newServiceAttrs.length + i;
            setAttrs[i] = new SlowTestServiceIntAttr( val );
        }//end loop
        /* Create the set of attributes with which to augment the attribute
         * set that results from the replacement operation. Make sure
         * the attributes to add are different from the attributes used
         * in the replacement operation. Also, there should be no delay
         * imposed on the serialization of these attributes.
	 */
        addAttrs = new Entry[newServiceAttrs.length];
        int offset = serviceAttrs.length + setAttrs.length;
        for(int i=0;i<addAttrs.length;i++) {
            addAttrs[i] = new TestServiceIntAttr( offset
    +( ((AbstractBaseTest.SlowTestServiceIntAttr)setAttrs[i]).val).intValue());
        }//endloop
        /* Create the set of attributes one would expect to find after
	 * replacing the initial attributes with the first set created
	 * above, and then augmenting the attributes resulting from the
         * replacement operation with the second set created above. 
	 * 
	 * Note that if the tasks employed by the join manager to first
	 * replace the initial set of attributes, and then augment the
	 * resulting attributes are not run synchronously, then the
	 * final set of attributes with which the service is associated
	 * in each lookup service can be different from the expected
	 * set created here; and the test will be declared a failure.
	 */

	/* Initially set the expected set to the replacement set. */
        expectedAttrs = new Entry[setAttrs.length+addAttrs.length];
        for(int i=0;i<setAttrs.length;i++) {
            expectedAttrs[i] = new SlowTestServiceIntAttr
    (( ((AbstractBaseTest.SlowTestServiceIntAttr)setAttrs[i]).val).intValue());
        }//end loop
        /* Concatenate the replacement set with the augmentation set */
        for(int i=setAttrs.length;i<expectedAttrs.length;i++) {
            int n = i-setAttrs.length;
            expectedAttrs[i] = new TestServiceIntAttr
         ((((AbstractBaseTest.TestServiceIntAttr)addAttrs[n]).val).intValue());
        }//end loop

    }//end setup

    /** Executes the current test by doing the following:
     * <p>
     *   Replaces the set of attributes with which the service is initially
     *   configured with a new set, and then immediately augments the 
     *   service's attributes with another set of attributes. After 
     *   first replacing and then augmenting the service's attributes,
     *   verifies that the appropriate attributes are propagated to
     *   each lookup service with which the associated test service is
     *   registered.
     */
    public void run() throws Exception {
        super.run();
        /* First call setAttributes() to replace the initial attributes */
        logger.log(Level.FINE, "replacing current attributes with "
                          +"new attributes ...");
        AttributesUtil.displayAttributeSet(setAttrs,"setAttrs", Level.FINE);
        joinMgrSrvcID.setAttributes(setAttrs);
        /* Call addAttributes() to augment the just replaced attributes */
        logger.log(Level.FINE, "augmenting replaced set of attributes ...");
        AttributesUtil.displayAttributeSet(addAttrs,"addAttrs", Level.FINE);
        joinMgrSrvcID.addAttributes(addAttrs);
        /* Verify the service is associated with the expected attributes */
        logger.log(Level.FINE, "verifying attributes were "
                                        +"modified in the correct order on "
                                        +"each lookup service ...");
        verifyPropagation(expectedAttrs,nSecsJoin);
        AttributesUtil.displayAttributeSet(expectedAttrs,
					   "expectedAttrs",
					   Level.FINE);
        logger.log(Level.FINE, 
		   "attributes successfully replaced & augmented, "
		   +"in the correct order, on all "
		   +curLookupListSize("SetAddAttributesOrder.run")
		   +" lookup service(s)");
    }//end run

}//end class SetAddAttributesOrder


