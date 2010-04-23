/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package net.jini.discovery;

import java.io.ObjectInput;
import java.rmi.RemoteException;
import net.jini.core.discovery.LookupLocator;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.lookup.PortableServiceRegistrar;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceItem;
import net.jini.core.lookup.ServiceMatches;
import net.jini.core.lookup.ServiceRegistration;
import net.jini.core.lookup.ServiceTemplate;
import net.jini.core.lookup.StreamServiceRegistrar;
import net.jini.io.Convert;
import net.jini.io.MarshalledInstance;

/**
 *
 * @author Peter Firmstone
 * @since 2.2.0
 */
public class StreamServiceRegistrarFacade implements StreamServiceRegistrar, 
        Facade<PortableServiceRegistrar> {
    private final PortableServiceRegistrar psr;
    
    public StreamServiceRegistrarFacade(PortableServiceRegistrar registrar) {
        while (registrar instanceof Facade){ //always ensure we have uncovered any facades.
            @SuppressWarnings("unchecked")
                Facade<PortableServiceRegistrar> f = (Facade<PortableServiceRegistrar>) registrar;
                registrar = f.reveal();
        }
        psr = registrar;
    }

    public EventRegistration notify(MarshalledInstance handback,
            ServiceTemplate tmpl, int transitions, RemoteEventListener listener,
            long leaseDuration) throws RemoteException {
        try {
            if ( psr instanceof net.jini.core.lookup.ServiceRegistrar) {
                net.jini.core.lookup.ServiceRegistrar sr = 
                        (net.jini.core.lookup.ServiceRegistrar) psr;
                Convert convert = Convert.getInstance();
                @SuppressWarnings("unchecked")
                java.rmi.MarshalledObject hback = 
                        convert.toRmiMarshalledObject(handback);
                return sr.notify(tmpl, transitions, listener, hback, leaseDuration);
            }
            throw new UnsupportedOperationException("Unsupported Method");
        } catch (NoClassDefFoundError er ) {
            //This is normal for Java CDC.
            throw new UnsupportedOperationException("Unsupported Method");
        }
    }

    public ObjectInput lookup(ServiceTemplate tmpl, boolean marshalled)
            throws RemoteException {
        if ( psr instanceof StreamServiceRegistrar ){
            StreamServiceRegistrar ssr = (StreamServiceRegistrar) psr;
            return ssr.lookup(tmpl, marshalled);
        }
        throw new UnsupportedOperationException("Not supported yet.");
        //We could make an empty implementation that doesn't return any matches?
    }

    public Class[] getEntryClasses(ServiceTemplate tmpl) throws RemoteException {
        return psr.getEntryClasses(tmpl);
    }

    public Object[] getFieldValues(ServiceTemplate tmpl, int setIndex, String field) 
            throws NoSuchFieldException, RemoteException {
        return psr.getFieldValues(tmpl, setIndex, field);
    }

    public String[] getGroups() throws RemoteException {
        return psr.getGroups();
    }

    public LookupLocator getLocator() throws RemoteException {
        return psr.getLocator();
    }

    public ServiceID getServiceID() {
        return psr.getServiceID();
    }

    public Class[] getServiceTypes(ServiceTemplate tmpl, String prefix) 
            throws RemoteException {
        return psr.getServiceTypes(tmpl, prefix);
    }

    public Object lookup(ServiceTemplate tmpl) throws RemoteException {
        return psr.lookup(tmpl);
    }

    public ServiceMatches lookup(ServiceTemplate tmpl, int maxMatches) 
            throws RemoteException {
        return psr.lookup(tmpl, maxMatches);
    }

    public ServiceRegistration register(ServiceItem item, long leaseDuration) 
            throws RemoteException {
        return psr.register(item, leaseDuration);
    }
    
    @Override
    public boolean equals(Object obj){
        if (obj == this) return true;
        while (obj instanceof Facade){
            Facade f = (Facade) obj;
            obj = f.reveal();
        }
        if (obj instanceof PortableServiceRegistrar){
            PortableServiceRegistrar p = (PortableServiceRegistrar) obj;
            if (psr.equals(p)) return true;
        }
        return false;
    }
    
    @Override
    public int hashCode(){
        return psr.hashCode();
    }

    public PortableServiceRegistrar reveal() {
        return psr;
    }

}
