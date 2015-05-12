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
package org.apache.river.test.impl.end2end.e2etest;
import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * The interface which defines remote methods to be executed
 * as part of the test.
 *
 * WARNING: THIS INTERFACE IS GENERATED AUTOMATICALLY AND 
 *          SHOULD NOT BE EDITED.
 */

public interface ConstraintsInterface extends Remote {

    public PlainObject oAuth() throws RemoteException;

    public PlainObject ooAuth(PlainObject obj1) throws RemoteException;

    public PlainObject orAuth(ReaderObject obj1) throws RemoteException;

    public PlainObject oooAuth(PlainObject obj1,PlainObject obj2) throws RemoteException;

    public PlainObject oorAuth(PlainObject obj1,ReaderObject obj2) throws RemoteException;

    public PlainObject orrAuth(ReaderObject obj1,ReaderObject obj2) throws RemoteException;

    public WriterObject wAuth() throws RemoteException;

    public WriterObject woAuth(PlainObject obj1) throws RemoteException;

    public WriterObject wrAuth(ReaderObject obj1) throws RemoteException;

    public WriterObject wooAuth(PlainObject obj1,PlainObject obj2) throws RemoteException;

    public WriterObject worAuth(PlainObject obj1,ReaderObject obj2) throws RemoteException;

    public WriterObject wrrAuth(ReaderObject obj1,ReaderObject obj2) throws RemoteException;

    public void vAuth() throws RemoteException;

    public void voAuth(PlainObject obj1) throws RemoteException;

    public void vrAuth(ReaderObject obj1) throws RemoteException;

    public void vooAuth(PlainObject obj1,PlainObject obj2) throws RemoteException;

    public void vorAuth(PlainObject obj1,ReaderObject obj2) throws RemoteException;

    public void vrrAuth(ReaderObject obj1,ReaderObject obj2) throws RemoteException;

    public PlainObject oNoauth() throws RemoteException;

    public PlainObject ooNoauth(PlainObject obj1) throws RemoteException;

    public PlainObject orNoauth(ReaderObject obj1) throws RemoteException;

    public PlainObject oooNoauth(PlainObject obj1,PlainObject obj2) throws RemoteException;

    public PlainObject oorNoauth(PlainObject obj1,ReaderObject obj2) throws RemoteException;

    public PlainObject orrNoauth(ReaderObject obj1,ReaderObject obj2) throws RemoteException;

    public WriterObject wNoauth() throws RemoteException;

    public WriterObject woNoauth(PlainObject obj1) throws RemoteException;

    public WriterObject wrNoauth(ReaderObject obj1) throws RemoteException;

    public WriterObject wooNoauth(PlainObject obj1,PlainObject obj2) throws RemoteException;

    public WriterObject worNoauth(PlainObject obj1,ReaderObject obj2) throws RemoteException;

    public WriterObject wrrNoauth(ReaderObject obj1,ReaderObject obj2) throws RemoteException;

    public void vNoauth() throws RemoteException;

    public void voNoauth(PlainObject obj1) throws RemoteException;

    public void vrNoauth(ReaderObject obj1) throws RemoteException;

    public void vooNoauth(PlainObject obj1,PlainObject obj2) throws RemoteException;

    public void vorNoauth(PlainObject obj1,ReaderObject obj2) throws RemoteException;

    public void vrrNoauth(ReaderObject obj1,ReaderObject obj2) throws RemoteException;

    public void v() throws RemoteException;

    public void vConf() throws RemoteException;

    public void vAuthConf() throws RemoteException;

    public void vNoauthConf() throws RemoteException;

    public void vNoconf() throws RemoteException;

    public void vAuthNoconf() throws RemoteException;

    public void vInteg() throws RemoteException;

    public void vAuthInteg() throws RemoteException;

    public void vNoauthInteg() throws RemoteException;

    public void vConfInteg() throws RemoteException;

    public void vAuthConfInteg() throws RemoteException;

    public void vNoauthConfInteg() throws RemoteException;

    public void vNoconfInteg() throws RemoteException;

    public void vAuthNoconfInteg() throws RemoteException;

    public void vCMinP() throws RemoteException;

    public void vAuthCMinP() throws RemoteException;

    public void vNoauthCMinP() throws RemoteException;

    public void vConfCMinP() throws RemoteException;

    public void vAuthConfCMinP() throws RemoteException;

    public void vNoauthConfCMinP() throws RemoteException;

    public void vNoconfCMinP() throws RemoteException;

    public void vAuthNoconfCMinP() throws RemoteException;

    public void vIntegCMinP() throws RemoteException;

    public void vAuthIntegCMinP() throws RemoteException;

    public void vNoauthIntegCMinP() throws RemoteException;

    public void vConfIntegCMinP() throws RemoteException;

    public void vAuthConfIntegCMinP() throws RemoteException;

    public void vNoauthConfIntegCMinP() throws RemoteException;

    public void vNoconfIntegCMinP() throws RemoteException;

    public void vAuthNoconfIntegCMinP() throws RemoteException;

    public void vCMinTp() throws RemoteException;

    public void vAuthCMinTp() throws RemoteException;

    public void vNoauthCMinTp() throws RemoteException;

    public void vConfCMinTp() throws RemoteException;

    public void vAuthConfCMinTp() throws RemoteException;

    public void vNoauthConfCMinTp() throws RemoteException;

    public void vNoconfCMinTp() throws RemoteException;

    public void vAuthNoconfCMinTp() throws RemoteException;

    public void vIntegCMinTp() throws RemoteException;

    public void vAuthIntegCMinTp() throws RemoteException;

    public void vNoauthIntegCMinTp() throws RemoteException;

    public void vConfIntegCMinTp() throws RemoteException;

    public void vAuthConfIntegCMinTp() throws RemoteException;

    public void vNoauthConfIntegCMinTp() throws RemoteException;

    public void vNoconfIntegCMinTp() throws RemoteException;

    public void vAuthNoconfIntegCMinTp() throws RemoteException;

    public void vCMinPCMinTp() throws RemoteException;

    public void vAuthCMinPCMinTp() throws RemoteException;

    public void vNoauthCMinPCMinTp() throws RemoteException;

    public void vConfCMinPCMinTp() throws RemoteException;

    public void vAuthConfCMinPCMinTp() throws RemoteException;

    public void vNoauthConfCMinPCMinTp() throws RemoteException;

    public void vNoconfCMinPCMinTp() throws RemoteException;

    public void vAuthNoconfCMinPCMinTp() throws RemoteException;

    public void vIntegCMinPCMinTp() throws RemoteException;

    public void vAuthIntegCMinPCMinTp() throws RemoteException;

    public void vNoauthIntegCMinPCMinTp() throws RemoteException;

    public void vConfIntegCMinPCMinTp() throws RemoteException;

    public void vAuthConfIntegCMinPCMinTp() throws RemoteException;

    public void vNoauthConfIntegCMinPCMinTp() throws RemoteException;

    public void vNoconfIntegCMinPCMinTp() throws RemoteException;

    public void vAuthNoconfIntegCMinPCMinTp() throws RemoteException;

    public void vAlt1() throws RemoteException;

    public void vAuthAlt1() throws RemoteException;

    public void vNoauthAlt1() throws RemoteException;

    public void vConfAlt1() throws RemoteException;

    public void vAuthConfAlt1() throws RemoteException;

    public void vNoauthConfAlt1() throws RemoteException;

    public void vNoconfAlt1() throws RemoteException;

    public void vAuthNoconfAlt1() throws RemoteException;

    public void vIntegAlt1() throws RemoteException;

    public void vAuthIntegAlt1() throws RemoteException;

    public void vNoauthIntegAlt1() throws RemoteException;

    public void vConfIntegAlt1() throws RemoteException;

    public void vAuthConfIntegAlt1() throws RemoteException;

    public void vNoauthConfIntegAlt1() throws RemoteException;

    public void vNoconfIntegAlt1() throws RemoteException;

    public void vAuthNoconfIntegAlt1() throws RemoteException;

    public void vCMinTpAlt1() throws RemoteException;

    public void vAuthCMinTpAlt1() throws RemoteException;

    public void vNoauthCMinTpAlt1() throws RemoteException;

    public void vConfCMinTpAlt1() throws RemoteException;

    public void vAuthConfCMinTpAlt1() throws RemoteException;

    public void vNoauthConfCMinTpAlt1() throws RemoteException;

    public void vNoconfCMinTpAlt1() throws RemoteException;

    public void vAuthNoconfCMinTpAlt1() throws RemoteException;

    public void vIntegCMinTpAlt1() throws RemoteException;

    public void vAuthIntegCMinTpAlt1() throws RemoteException;

    public void vNoauthIntegCMinTpAlt1() throws RemoteException;

    public void vConfIntegCMinTpAlt1() throws RemoteException;

    public void vAuthConfIntegCMinTpAlt1() throws RemoteException;

    public void vNoauthConfIntegCMinTpAlt1() throws RemoteException;

    public void vNoconfIntegCMinTpAlt1() throws RemoteException;

    public void vAuthNoconfIntegCMinTpAlt1() throws RemoteException;

    public void vAlt2() throws RemoteException;

    public void vAuthAlt2() throws RemoteException;

    public void vNoauthAlt2() throws RemoteException;

    public void vConfAlt2() throws RemoteException;

    public void vAuthConfAlt2() throws RemoteException;

    public void vNoauthConfAlt2() throws RemoteException;

    public void vNoconfAlt2() throws RemoteException;

    public void vAuthNoconfAlt2() throws RemoteException;

    public void vIntegAlt2() throws RemoteException;

    public void vAuthIntegAlt2() throws RemoteException;

    public void vNoauthIntegAlt2() throws RemoteException;

    public void vConfIntegAlt2() throws RemoteException;

    public void vAuthConfIntegAlt2() throws RemoteException;

    public void vNoauthConfIntegAlt2() throws RemoteException;

    public void vNoconfIntegAlt2() throws RemoteException;

    public void vAuthNoconfIntegAlt2() throws RemoteException;

    public void vCMinTpAlt2() throws RemoteException;

    public void vAuthCMinTpAlt2() throws RemoteException;

    public void vNoauthCMinTpAlt2() throws RemoteException;

    public void vConfCMinTpAlt2() throws RemoteException;

    public void vAuthConfCMinTpAlt2() throws RemoteException;

    public void vNoauthConfCMinTpAlt2() throws RemoteException;

    public void vNoconfCMinTpAlt2() throws RemoteException;

    public void vAuthNoconfCMinTpAlt2() throws RemoteException;

    public void vIntegCMinTpAlt2() throws RemoteException;

    public void vAuthIntegCMinTpAlt2() throws RemoteException;

    public void vNoauthIntegCMinTpAlt2() throws RemoteException;

    public void vConfIntegCMinTpAlt2() throws RemoteException;

    public void vAuthConfIntegCMinTpAlt2() throws RemoteException;

    public void vNoauthConfIntegCMinTpAlt2() throws RemoteException;

    public void vNoconfIntegCMinTpAlt2() throws RemoteException;

    public void vAuthNoconfIntegCMinTpAlt2() throws RemoteException;

    public void vBogus() throws RemoteException;

    public void multi() throws RemoteException;

    public void multi(int i) throws RemoteException;

    public void multi(int i, ReaderObject obj1) throws RemoteException;

    public void multi(PlainObject obj1) throws RemoteException;

    public void multi(PlainObject obj1, ReaderObject obj2) throws RemoteException;
}
