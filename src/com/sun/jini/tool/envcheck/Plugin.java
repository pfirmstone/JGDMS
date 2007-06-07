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
package com.sun.jini.tool.envcheck;

/**
 * A test plugin.
 */
public interface Plugin {

    /** 
     * Run the test implemented by the plugin.
     *
     * @param envCheck a reference to the driver instance.
     */
    public void run(EnvCheck envCheck);

    /**
     * Determine whether <code>arg</code> is a plugin-specific command-line
     * option for this plugin and save any necessary state. State should
     * be saved in static fields since plugin instances may not be cached.
     *
     * @param arg the command-line option to examine
     * @return true if the plugin supports this option
     */
    public boolean isPluginOption(String arg);
}
