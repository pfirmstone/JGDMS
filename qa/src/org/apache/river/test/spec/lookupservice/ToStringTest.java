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
package org.apache.river.test.spec.lookupservice;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;

import net.jini.core.lookup.ServiceItem;
import net.jini.core.lookup.ServiceMatches;
import net.jini.core.lookup.ServiceTemplate;

import org.apache.river.qa.harness.LegacyTest;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;


/**
 * LegacyTest that verifies that toString() methods of ServiceItem,
 * ServiceMatches, and ServiceTemplate can handle null values,
 * 0-length arrays, and arrays with null elements.
 * 
 */
public class ToStringTest implements LegacyTest {

    private QAConfig config = null;

    public synchronized Test construct(QAConfig config) {
        this.config = config;
        return this;
    }

    public synchronized void run() throws Exception {
        boolean testFailed = false;
        Object[] obj = ObjectFactory.create(new Class[]{ServiceTemplate.class,
            ServiceItem.class, ServiceMatches.class});
        for (int i=0; i<obj.length; i++) {
            try {
                System.out.println(obj[i]);
            } catch (Exception e) {
                testFailed = true;
                e.printStackTrace();
            }
        }  
        if (testFailed)
            throw new Exception("problems encountered with toString()"
                + " implementation");
    }

    public void tearDown(){};

    public static class ObjectFactory {

        /**
         * Constructs an array of objects with null values, null
         * array elements, and 0-element arrays.  This class may only
         * be used with classes that tolerate 0-element arrays, null values,
         * null-element arrays, and default primitive values in their 
         * constructors
         */
        public static Object[] create(Class[] types) 
            throws InstantiationException, IllegalAccessException,
            InvocationTargetException
        {
            if (types == null)
                throw new IllegalArgumentException(
                    "array of types may not be null");
            if (types.length <= 0)
                throw new IllegalArgumentException(
                    "array of types may not be empty");
            ArrayList testObjs = new ArrayList();
            for (int k = 0; k < types.length; k++) {
                Class type = types[k];
                Constructor[] constructors = type.getDeclaredConstructors();
                for (int i = 0; i < constructors.length; i++) {
                    Class[] parameters = constructors[i].getParameterTypes();
                    Object[] obj = new Object[parameters.length];
                    Object[] nullObjs = new Object[parameters.length];
                    Object[] nullElements = new Object[parameters.length];
                    for (int j = 0; j < parameters.length; j++) {
                        if (parameters[j].isArray()) {
                            obj[j] = Array.newInstance(
                                parameters[j].getComponentType(), 0);
                            nullObjs[j] = null;
                            nullElements[j] = Array.newInstance(
                                parameters[j].getComponentType(), 1);
                        } else if (!parameters[j].isPrimitive()) {
                            obj[j] = null;
                            nullObjs[j] = null;
                            nullElements[j] = null;
                        } else {
                            obj[j] = getPrimitiveValue(parameters[j]);
                            nullObjs[j] = getPrimitiveValue(parameters[j]);
                            nullElements[j] = getPrimitiveValue(parameters[j]);
                        }
                    }                    
                    constructors[i].setAccessible(true);
                    testObjs.add(constructors[i].newInstance(obj));
                    testObjs.add(constructors[i].newInstance(nullObjs));
                    testObjs.add(constructors[i].newInstance(nullElements));
                }
            }
            return testObjs.toArray();
        }

        /** 
         * Returns a canonical value for a primitive type
         */
        public static Object getPrimitiveValue(Class c) {
            Object val = null;
            if (c == int.class) {
                val = new Integer(0);
            } else if (c == long.class) {
                val = new Long(0);
            } else if (c == char.class) {
                val = new Character(' ');
            } else if (c == byte.class) {
                val = new Byte((byte)0);
            } else if (c == short.class) {
                val = new Short((short)0);
            } else if (c == float.class) {
                val = new Float(0);
            } else if (c == double.class) {
                val = new Double(0);
            } else if (c == boolean.class) {
                val = new Boolean(false);
            }
            return val;
        }

    }

   
}