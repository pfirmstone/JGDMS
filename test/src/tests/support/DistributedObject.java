/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package tests.support;

import java.io.Serializable;
import org.apache.river.api.io.Distributed;
import org.apache.river.api.io.SerialFactory;

/**
 *
 * @author peter
 */
public class DistributedObject implements Distributed {
    
    public static DistributedObject create(String str){
        return new DistributedObject(str);
    }
    
    private final String testString;
    /* 0 - constructor(String)
     * 1 - static factory method
     * 2 - builder
     * 3 - constructor(Boolean)
     * 4 - constructor(Character)
     */
    private final int method;
    
    public DistributedObject(String str){
        testString = str;
        method = 0;
    }
    
    public DistributedObject(Number num){
        testString = num.toString();
        method = 5;
    }
    
    public DistributedObject(Character ch){
        testString = ch.toString();
        method = 4;
    }
    
    public DistributedObject(Boolean b){
        testString = b.toString();
        method = 3;
    }
    
    public DistributedObject(boolean b){
        testString = Boolean.toString(b);
        method = 6;
    }
    
    public DistributedObject(String str, int method){
        testString = str;
        this.method = method;
    }

    public SerialFactory substitute() {
        Class[] signature = new Class[1];
        Object[] parameters = new Object[1];
        parameters[0] = testString;
        switch (method){
            case 0: signature[0] = String.class;
                    return new SerialFactory(this.getClass(), null, signature, parameters );
        
            case 1 :
                    signature[0] = String.class;
                    return new SerialFactory(this.getClass(), "create", signature, parameters);
        
            case 2:
                    Builder builder = new Builder().setString(testString);
                    return new SerialFactory(builder, "build", null, null);
            case 3:
                    signature[0] = Boolean.class;
                    parameters[0] = Boolean.valueOf(testString);
                    return new SerialFactory(this.getClass(), null, signature, parameters);
            case 4:
                    signature[0] = Character.class;
                    parameters[0] = Character.valueOf(testString.charAt(0));
                    return new SerialFactory(this.getClass(), null, signature, parameters);
            case 6:
                    signature[0] = Boolean.TYPE;
                    parameters[0] = Boolean.valueOf(testString);
                    return new SerialFactory(this.getClass(), null, signature, parameters);
            default:
                    return null;
        }
    }
    
    public String toString(){
        return testString;
    }
    
    public static class Builder implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private String str;
        
        public Builder(){
            
        }
        
        public Builder setString(String str){
            this.str = str;
            return this;
        }
        
        public DistributedObject build(){
            return new DistributedObject(str);
        }
    }
    
}
