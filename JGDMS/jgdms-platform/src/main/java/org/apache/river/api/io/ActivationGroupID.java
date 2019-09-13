/*
 * Copyright 2019 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.river.api.io;

import java.io.ObjectStreamField;
import java.io.Serializable;

/**
 * Had to place this here due to readResolve method of Serializer.
 */
final class ActivationGroupID implements Serializable {

    private static final long serialVersionUID = -1648432278909740833L;
    private static final ObjectStreamField[] serialPersistentFields = {new ObjectStreamField("system", java.rmi.activation.ActivationSystem.class), new ObjectStreamField("uid", java.rmi.server.UID.class)};
    java.rmi.activation.ActivationSystem system;
    java.rmi.server.UID uid;

    ActivationGroupID() {
    }

    ActivationGroupID(java.rmi.activation.ActivationSystem system, java.rmi.server.UID uid) {
        this.system = system;
        this.uid = uid;
    }
    
}
