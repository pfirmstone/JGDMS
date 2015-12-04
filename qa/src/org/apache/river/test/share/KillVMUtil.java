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

package org.apache.river.test.share;

//import org.apache.river.start.ClassLoaderUtil;

import java.io.IOException;

import java.net.MalformedURLException;

import java.rmi.activation.Activatable;
import java.rmi.activation.ActivationDesc;
import java.rmi.activation.ActivationException;
import java.rmi.activation.ActivationGroupID;
import java.rmi.activation.ActivationID;

import java.rmi.MarshalledObject;
import java.rmi.Remote;

import java.rmi.RemoteException;

/**
 * This class contains a set of static classes and methods that provide 
 * general-purpose functions related to shutting down an ActivationGroup (VM)
 * in which one or activatable processes are executing. This utility class
 * is intended to be useful to all categories of tests that wish to simulate
 * a system crash. For example, tests that verify the ability of an 
 * application to persist its state may find this utility particularly
 * useful.
 */
public class KillVMUtil {
    /** Utility method used to kill the VM (ActivationGroup) referenced
     *  by the <code>gid</code> parameter. If an activatable service is
     *  started in NON-restartable mode, calling this method with
     *  the <code>ActivationGroupID</code> of that service's
     *  <code>ActivationGroup</code> will cause a special object to be
     *  registered with that VM; an object whose only function is to
     *  perform a <code>System.exit</code> on the VM. After registering
     *  this special object with the indicated <code>ActivationGroup</code>,
     *  this method invokes the killVM() method on that object, resulting
     *  in the stoppage of all processes running in the VM.
     *
     *  Note that if the original service was started in restart mode,
     *  after stopping the VM, RMID will simply re-spawn the VM and 
     *  re-start the service. 
     */
    public static void killActivationGroup(ActivationGroupID gid)
                                   throws MalformedURLException,
                                          ActivationException, RemoteException
    {
        String implClass = KillVMObjectImpl.class.getName();
	String codeLocation = null; //BDJ - TEMP WORKAROUND FOR COMPILE
//          String codeLocation = ClassLoaderUtil.URLsToString
//                                (ClassLoaderUtil.getClasspathURLs
//  	                             (System.getProperty("java.class.path")));
        ActivationDesc desc = new ActivationDesc(gid,implClass,
                                                 codeLocation,null,false);
        KillVMObject serverStub = (KillVMObject)(Activatable.register(desc));
        long delay = serverStub.killVM();        
        try {Thread.sleep(delay);} catch (InterruptedException e) { 
            Thread.currentThread().interrupt();
        }
    }//end killActivationGroup

    /** Remote interface that should be implemented by any object desiring
     *  the capability to abruptly stop all processes in a given
     *  ActivationGroup (VM).
     */
    public interface KillVMObject extends Remote {
        /** Abruptly stops the VM in which this method's implementation
         *  executes.
         *  
         * @return a <code>long</code> value representing an estimate of the
         *         number of milliseconds that will elapse before the VM is
         *         completely shutdown. The caller of this method should not
         *         call any processes in this VM until after this time has
         *         elapsed.
         */
        public long killVM() throws java.rmi.RemoteException;
    }//end interface KillVMObject

    /** Special activatable class whose instance, once registered with a
     *  given ActivationGroup (VM), can be used to completely stop (or "kill")
     *  all processes registered and running in that VM.
     *
     *  Note that because calls to this class' killVM() method are
     *  asynchronous, certain requirements are imposed on the implementation.
     *  If the VM is exited from the current thread, rather than from a
     *  separate thread, entities which invoke this method will always
     *  receive a <code>RemoteException</code> because exiting the VM in
     *  the current thread will break the communication link between the
     *  frontend and the backend of the service. Thus, the VM must be
     *  exited from a separate thread.
     *  
     *  Another implementation requirement, related to the thread requirement,
     *  involves preventing <code>RemoteException</code>s through the
     *  introduction of timing delays. If the VM is exited before the remote
     *  call has had a chance to complete, just as with the case of exiting
     *  the VM from the current thread, the communication link between the
     *  this class' frontend and backend will be broken, resulting in a
     *  <code>RemoteException</code>. Thus, not only must the VM be exited
     *  from a separate thread, but it also must be exited only after the
     *  remote call to this method has completed. By instructing the thread
     *  that exits the VM to delay a certain amount of time before actually
     *  exiting the VM, this method attempts to "guarantee" that the VM will
     *  not be exited until the remote call has completed. Note that the
     *  amount of time to delay is simply a rough estimate of the amount
     *  of time a typical remote call should take to complete. Thus, a
     *  <code>RemoteException</code> may occur because the time delay is
     *  too small.
     *
     *  Next, note that the use of the killVM() method also imposes a related
     *  timing requirement on the client-side entity. That is, when an
     *  entity invokes this method, it must also delay a certain amount
     *  time before attempting to again interact with any process that
     *  was registered with the killed VM in restart mode. This delay is
     *  necessary to allow such services to be re-activated and to recover
     *  any persisted state. The killVM() method returns a <code>long</code>
     *  value that represents the amount of time the client-side entity
     *  should delay before again attempting to interact with those
     *  restartable processes. As with the server side time estimate,
     *  this time value is a rough estimate, and may need to be adjusted
     *  if the entity experiences <code>RemoteException</code>s on a
     *  regular basis when the entity attempts to interact with any
     *  restarted processes after invoking this method.
     *
     *  Finally, note that because this class is an inner class, it must 
     *  be declared static. If it is not declared static, misleading
     *  ActivationExceptions will occur that seem to indicate a missing
     *  activation constructor (the exception will also say something about
     *  and problems while intializing (<init>). These exceptions occur
     *  because the non-static inner class will have an implicit reference
     *  to the outter class which is not registered with the activation
     *  system.
     */
    public static class KillVMObjectImpl implements KillVMObject {
        public KillVMObjectImpl(ActivationID activationID,
                                MarshalledObject data) throws IOException
        {
            Activatable.exportObject(this, activationID, 0);
        }//end constructor
        public long killVM() throws RemoteException {
            long delay = 20; // wait N milliseconds before exiting VM
            (new KillVMThread(delay)).start();
            return (delay*1000); //just an estimate
        }
        private class KillVMThread extends Thread {
            long delay;
	    /** Thread that inherits its daemon status from main thread */
	    public KillVMThread(long delay) {
	        super("killVM");
                this.delay = delay;
            }
	    public void run() {
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                System.exit(0);
	    }
        }//end class KillVMThread
    }//end class KillVMObjectImpl

} //end class KillVMUtil


