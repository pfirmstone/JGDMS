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
 * An enum field's value as it is carried on the wire: the enum's class name, taken from the
 * field's declared wire type ({@code "enum:<className>"}), and the constant's name, taken from
 * the payload. It is what a class-free decode returns in place of an {@link Enum} constant
 * (JGDMS-STD-006 S3.11): no class is resolved, loaded or initialised to produce it, so neither
 * name has been checked against any class. Whether the class exists, is an enum, or still
 * declares the constant is decided only when an object is constructed.
 *
 * <p>A wire-null enum field decodes to {@code null}, not to an {@code EnumName}.
 *
 * @param className    the enum's class name as declared in the schema (never {@code null})
 * @param constantName the constant's name as carried in the payload (never {@code null})
 */
public record EnumName(String className, String constantName) {

    public EnumName {
        Objects.requireNonNull(className, "className");
        Objects.requireNonNull(constantName, "constantName");
    }
}
