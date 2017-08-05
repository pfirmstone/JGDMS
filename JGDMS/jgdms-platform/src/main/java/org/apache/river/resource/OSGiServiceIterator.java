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
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.util.tracker.ServiceTracker;

/**
 *
 * @author peter
 */
public class OSGiServiceIterator implements BundleActivator {
    
    private static OSGiServiceIterator osi;
    
    static <S> Iterator<S> providers(Class<S> service){
	return new ServiceIterator(service, osi.bundleContext);
    }
    
    private BundleContext bundleContext;
    
    public OSGiServiceIterator(){}

    public void start(BundleContext bc) throws Exception {
	if (osi != null) throw new IllegalArgumentException("start may only be called once");
	Service.setOsgi();
	bundleContext = bc;
	osi = this;
    }

    public void stop(BundleContext bc) throws Exception {
	bundleContext = null;
	osi = null;
    }
    
    private static class ServiceIterator<S> implements Iterator<S> {
	private final S[] instances;
	private int index;
	
	ServiceIterator(Class<S> service, BundleContext bc){
	    ServiceTracker st = new ServiceTracker(bc, service.getCanonicalName(), null);
	    Object[] services = st.getServiceReferences();
	    List<S> matches = new ArrayList<S>(services.length);
	    for(int i=0, l=services.length; i<l; i++){
		if (service.isInstance(services[i])) matches.add((S) services[i]);
	    }
	    instances = (S[]) matches.toArray();
	    index = 0;
	}

	public boolean hasNext() {
	    return index < instances.length;
	}

	public S next() {
	    if (!hasNext()) throw new NoSuchElementException("End reached");
	    return instances[index++];
	}
	
    }
}
