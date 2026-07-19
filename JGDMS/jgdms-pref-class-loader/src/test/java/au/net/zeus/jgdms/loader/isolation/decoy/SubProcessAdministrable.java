/*
 * Copyright 2026 The Apache Software Foundation.
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
package au.net.zeus.jgdms.loader.isolation.decoy;

/**
 * Adversarial decoy: an interface <strong>literally named</strong>
 * {@code SubProcessAdministrable} but in a different package, hence a distinct
 * {@link Class} identity from the real
 * {@code au.net.zeus.jgdms.loader.isolation.SubProcessAdministrable}.
 *
 * <p>Used to prove {@code HostedProxyGuard} keys on resolved <em>type
 * identity</em>, not on a name string: a proxy declaring this same-named-but-
 * different-identity interface cannot be cast to or dispatched as the real
 * management interface and is therefore harmless; a correct identity-based
 * guard must NOT reject it, whereas a buggy name-based guard would.
 */
public interface SubProcessAdministrable {
    Object getSubProcessPolicyAdmin();
}
