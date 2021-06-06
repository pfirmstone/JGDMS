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
import net.jini.activation.arg.ActivationDesc;
import net.jini.activation.arg.ActivationException;
import net.jini.activation.arg.ActivationGroupID;
import net.jini.activation.arg.MarshalledObject;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
import org.apache.river.impl.Messages;

@AtomicSerial
public final class ActivationDescImpl implements Serializable, ActivationDesc {
    private static final long serialVersionUID = 7455834104417690957L;
    
    public static SerialForm [] serialForm(){
        return new SerialForm[]{
            new SerialForm("groupID", ActivationGroupID.class),
            new SerialForm("className", String.class),
            new SerialForm("location", String.class),
            new SerialForm("data", MarshalledObject.class),
            new SerialForm("restart", Boolean.TYPE)
        };
    }
    
    public static void serialize(PutArg arg, ActivationDescImpl desc) throws IOException {
        arg.put("groupID", desc.groupID);
        arg.put("className", desc.className);
        arg.put("location", desc.location);
        arg.put("data", desc.data);
        arg.put("restart", desc.restart);
        arg.writeArgs();
    }

    /**
     * @serial The ActivationGroupID of the object.
     */
    private final ActivationGroupID groupID;

    /**
     * @serial The className of the object.
     */
    private final String className;

    /**
     * @serial The location(<i>codebase/URLs</i>) from which the class of the object can be loaded.
     */
    private final String location;

    /**
     * @serial MarshalledInstance that contain object-specific initialization data used during each activation.
     */
    private final MarshalledObject data;

    /**
     * @serial If the object requires restart service, restart should be true. If restart is false, the object is simply activated upon demand.
     */
    private final boolean restart;
    
    public ActivationDescImpl(GetArg arg) throws IOException, ClassNotFoundException {
        this(
            arg.get("groupID", null, ActivationGroupIDImpl.class),
            arg.get("className", null, String.class),
            arg.get("location", null, String.class),
            arg.get("data", null, MarshalledObject.class),
            arg.get("restart", false)
        );
    }

    public ActivationDescImpl(String className, String location, MarshalledObject data)
            throws ActivationException {
        this(ActivationGroup.currentGroupID(),
            className,
            location,
            data,
            false,
            check(ActivationGroup.currentGroupID())
        );
    }

    public ActivationDescImpl(String className, String location, MarshalledObject data,
            boolean restart) throws ActivationException {
        this(ActivationGroup.currentGroupID(),
            className,
            location,
            data,
            restart,
            check(ActivationGroup.currentGroupID())
        );
    }

    public ActivationDescImpl(ActivationGroupID groupID, String className, String location,
            MarshalledObject data) {
        this(groupID, className, location, data, false, check(groupID));
    }

    public ActivationDescImpl(ActivationGroupID groupID, String className, String location,
            MarshalledObject data, boolean restart) {
        this(groupID, className, location, data, restart, check(groupID));
    }
    
    private ActivationDescImpl(ActivationGroupID groupID, String className, String location,
            MarshalledObject data, boolean restart, boolean check) {
        
        this.groupID = groupID;
        this.className = className;
        this.location = location;
        this.data = data;
        this.restart = restart;
    }
    
    private static boolean check(ActivationGroupID groupID){
        if (groupID == null) {
            // rmi.10=The groupID can't be null.
            throw new IllegalArgumentException(Messages.getString("rmi.10")); //$NON-NLS-1$
        }
        return true;
    }

    @Override
    public ActivationGroupID getGroupID() {
        return groupID;
    }

    @Override
    public MarshalledObject getData() {
        return data;
    }

    @Override
    public String getLocation() {
        return location;
    }

    @Override
    public String getClassName() {
        return className;
    }

    @Override
    public boolean getRestartMode() {
        return restart;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ActivationDescImpl) {
            ActivationDescImpl objCasted = (ActivationDescImpl) obj;
            boolean p0, p1, p2, p3, p4;
            p0 = (groupID == null) ? objCasted.groupID == null : groupID
                    .equals(objCasted.groupID);
            p1 = (className == null) ? objCasted.className == null : className
                    .equals(objCasted.className);
            p2 = (location == null) ? objCasted.location == null : location
                    .equals(objCasted.location);
            p3 = (data == null) ? objCasted.data == null : data.equals(objCasted.data);
            p4 = (restart == objCasted.restart);
            return p0 && p1 && p2 && p3 && p4;
        }
        return false;
    }

    @Override
    public int hashCode() {
        int groupID_Hash = (groupID == null) ? 0 : groupID.hashCode();
        int className_Hash = (className == null) ? 0 : className.hashCode();
        int location_Hash = (location == null) ? 0 : location.hashCode();
        int data_Hash = (data == null) ? 0 : data.hashCode();
        int restart_Hash = (restart == false) ? 0 : 1;
        int hashCode = groupID_Hash ^ className_Hash ^ location_Hash ^ data_Hash ^ restart_Hash;
        return hashCode;
    }
}
