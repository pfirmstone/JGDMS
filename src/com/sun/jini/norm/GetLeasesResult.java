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
package com.sun.jini.norm;

import com.sun.jini.proxy.MarshalledWrapper;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamField;
import java.io.Serializable;
import net.jini.io.MarshalledInstance;

/**
 * Holds the results of a call to {@link NormServer#getLeases
 * NormServer.getLeases}.
 */
final class GetLeasesResult implements Serializable {
    private static final long serialVersionUID = 1;

    /**
     * @serialField marshalledLeases MarshalledInstance[] The marshalled
     *		    leases.
     */
    private static final ObjectStreamField[] serialPersistentFields = {
	/* Make sure the marshalled leases array is not shared */
	new ObjectStreamField(
	    "marshalledLeases", MarshalledInstance[].class, true)
    };

    /** Whether to verify codebase integrity. */
    private transient boolean verifyCodebaseIntegrity;

    /** The marshalled leases. */
    final MarshalledInstance[] marshalledLeases;

    /**
     * Creates an object that holds the results of a call to {@link NormServerBaseImpl#getLeases
     * getLeases}.
     *
     * @param marshalledLeases the leases being returned by the call
     */
    GetLeasesResult(MarshalledInstance[] marshalledLeases) {
	this.marshalledLeases = marshalledLeases;
    }

    /**
     * Returns whether to verify codebase integrity when unmarshalling leases.
     */
    boolean verifyCodebaseIntegrity() {
	return verifyCodebaseIntegrity;
    }

    /* Set transient fields. */
    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();
	verifyCodebaseIntegrity = MarshalledWrapper.integrityEnforced(in);
    }
}
