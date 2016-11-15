#!/usr/bin/env groovy
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
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

def jarMap = [
        "../lib-ext/jsk-policy.jar"         : "apache-river/river-policy",
        "../lib/jsk-platform.jar"           : "apache-river/river-platform",
        "../lib/jsk-resources.jar"          : "apache-river/river-resources",
        "../lib-dl/jsk-dl.jar"              : "apache-river/river-dl",
        "../lib/serviceui.jar"              : "apache-river/river-dl",
        "../lib/jsk-lib.jar"                : "apache-river/river-lib",
        "../lib/start.jar"                  : "apache-river/river-start",
        "../lib/destroy.jar"                : "apache-river/river-destroy",
        "../lib/sharedvm.jar"               : "apache-river/river-sharedvm",
        "../lib-dl/group-dl.jar"            : "apache-river/river-services/group/group-dl",
        "../lib/group.jar"                  : "apache-river/river-services/group/group-service",
        "../lib-dl/mahalo-dl.jar"           : "apache-river/river-services/mahalo/mahalo-dl",
        "../lib/mahalo.jar"                 : "apache-river/river-services/mahalo/mahalo-service",
        "../lib-dl/mercury-dl.jar"          : "apache-river/river-services/mercury/mercury-dl",
        "../lib/mercury.jar"                : "apache-river/river-services/mercury/mercury-service",
        "../lib-dl/norm-dl.jar"             : "apache-river/river-services/norm/norm-dl",
        "../lib/norm.jar"                   : "apache-river/river-services/norm/norm-service",
        "../lib-dl/outrigger-dl.jar"        : "apache-river/river-services/outrigger/outrigger-dl",
        "../lib/outrigger.jar"              : "apache-river/river-services/outrigger/outrigger-service",
        "../lib/outrigger-snaplogstore.jar" : "apache-river/river-services/outrigger/outrigger-snaplogstore",
        "../lib-dl/reggie-dl.jar"           : "apache-river/river-services/reggie/reggie-dl",
        "../lib/reggie.jar"                 : "apache-river/river-services/reggie/reggie-service",
        "../lib-dl/phoenix-dl.jar"          : "apache-river/phoenix-activation/phoenix-dl",
        "../lib/phoenix.jar"                : "apache-river/phoenix-activation/phoenix",
        "../lib/phoenix-init.jar"           : "apache-river/phoenix-activation/phoenix-init",
        "../lib/phoenix-group.jar"          : "apache-river/phoenix-activation/phoenix-group",
        "../lib/checkconfigurationfile.jar" : "apache-river/tools/checkconfigurationfile",
        "../lib/checkser.jar"               : "apache-river/tools/checkser",
        "../lib/classdep.jar"               : "apache-river/tools/classdep",
        "../lib/classserver.jar"            : "apache-river/tools/classserver",
        "../lib/computedigest.jar"          : "apache-river/tools/computedigest",
        "../lib/computehttpmdcodebase.jar"  : "apache-river/tools/computehttpmdcodebase",
        "../lib/envcheck.jar"               : "apache-river/tools/envcheck",
        "../lib/jarwrapper.jar"             : "apache-river/tools/jarwrapper",
        "../lib/preferredlistgen.jar"       : "apache-river/tools/preferredlistgen"
]

def policy = []
def platform = []
def sharedvm = []
def start = []
def destroy = []
def lib = []
def lib_dl = []
def dlMap = [:]
File src = new File(System.getProperty("user.dir"), "../src")

for(Map.Entry<String, String> entry : jarMap.entrySet()) {
    jar = entry.key
    target = entry.value
    println jar
    ZipFile zipFile = new ZipFile(jar)
    Enumeration zipEntries = zipFile.entries()
    while(zipEntries.hasMoreElements()) {
        ZipEntry zipEntry = (ZipEntry)zipEntries.nextElement()
        if(zipEntry.getName().endsWith("MANIFEST.MF") ||
           zipEntry.getName().endsWith("META-INF/")   ||
           zipEntry.getName().endsWith("META-INF/services/"))
            continue
        if(jar.contains("jsk-resources")) {
            prepAndCopy(zipFile, zipEntry, src, target)
        } else {
            if(!zipEntry.getName().contains("\$") && !zipEntry.isDirectory()) {
                if (jar.contains("jsk-policy")) {
                    policy << zipEntry.getName()
                    prepAndCopy(zipFile, zipEntry, src, target)
                } else if(jar.contains("jsk-platform")) {
                    platform << zipEntry.getName()
                    if (skip(zipEntry, policy)){
                        println "\t- ${zipEntry.getName()}"
                    } else {
                        prepAndCopy(zipFile, zipEntry, src, target)
                    }
                } else if(jar.contains("jsk-dl") || jar.contains("serviceui")) {
                    lib_dl << zipEntry.getName()
                    if(skip(zipEntry, policy, platform)) {
                        println "\t- ${zipEntry.getName()}"
                    } else {
                        prepAndCopy(zipFile, zipEntry, src, target)
                    }
                } else if(jar.contains("jsk-lib")) {
                    lib << zipEntry.getName()
                    if(skip(zipEntry, policy, platform, lib_dl)) {
                        println "\t- ${zipEntry.getName()}"
                    } else {
                        prepAndCopy(zipFile, zipEntry, src, target)
                    }
                } else if(jar.contains("start")) {
                    start << zipEntry.getName()
                    if (skip(zipEntry, policy, platform, lib_dl, lib )){
                        println "\t- ${zipEntry.getName()}"
                    } else {
                        prepAndCopy(zipFile, zipEntry, src, target)
                    }
                } else if(jar.contains("destroy")) {
                    destroy << zipEntry.getName()
                    if (skip(zipEntry, policy, platform, lib_dl, start, lib)){
                        println "\t- ${zipEntry.getName()}"
                    } else {
                        prepAndCopy(zipFile, zipEntry, src, target)
                    }
                } else if(jar.contains("sharedvm")) {
                    sharedvm << zipEntry.getName()
                    if (skip(zipEntry, policy, platform, lib_dl, start, lib, destroy)){
                        println "\t- ${zipEntry.getName()}"
                    } else {
                        prepAndCopy(zipFile, zipEntry, src, target)
                    }
                } else {
                    if(jar.contains("-dl")) {
                        String key = getKeyName(jar)
                        dlJarClassList = dlMap.get(key)
                        if(dlJarClassList==null)
                            dlJarClassList = []
                        dlJarClassList << zipEntry.name
                        dlMap.put key, dlJarClassList

                        if(skip(zipEntry, policy, platform, lib_dl, start, lib, destroy, sharedvm)) {
                            println "\tSkip ${zipEntry.getName()}"
                        } else {
                            prepAndCopy(zipFile, zipEntry, src, target)
                        }
                    } else {
                        String key = getKeyName(jar)
                        dlJarClassList = dlMap.get(key)
                        if(skip(zipEntry, policy, platform, lib_dl, start, lib, destroy, sharedvm, dlJarClassList as List)) {
                            println "\tSkip ${zipEntry.getName()}"
                        } else {
                            prepAndCopy(zipFile, zipEntry, src, target)
                        }
                    }
                }
            }
        }
    }
}

void prepAndCopy(zipFile, zipEntry, src, target) {
    String source
    if(zipEntry.getName().endsWith("class")) {
        source =
            String.format("%sjava",
                          zipEntry.getName().substring(0, zipEntry.getName().length()-"class".length()))
    } else if(zipEntry.getName().endsWith("PREFERRED.LIST")) {
        iStream = zipFile.getInputStream(zipEntry)
        File preferredList = new File(String.format("%s/src/main/resources/PREFERRED.LIST", target))
        File parent = preferredList.getParentFile()
        parent.mkdirs()
        def writer = preferredList.newWriter()
        writer << iStream.text
        writer.flush()
        writer.close()
        println("\tGenerated "+preferredList.path)
        iStream.close()
        return
    } else if(zipEntry.getName().startsWith("META-INF/services/")) {
        String service = zipEntry.getName().substring("META-INF/services/".length())
        File serviceResource = new File(String.format("%s/src/main/resources/services/%s", target, service))
        File parent = serviceResource.getParentFile()
        parent.mkdirs()
        iStream = zipFile.getInputStream(zipEntry)
        def writer = serviceResource.newWriter()
        writer << iStream.text
        writer.flush()
        writer.close()
        println("\tGenerated "+serviceResource.path)
        return
    } else {
        source = zipEntry.getName()
    }
    copy(src, source, String.format("%s/src/main/java/%s", target, source))
}

void copy(src, source, target) {
    File file = new File(src, source)
    if(file.exists()) {
        File targetFile = new File(target)
        File parent = targetFile.getParentFile()
        parent.mkdirs()
        String moveFile = file.toString()
        String dir = parent.toString()
//        def writer = targetFile.newWriter()
//        writer << file.text
//        writer.flush()
//        writer.close()
        println String.format("\tgit mv %-100s to %s", file.path, target)
        String command = "git mv " + moveFile + " " + dir
        def p = command.execute()
        p.waitFor()
    } else {
        println "\tNOT FOUND: ${file.path}"
    }
}

String getKeyName(name) {
    int ndx = name.startsWith("../lib-dl")?10:7
    String s = name.substring(ndx)
    ndx = s.endsWith("-dl.jar")?7:4
    return s.substring(0, s.length()-ndx)
}

boolean skip(ZipEntry entry, List... lists) {
    boolean skip = false
    for(List list : lists) {
        if(list==null)
            continue
        skip = !entry.name.endsWith("PREFERRED.LIST") && list.contains(entry.name)
        if(skip)
            break;
    }
    skip
}
