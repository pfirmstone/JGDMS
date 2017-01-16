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
package net.jini.lookup.entry;

/**
 * This interface indicates that an Entry class is created and
 * modified by the service with which it is associated.  Parties other
 * than the service that registered itself should not attempt to add
 * or modify any object that implements this interface.
 * 
 * @author Sun Microsystems, Inc.
 *
 * @see net.jini.core.entry.Entry
 */
public interface ServiceControlled {
}
