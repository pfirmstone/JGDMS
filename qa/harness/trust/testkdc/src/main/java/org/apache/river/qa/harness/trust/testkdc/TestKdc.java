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
package org.apache.river.qa.harness.trust.testkdc;

import org.apache.kerby.kerberos.kerb.server.SimpleKdcServer;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Stands up a real, wire-protocol-compliant test Kerberos KDC (Apache Kerby,
 * pure Java) for validating net.jini.jeri.kerberos end-to-end against the
 * qa/harness/configs/kerberos/reggie/reggie.config JERI transport
 * configuration, without requiring root privileges or a container runtime
 * (neither is available in every JGDMS dev/CI sandbox; Apache Kerby
 * implements the real KRB5 ASN.1 wire protocol, unlike a mock).
 *
 * Usage:
 *   java -cp <kerby classpath>:testkdc.jar \
 *     org.apache.river.qa.harness.trust.testkdc.TestKdc \
 *     <realm> <host> <tcpPort> <workDir> <keytabFile> <principal1> [<principal2> ...]
 *
 * Writes <workDir>/krb5.conf (Kerby generates this) and <keytabFile>
 * (aggregate keytab for ALL listed principals, matching the QA harness's
 * "aggregatePasswordFile" convention -- see qaHarness.prop /
 * configSet.properties: -Dkeytab=${...aggregatePasswordFile}).
 *
 * Prints "TESTKDC-READY" to stdout once the KDC is listening and the keytab
 * has been written, then blocks until stdin is closed or the process is
 * killed (see ../run-test-kdc.sh).
 */
public final class TestKdc {

    public static void main(String[] args) throws Exception {
        if (args.length < 6) {
            System.err.println(
                "usage: TestKdc <realm> <host> <tcpPort> <workDir> <keytabFile> <principal1> [<principal2> ...]");
            System.exit(2);
        }
        String realm = args[0];
        String host = args[1];
        int tcpPort = Integer.parseInt(args[2]);
        File workDir = new File(args[3]);
        File keytabFile = new File(args[4]);
        List<String> principals = new ArrayList<>();
        for (int i = 5; i < args.length; i++) {
            principals.add(args[i]);
        }

        if (!workDir.exists() && !workDir.mkdirs()) {
            throw new IllegalStateException("could not create workDir " + workDir);
        }
        if (keytabFile.exists() && !keytabFile.delete()) {
            throw new IllegalStateException("could not remove stale keytab " + keytabFile);
        }

        SimpleKdcServer kdc = new SimpleKdcServer();
        kdc.setKdcRealm(realm);
        kdc.setKdcHost(host);
        kdc.setAllowUdp(false);
        kdc.setAllowTcp(true);
        kdc.setKdcTcpPort(tcpPort);
        kdc.setWorkDir(workDir);

        kdc.init();
        kdc.start();

        String[] principalArray = principals.toArray(new String[0]);
        kdc.createAndExportPrincipals(keytabFile, principalArray);

        System.out.println("TestKdc: realm=" + realm + " host=" + host + " tcpPort=" + tcpPort);
        System.out.println("TestKdc: workDir=" + workDir.getAbsolutePath());
        System.out.println("TestKdc: krb5.conf=" + new File(workDir, "krb5.conf").getAbsolutePath());
        System.out.println("TestKdc: keytab=" + keytabFile.getAbsolutePath());
        for (String p : principalArray) {
            System.out.println("TestKdc: principal created: " + p);
        }
        System.out.flush();

        // Marker file: lets a launcher script poll for readiness without
        // scraping stdout.
        Path readyMarker = workDir.toPath().resolve("READY");
        Files.write(readyMarker, ("ready\n").getBytes(),
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        System.out.println("TESTKDC-READY");
        System.out.flush();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                kdc.stop();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }));

        // Block until killed (SIGTERM from run-test-kdc.sh stop / Ctrl-C).
        Object lock = new Object();
        synchronized (lock) {
            lock.wait();
        }
    }
}
