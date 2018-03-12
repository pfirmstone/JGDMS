/*
 * Copyright 2018 peter.
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
package net.jini.export;

/**
 * A marker interface for a dynamic proxy.  Services that don't 
 * use a smart proxy, and utilize only a dynamic proxy, but still need a
 * codebase to resolve interface classes should implement this interface.
 * 
 * @author peter
 */
public interface DynamicProxyCodebaseAccessor extends CodebaseAccessor {
    
}
