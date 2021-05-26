/*
 * Copyright 2016 Apache Software Foundation.
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

package com.sun.jini.start;

/**
 *
 */
@Deprecated
public final class HTTPDStatus {
    
    private HTTPDStatus(){}
    
    public static void main(String[] args) {
	org.apache.river.start.HTTPDStatus.main(args);
    }
    
    public static void httpdWarning(String codebase) {
	org.apache.river.start.HTTPDStatus.httpdWarning(codebase);
    }
}
