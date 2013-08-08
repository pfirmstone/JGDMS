#!/usr/bin/env groovy
/*
 * Copyright to the original author or authors.
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
String version = "2.2.1"
String rootDir = ".."
String repositoryId="apache.releases.https"
String repositoryURL="https://repository.apache.org/service/local/staging/deploy/maven2"
//String repositoryURL="file:///Users/trasukg/mvn-repo"
String passphrase="Insert passphrase here..."
        
["net.jini:jsk-platform":"lib",
 "net.jini:jsk-lib":"lib",
 "net.jini:jsk-dl":"lib-dl",
 "net.jini:jsk-resources":"lib",
 "net.jini:jsk-policy":"lib",
 "net.jini.lookup:serviceui":"lib",
 "org.apache.river:fiddler":"lib",
 "org.apache.river:fiddler-dl":"lib-dl",
 "org.apache.river:mahalo":"lib",
 "org.apache.river:mahalo-dl":"lib-dl",
 "org.apache.river:mercury":"lib",
 "org.apache.river:mercury-dl":"lib-dl",
 "org.apache.river:norm":"lib",
 "org.apache.river:norm-dl":"lib-dl",
 "org.apache.river:outrigger":"lib",
 "org.apache.river:outrigger-dl":"lib-dl",
 "org.apache.river:reggie":"lib",
 "org.apache.river:reggie-dl":"lib-dl",
 "org.apache.river:start":"lib"
].each {artifact, subDir ->
    
    String[] parts = artifact.split(":")
    String gId = parts[0]
    String aId = parts[1]
    String dir = rootDir+"/"+subDir
    /*String deployCommand = "mvn deploy:deploy-file "+
                           "-DrepositoryId=apache.releases.https "+
                           "-Dversion=${version} "+
                           "-DgeneratePom=false -Dpackaging=jar "+
                           "-DgroupId=${gId} "+
                           "-DartifactId=${aId} "+
                           "-Dfile=${dir}/${aId}.jar "+
                           "-DpomFile=./${aId}.pom "+
                           "-Durl=https://repository.apache.org/service/local/staging/deploy/maven2"
    */
    def deployCommand = [\
        "mvn",
        "gpg:sign-and-deploy-file",
        "-DpomFile=${aId}.pom",
        "-Dfile=${dir}/${aId}.jar",
        "-DrepositoryId=${repositoryId}",
	"-Durl=${repositoryURL}",
        "-Dgpg.useAgent=false",
	"-Dgpg.keyname=gtrasuk@apache.org",
        "-Dgpg.passphrase=${passphrase}"
    ]
    /*
	
        /*"-Drepository=${repository} " +
	
	
    */
/*
"mvn gpg:sign-and-deploy-file " +
	"-Dpassphrase='situation business offensive persion want comprehension product features' " +
	"-DpomFile=./${aId}.pom " +
	"-Dfile=${dir}/${aId}.jar " + 
	"-DrepositoryId=${repository} " +
	"-Durl=${repositoryURL}"
*/
    /* First make sure the jar file contains the LICENSE file */
    def p="jar uvf ${dir}/${aId}.jar -C .. LICENSE".execute()
    p.consumeProcessOutputStream(System.out)
    p.consumeProcessErrorStream(System.err)
    p.waitFor()
    
    println deployCommand
    Process process = deployCommand.execute()
    process.consumeProcessOutputStream(System.out)
    process.consumeProcessErrorStream(System.err)
    process.waitFor()
}
