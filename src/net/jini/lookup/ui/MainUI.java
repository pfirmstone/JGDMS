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
package net.jini.lookup.ui;

/**
 * UI role interface implemented by Main UIs,
 * which enable client programs to grant users general access to a service.
 * If a UI descriptor's UI factory produces a UI that implements
 * this interface (i.e., produces a Main UI), the UI descriptor's
 * <CODE>role</CODE> field must reference a <CODE>String</CODE> with the value
 * <CODE>"net.jini.lookup.ui.MainUI"</CODE>.
 *
 * <P>
 * The first parameter of any factory method declared in a UI factory type is an
 * object called the "role object."
 * Any factory method that produces an Main UI must accept as the role object the
 * service item (the <CODE>net.jini.core.lookup.ServiceItem</CODE>) of the service
 * with which the Main UI is associated.
 *
 * <P>
 * Main UIs should allow clients to configure them before they
 * begin. For example, Main UIs produced by <CODE>FrameFactory</CODE>,
 * <CODE>JFrameFactory</CODE>, <CODE>WindowFactory</CODE>
 * and <CODE>JWindowFactory</CODE> (all members of the <CODE>net.jini.lookup.ui.factory</CODE> package)
 * should not be visible when they are returned from the factory method. This allows clients to set
 * the UI's position and size, for example, before making the UI
 * visible by invoking <CODE>setVisible(true)</CODE> on the UI.
 *
 * <P>
 * A client should be
 * able to invoke a Main UI factory method multiple times sequentially. In other words, if a user
 * uses a service via a Main UI, then says exit, then double clicks once again on the service icon,
 * the client can just simply invoke a UI factory method again, and get another Main UI for the same
 * service. Main UIs, therefore, should be written so that they work no matter what state
 * the service object happens to be in when the Main UI is created.
 *
 * <P>
 * It is recommended that clients use multiple Main UIs for the same service only sequentially, and
 * avoid creating multiple Main UIs for the same service that operate concurrently with one another.
 * But because some clients may create and use multiple Main UIs at the same time for the same service,
 * providers of services and main UIs should program defensively, to ensure that multiple Main UIs
 * for the same service at the same time will all work together concurrently.
 *
 * @author Bill Venners
*/

public interface MainUI {

    /**
     * Convenience constant to use in the <CODE>role</CODE>
     * field of <CODE>UIDescriptor</CODE>s for MainUI role UIs.
     * The value of this constant is <CODE>"net.jini.lookup.ui.MainUI"</CODE>.
    */
    String ROLE = "net.jini.lookup.ui.MainUI";
}

