/*
 *  DemoService.java
 * 
 */

package org.apache.river.examples.federation;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 *
 */
public interface DemoService
    extends Remote
{
    public String hello( String msg ) throws RemoteException ;
}
