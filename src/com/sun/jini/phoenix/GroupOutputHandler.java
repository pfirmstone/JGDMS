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
package com.sun.jini.phoenix;

import java.io.InputStream;
import java.rmi.activation.ActivationGroupID;
import java.rmi.activation.ActivationGroupDesc;

/**
 * Defines the interface for handlers of the output of activation group
 * processes.
 *
 * @author Sun Microsystems, Inc.
 * 
 * @since 2.0
 */
public interface GroupOutputHandler {
    /**
     * Handles the output from an activation group process. The data
     * from each of the specified streams must be read (asynchronously after
     * this method returns) until EOF is reached, and processed in whatever
     * manner is deemed appropriate by the handler.
     *
     * @param id the activation group identifier of the group
     * @param desc the activation group descriptor of the group
     * @param incarnation the current incarnation number of the group
     * @param name the name of the group, in the form "Group-<i>n</i>"
     * @param out a stream that produces the standard output of the group
     * @param err a stream that produces the error output of the group
     */
    void handleOutput(ActivationGroupID id,
		      ActivationGroupDesc desc,
		      long incarnation,
		      String name,
		      InputStream out,
		      InputStream err);
}
