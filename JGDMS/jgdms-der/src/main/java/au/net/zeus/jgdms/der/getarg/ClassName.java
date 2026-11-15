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

package au.net.zeus.jgdms.der.getarg;

import java.util.Objects;

/**
 * A {@code java.lang.Class} field's value as it is carried on the wire: the class's name. It is
 * what a class-free decode returns in place of a {@link Class} (JGDMS-STD-006 S3.11): no class
 * is resolved or loaded to produce it, so the name has not been checked against any class.
 *
 * <p>A wire-null {@code Class} field decodes to {@code null}, not to a {@code ClassName}.
 *
 * @param name the class name as carried in the payload (never {@code null})
 */
public record ClassName(String name) {

    public ClassName {
        Objects.requireNonNull(name, "name");
    }
}
