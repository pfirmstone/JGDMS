/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package au.net.zeus.jgdms.cel;

/**
 * The eight expression types of JGDMS-STD-011 §4.1. {@code LIST} is
 * parameterised by a scalar element type at the value level ({@link
 * CelValue.ListV#elementType()}); there is no separate list-of-list, list of
 * object, etc. per §4.7.
 */
public enum CelType {
    BOOL,
    INT,
    DOUBLE,
    STRING,
    BYTES,
    NULL_T,
    LIST,
    OBJECT
}
