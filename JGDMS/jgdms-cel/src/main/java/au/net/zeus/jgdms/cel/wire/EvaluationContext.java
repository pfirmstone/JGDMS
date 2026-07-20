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

package au.net.zeus.jgdms.cel.wire;

/**
 * Appendix B §B.3's {@code EvaluationContext} -- the context tag alone
 * selects predicate vs transform (STD-011 §11.3 item 4); a predicate's
 * result type is always {@code bool} (§9.4) so {@link Predicate} carries
 * nothing further.
 */
public sealed interface EvaluationContext {

    record Predicate() implements EvaluationContext {}

    record Transform(DeclaredResultType resultType) implements EvaluationContext {
        public Transform {
            if (resultType == null) throw new NullPointerException("resultType");
        }
    }
}
