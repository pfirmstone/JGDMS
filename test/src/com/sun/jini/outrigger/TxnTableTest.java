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

package com.sun.jini.outrigger;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import net.jini.core.transaction.server.TransactionManager;
import junit.framework.TestCase;

import static org.mockito.Mockito.*;

public class TxnTableTest extends TestCase {

	private static Class innerKey = null;
	static {
		Class[] classes = TxnTable.class.getDeclaredClasses();
		for(Class clazz : classes) {
			if(clazz.getName().endsWith("Key")) {
				innerKey = clazz;
				break;
			}
		}
	}
	
	/**
	 * Test to expose the bug identified in River-
	 * Test to validate patch supplied in River-283
	 * 
	 */
	@SuppressWarnings("unchecked")
	public void testKeyEquals() throws Exception {
		
		TxnTable mockTxnTable = mock(TxnTable.class);
		
		TransactionManager mockTransactionManager = mock(TransactionManager.class);
		
		final long id = 1000L;
		final boolean isPrepared = true;
		
		Object key = createKeyInstance(mockTxnTable,
									   mockTransactionManager, 
									   id, 
									   isPrepared);
		
		Object equalKey = createKeyInstance(mockTxnTable,
											mockTransactionManager, 
											id, 
											isPrepared);
		
		assertTrue("These keys should be equal", key.equals(equalKey));
		
		Object notPrepared = createKeyInstance(mockTxnTable,
							  				   mockTransactionManager, 
											   id, 
											   !isPrepared);
		
		//the following assertion reveals the bug
		assertTrue("Although not prepared, it's manager is the same and should therefore be equal", key.equals(notPrepared));
		
		try {
			notPrepared.equals(notPrepared);
			fail("No AssertionError thrown");
		} catch (AssertionError ae) {}
	}

	private Object createKeyInstance(TxnTable mockTxnTable,
									  TransactionManager mockTransactionManager, 
									  final long firstId,
									 final boolean isPrepared) throws NoSuchMethodException,
									 									InstantiationException, 
									 									IllegalAccessException,
									 									InvocationTargetException {
		
		Constructor innerKeyCntr = innerKey.getDeclaredConstructor(new Class[] { TxnTable.class,
																				  TransactionManager.class,
														        				  long.class,
																        		  boolean.class });
		assertNotNull("Sanity failed", innerKeyCntr);
		
		innerKeyCntr.setAccessible(true);
		Object instance = innerKeyCntr.newInstance(new Object[] {mockTxnTable, 
																  mockTransactionManager,
																  firstId,
																  isPrepared} );
		
		assertNotNull("Sanity failed", instance);
		
		return instance;
	}
	
}
