/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.jini.activation;

import java.io.IOException;
import java.io.Serializable;
import java.rmi.server.UID;
import net.jini.activation.arg.ActivationGroupID;
import net.jini.activation.arg.ActivationSystem;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
import org.apache.river.api.io.Valid;

@AtomicSerial
public class ActivationGroupIDImpl implements Serializable, ActivationGroupID {
    private static final long serialVersionUID = -1648432278909740833L;
    
    public static SerialForm [] serialForm(){
        return new SerialForm []{
            new SerialForm("uid", UID.class),
            new SerialForm("system", ActivationSystem.class)
        };
    }
    
    public static void serialize(PutArg arg, ActivationGroupIDImpl id) throws IOException{
        arg.put("uid", id.uid);
        arg.put("system", id.system);
        arg.writeArgs();
    }

    private final UID uid;

    private final ActivationSystem system;

    public ActivationGroupIDImpl(ActivationSystem system) {
        this(new UID(), notNull(system));
    }
    
    private static <T> T notNull(T t){
        if (t == null) throw new NullPointerException("arguement cannot be null");
        return t;
    }
    
    public ActivationGroupIDImpl(GetArg arg) throws IOException, ClassNotFoundException{
        this(Valid.notNull(arg.get("uid", null, UID.class), "uid cannot be null"),
             Valid.notNull(arg.get("system", null, ActivationSystem.class),
                     "ActivationSystem cannot be null")
        );
    }
    
    private ActivationGroupIDImpl(UID uid, ActivationSystem system){
        this.system = system;
        this.uid = uid;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ActivationGroupID) {
            ActivationGroupID id = (ActivationGroupID) obj;
            return (uid.equals(id.getUID()) && system.equals(id.getSystem()));
        }
        return false;
    }

    @Override
    public ActivationSystem getSystem() {
        return system;
    }

    @Override
    public int hashCode() {
        return uid.hashCode();
    }

    @Override
    public String toString() {
        return "ActivationGroupID[" + uid + "; " + system + "]"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    @Override
    public UID getUID() {
        return uid;
    }
}
