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
package org.apache.river.test.impl.reliability;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Represents one remote party of the deep 2-party recursion implemented by
 * this RMI reliability test. An OrangeEcho instance recursively calls back 
 * to it's caller, an Orange instance.
 * The recursion stops when it reaches a given 'level'.
 */
public interface OrangeEcho extends Remote {
    int[] recurse(Orange orange, int[] message, int level)
	throws RemoteException;
}
