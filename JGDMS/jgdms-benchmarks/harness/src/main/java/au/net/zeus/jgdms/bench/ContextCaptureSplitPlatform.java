/*
 * Copyright (C) 2026 the original author or authors.
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
package au.net.zeus.jgdms.bench;

import org.openjdk.jmh.annotations.Fork;

/**
 * {@link ContextCaptureSplit} on a platform thread, SecurityManager installed.
 * Written with AI assistance (Claude); see the README.
 */
@Fork(value = 2,
        jvmArgsPrepend = {ContextCaptureSplit.ADD_OPENS_1, ContextCaptureSplit.ADD_OPENS_2},
        jvmArgsAppend = "-Djava.security.manager=default")
public class ContextCaptureSplitPlatform extends ContextCaptureSplit {
}
