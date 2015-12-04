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
package org.apache.river.test.spec.jeri.basicjeriexporter.util;

//java.lang.reflect
import java.lang.reflect.Method;

/**
 * Implementation class for test remote services
 */
public class BJETestServiceImpl implements BJETestService {

    private final static String returnVal = "I did something";
    private static volatile boolean stop = false;

    public BJETestServiceImpl() {
    }

    public Object doSomething() {
        return returnVal;
    }

    public Object doSomethingLong(){
        try {
            Method m = this.getClass().getMethod("doSomethingLong",
                new Class[]{});
            BJETransportListener.getListener().called(m, this, new Object[]{});
            while (!stop) {
                Thread.sleep(1000);
            }
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InterruptedException e){
            e.printStackTrace();
        }



        return returnVal;
    }

    public void stop() {
        stop = true;
    }
}
