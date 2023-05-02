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

package net.jini.lookup;

import  net.jini.core.lookup.ServiceItem;

/** 
 * The <code>ServiceItemFilter</code> interface defines the methods used by
 * an object such as the {@link net.jini.lookup.ServiceDiscoveryManager
 * ServiceDiscoveryManager} or the {@link net.jini.lookup.LookupCache 
 * LookupCache} to apply additional selection criteria when searching for
 * services in which an entity has registered interest. It is the
 * responsibility of the entity requesting the application of additional
 * criteria to construct an implementation of this interface that defines
 * the additional criteria, and to pass the resulting object (referred to
 * as a <i>filter</i>) into the object that will apply it.
 * <p>
 * The filtering mechanism provided by implementations of this interface is
 * particularly useful to entities that wish to extend the capabilities of
 * the standard template matching scheme. For example, because template
 * matching does not allow one to search for services based on a range of
 * attribute values, this additional matching mechanism can be exploited by
 * the entity to ask the managing object to find all registered printer
 * services that have a resolution attribute between say, 300 dpi and
 * 1200 dpi.
 * <p>
 * In addition to (or instead of) applying additional matching criteria to
 * candidate service proxies initially found through template matching, this
 * filtering mechanism can also be used to extend the selection process so
 * that only proxies that are <i>safe</i> to use are returned to the entity.
 * To do this, the entity would use this interface to supply the
 * {@link net.jini.lookup.ServiceDiscoveryManager ServiceDiscoveryManager}
 * or {@link net.jini.lookup.LookupCache LookupCache} with a filter that,
 * when applied to a candidate proxy, performs a set of operations that
 * is referred to as <i>proxy preparation</i>. As described in the 
 * documentation for {@link net.jini.security.ProxyPreparer}, proxy
 * preparation typically includes operations such as, verifying trust
 * in the proxy, specifying client constraints, and dynamically granting
 * necessary permissions to the proxy.
 * 
 * @author Sun Microsystems, Inc.
 *
 * @see ServiceDiscoveryManager
 */
public interface ServiceItemFilter {

    /**
     * <p>
     * This method defines the implementation of the additional selection
     * criteria (additional matching and/or proxy preparation) to apply to a
     * {@link net.jini.core.lookup.ServiceItem ServiceItem} object found
     * through standard template matching. This method takes one argument:
     * the {@link net.jini.core.lookup.ServiceItem ServiceItem} object to
     * test against the additional criteria.</p>
     * <p> 
     * Neither a <code>null</code> reference nor a
     * {@link net.jini.core.lookup.ServiceItem ServiceItem} object containing
     * <code>null</code> fields will be passed to this method by the 
     * {@link net.jini.lookup.ServiceDiscoveryManager ServiceDiscoveryManager}
     * or the {@link net.jini.lookup.LookupCache LookupCache}.</p>
     * <p>
     * If the parameter passed to this method is a
     * {@link net.jini.core.lookup.ServiceItem ServiceItem} object that has
     * non-<code>null</code> fields but is associated with attribute sets
     * containing <code>null</code> entries, then this method must process
     * that parameter in a reasonable manner.</p>
     * <p>
     * Note that although this method returns a <code>boolean</code>, there
     * are actually three possible return states that can occur. Those states
     * are classified by the value of the returned <code>boolean</code> in
     * combination with the (possibly modified) contents of the
     * {@link net.jini.core.lookup.ServiceItem ServiceItem} object that was
     * input to this method. The three possible return states can be
     * summarized as follows:
     * </p>
     * <ul>
     *   <li> If the input object satisfies any additional matching criteria
     *        that are specified, and if the proxy is successfully prepared
     *        (when requested), then this method returns <code>true</code>
     *        and the service field of the
     *        {@link net.jini.core.lookup.ServiceItem ServiceItem} parameter
     *        is either left unchanged (when proxy preparation is not
     *        requested) or is <b><i>replaced</i></b> with the prepared proxy.
     *        When this state is returned by this method, it is said that the
     *        object <i>passed</i> (the <code>check</code> method of) the
     *        filter; or that the filter returned a <i>pass</i> condition.
     *   <li> If either the input object does not satisfy any additional
     *        matching criteria that are specified, or if proxy preparation
     *        is requested but fails because of a <i>definite exception</i>
     *        (such as a {@link java.lang.SecurityException SecurityException},
     *        then this method returns <code>false</code>. When this state is
     *        returned by this method, it is said that the object
     *        <i>failed</i> (the <code>check</code> method of) the filter;
     *        or that the filter returned a <i>failure</i> condition.
     *   <li> If the input object satisfies any additional matching criteria
     *        that are specified, and proxy preparation is requested but
     *        fails because of an <i>indefinite exception</i> (such as a
     *        {@link java.rmi.RemoteException RemoteException}), then this
     *        method returns <code>true</code> and the service field of the
     *        {@link net.jini.core.lookup.ServiceItem ServiceItem} parameter
     *        is <b><i>replaced</i></b> with <code>null</code>. In this case,
     *        the object has neither passed nor failed the filter. Thus, when
     *        this state is returned by this method, it is said that the
     *        results of the filtering process are <i>indefinite</i>.
     * </ul>
     * <p>
     * With respect to a remote operation such as proxy preparation, the
     * term <i>indefinite exception</i> refers to a class of exception where
     * any such exception does not allow assertions to be made about the
     * probability of success (or failure) of future attempts to prepare the
     * proxy. A {@link java.rmi.RemoteException RemoteException} caused by a
     * transient communciation failure is one such example of an exception
     * that can be classified as an indefinite exception. Thus, whenever
     * this method returns an indefinite result, the object that invoked
     * this method (either {@link net.jini.lookup.ServiceDiscoveryManager
     * ServiceDiscoveryManager} or {@link net.jini.lookup.LookupCache
     * LookupCache}) will retry the filter by calling this method again,
     * at a later time, when success may be possible.
     * <p>
     * Alternatively, the term <i>definite exception</i> refers to a 
     * class of exception where any such exception is indicative of a
     * <b><i>permanent</i></b> failure. That is, when an operation fails
     * as a result of an exception that can be classified as a definite
     * exception, that exception allows one to assert that any future
     * attempts to perform the failed operation will also be met with failure.
     * A {@link java.lang.SecurityException SecurityException} is an example
     * of a definite exception in the case of proxy preparation. Thus, when
     * this method results in failure, that failure occurs either because
     * the object being filtered does not currently match the given
     * criteria, or a definite exception occurs as a result of proxy
     * preparation (or both). In either case, because it is a virtual
     * certainty that failure will again result on all future attempts to
     * filter the object (that is, perform matching and/or proxy preparation),
     * no attempt is made to retry the operation.
     * <p>
     * Except for the modifications that may result from filtering as
     * described above, this method must not modify any other aspect
     * of the contents of the input {@link net.jini.core.lookup.ServiceItem
     * ServiceItem} object because doing so can result in unpredictable and
     * undesirable effects on future processing by the
     * {@link net.jini.lookup.ServiceDiscoveryManager ServiceDiscoveryManager}.
     * Therefore, the effects of such modifications are undefined.
     * 
     * @param item the <code>ServiceItem</code> object to test against the 
     *             additional criteria.
     *
     * @return <code>false</code> if the input object fails the filter;
     *         <code>true</code> otherwise (see the method description above).
     */
    boolean  check(ServiceItem item);
}
