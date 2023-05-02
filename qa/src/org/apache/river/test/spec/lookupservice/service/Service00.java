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
package org.apache.river.test.spec.lookupservice.service;
import java.io.IOException;
import java.io.Serializable;
import java.rmi.server.ExportException;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.core.entry.Entry;
import net.jini.core.lookup.ServiceRegistration;
import net.jini.export.ProxyAccessor;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
import org.apache.river.qa.harness.QAConfig;

@AtomicSerial
public class Service00 implements Serializable, ProxyAccessor, ServiceRegInitializer {
    
    public static SerialForm[] serialForm(){
        return new SerialForm[]{
            new SerialForm("i", Integer.TYPE)
        };
    }
    
    public static void serialize(PutArg arg, Service00 s) throws IOException{
        arg.put("i", s.i);
        arg.writeArgs();
    }
    
    public int i;
    private transient BootStrapService service;
    
    public Service00(int i) {
        this.i = i;
	service = new BootStrapService(this, QAConfig.getConfig().getConfiguration());
	try {
	    service.start();
	} catch (Exception ex) {
	    Logger.getLogger(Service00.class.getName()).log(Level.SEVERE, null, ex);
	}
    }

    public Service00(GetArg arg) throws IOException{
	i = arg.get("i", 0);
	service = null; // Not local.
    }

    public boolean equals(Object obj) {
        try {
            if ( this == obj ) {
                return true;
            } else if (    ((obj.getClass()).equals(Service00.class))
                        && (((Service00)obj).i == i) ) {
                return true;
            } else {
                return false;
            }
        } catch (NullPointerException e) {
            return false;
	}
    }

    @Override
    public Object getProxy() {
	return service == null ? null : service.getBootStrapProxy();
    }

    @Override
    public ServiceRegistration setServiceRegistration(ServiceRegistration regist, Entry[] regAttr) {
	return service.setServiceRegistration(regist, regAttr);
    }
}
