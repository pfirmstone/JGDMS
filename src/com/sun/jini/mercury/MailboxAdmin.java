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
package com.sun.jini.mercury;

import com.sun.jini.admin.DestroyAdmin;

import net.jini.admin.JoinAdmin;

/**
 * An administrative interface for the mercury implementation
 * of the event mailbox service.
 *
 * @author Sun Microsystems, Inc.
 *
 * @since 1.1
 */
public interface MailboxAdmin
    extends DestroyAdmin, JoinAdmin
{
    // No additional methods
}

