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
package org.apache.river.test.spec.activation.util;
import java.rmi.server.UID;
import java.util.logging.Logger;
import java.util.logging.Level;
import net.jini.activation.arg.ActivationGroupID;
import net.jini.activation.arg.ActivationSystem;
import net.jini.activation.ActivationGroupIDImpl;

/**
 * A fake implementation of the <code>ActivationGroupID</code>
 * class.
 */
public class FakeActivationGroupID implements ActivationGroupID {
    private Logger logger;
    private ActivationGroupID groupID;

    /**
     * Stores logger and calls superclass constructor passing
     * ActivationSystem as paparameter
     */
    public FakeActivationGroupID(Logger logger, ActivationSystem system) {
        this.groupID = new ActivationGroupIDImpl(system);
        this.logger = logger;
    }

    /**
     * works same as getSystem() method of superclass, but send debug message
     * in log
     */
    public ActivationSystem getSystem() {
        ActivationSystem system = groupID.getSystem();
        logger.log(Level.FINEST, "system=" + system.toString());
	return system;
    }
    
    @Override
    public UID getUID(){
        return groupID.getUID();
    }
    
}
