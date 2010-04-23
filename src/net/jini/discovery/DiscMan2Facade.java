/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package net.jini.discovery;

import net.jini.core.lookup.PortableServiceRegistrar;
import net.jini.core.lookup.ServiceRegistrar;

/**
 * This facade is intended to wrap a DiscoveryManagement2 for existing
 * applications that utilise DiscoveryManagement.
 * 
 * DiscoveryManagement wont be supported on Java CDC, it is depreciated. It is
 * intended to allow a transition period to DiscoveryManagement2 for existing software.
 * 
 * @param DiscoveryListenerManagement 
 * @author Peter Firmstone
 * @since 2.2.0
 */
public class DiscMan2Facade 
        implements DiscoveryManagement2, Facade<DiscoveryListenerManagement> {
    private final DiscoveryListenerManagement dlm;
    @SuppressWarnings("unchecked")
    public DiscMan2Facade(DiscoveryListenerManagement dm){
        if (dm == null) throw new NullPointerException("DiscoveryManager cannot be null");
        while (dm instanceof Facade){
            Facade f = (Facade) dm;
            dm = (DiscoveryListenerManagement) f.reveal();
        }
        dlm = dm;
    }

    public PortableServiceRegistrar[] getPRegistrars() {
        try {
            if (dlm instanceof DiscoveryManagement){
                ServiceRegistrar[] psr = 
                        ((DiscoveryManagement)dlm).getRegistrars();
                int l = psr.length;
                ServiceRegistrar[] sr = new ServiceRegistrar[l];
                for (int i = 0; i < l; i++){
                    if (psr[i] instanceof ServiceRegistrar){
                        sr[i] = (ServiceRegistrar) psr[i];
                    }else {
                        sr[i] = new ServiceRegistrarFacade(psr[i]);
                    }
                }
                return sr;
            }
        } catch (NoClassDefFoundError er){
            // This is expected for Java CDC.
        }
        if (dlm instanceof DiscoveryManagement2){
            DiscoveryManagement2 dm = (DiscoveryManagement2) dlm;
            return dm.getPRegistrars();
        }
        throw new UnsupportedOperationException("Not supported.");
    }

    public void discard(PortableServiceRegistrar proxy) {
        try {
            if (dlm instanceof DiscoveryManagement){
                if ( proxy instanceof ServiceRegistrar){
                    ((DiscoveryManagement) dlm).discard((ServiceRegistrar)proxy);
                } else {
                    ((DiscoveryManagement) dlm).discard(new ServiceRegistrarFacade(proxy));
                }
            }
        } catch (NoClassDefFoundError er){
            // This is expected for Java CDC.
        }
        if (dlm instanceof DiscoveryManagement2){
            DiscoveryManagement2 dm = (DiscoveryManagement2) dlm;
            dm.discard(proxy);
        }
        throw new UnsupportedOperationException("Not supported.");
    }

    public void addDiscoveryListener(DiscoveryListener listener) {
        dlm.addDiscoveryListener(listener);
    }

    public void removeDiscoveryListener(DiscoveryListener listener) {
        dlm.removeDiscoveryListener(listener);
    }

    public void terminate() {
        dlm.terminate();
    }

    public DiscoveryListenerManagement reveal() {
        return dlm;
    }
}
