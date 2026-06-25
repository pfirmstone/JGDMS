/*
 * Copyright 2017 The Apache Software Foundation.
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

package org.apache.river.resource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleReference;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;

/**
 * Finds {@link Service} providers in the OSGi service registry, for the
 * cross-bundle SPIs (discovery providers, {@code Configuration}, marshal
 * factories, ...) that classpath {@code META-INF/services} scanning cannot
 * reach across bundle boundaries. Activated as a {@link BundleActivator},
 * which records the platform bundle's context and flips {@link Service} into
 * OSGi mode.
 *
 * <p>This serves only the <em>cross-bundle</em> relationship. Providers that
 * are <em>co-loaded</em> with the requesting class (a per-package
 * {@code MarshalDelegate} in the same loader, including non-bundle proxy
 * codebase jars) are found by {@code Service}'s loader-scoped
 * {@code META-INF/services} scan instead, and the two are unioned.
 *
 * @author peter
 */
public class OSGiServiceIterator implements BundleActivator {

    private static volatile OSGiServiceIterator osi;

    /**
     * Cross-bundle SPI providers of {@code service} from the OSGi service
     * registry, scoped to {@code loader}'s bundle. The scope is the nearest
     * bundle in {@code loader}'s parent chain (so a proxy codebase loader
     * resolves to its client bundle parent), falling back to the platform
     * bundle context when the requester is not in a bundle.
     */
    static <S> Iterator<S> providers(Class<S> service, ClassLoader loader) {
	OSGiServiceIterator activator = osi;
	if (activator == null) {
	    return Collections.<S>emptyIterator();
	}
	BundleContext bc = contextFor(loader, activator.bundleContext);
	if (bc == null) {
	    return Collections.<S>emptyIterator();
	}
	List<S> instances = new ArrayList<S>();
	try {
	    // null filter is always syntactically valid; class name is the
	    // binary name services are registered under.
	    ServiceReference<?>[] refs =
		    bc.getServiceReferences(service.getName(), null);
	    if (refs != null) {
		for (ServiceReference<?> ref : refs) {
		    Object svc = bc.getService(ref);
		    // The service is held for the provider's lifetime -- these
		    // SPIs are long-lived singletons -- so it is not ungot here.
		    if (service.isInstance(svc)) {
			instances.add(service.cast(svc));
		    }
		}
	    }
	} catch (InvalidSyntaxException impossible) {
	    // unreachable with a null filter
	} catch (RuntimeException e) {
	    // A registry failure must never break provider discovery; the
	    // loader-scoped scan in Service still runs.
	}
	return instances.iterator();
    }

    /**
     * The bundle context to scope the registry lookup to: the nearest bundle
     * in {@code loader}'s parent chain (the parent-loader rule -- a proxy
     * codebase loader resolves to its client bundle parent), or
     * {@code fallback} (the platform context) when the requester is not in a
     * bundle, so a non-bundle requester still sees cross-bundle SPIs.
     */
    private static BundleContext contextFor(ClassLoader loader, BundleContext fallback) {
	for (ClassLoader cl = loader; cl != null; cl = cl.getParent()) {
	    if (cl instanceof BundleReference) {
		Bundle b = ((BundleReference) cl).getBundle();
		if (b != null) {
		    BundleContext c = b.getBundleContext();
		    if (c != null) {
			return c;
		    }
		}
	    }
	}
	return fallback;
    }

    private volatile BundleContext bundleContext;

    public OSGiServiceIterator() {}

    @Override
    public void start(BundleContext bc) throws Exception {
	if (osi != null) {
	    throw new IllegalStateException("start may only be called once");
	}
	bundleContext = bc;
	osi = this;
	Service.setOsgi();
    }

    @Override
    public void stop(BundleContext bc) throws Exception {
	osi = null;
	bundleContext = null;
    }
}
