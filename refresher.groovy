// Doc: https://jenkinsci.github.io/job-dsl-plugin
// Doc: https://jenkins.dev.aws.sinfo-one.it/plugin/job-dsl/api-viewer/index.html

folder('/jenkins') {
  description('Jenkins JCASC Refresher')
}

freeStyleJob('/jenkins/jcasc-refresher') {
  description('Jenkins JCASC Refresher')

  logRotator {
    numToKeep(10)
  }

  steps {
    shell('echo "Current directory: $(pwd)"') 
    
shell('''
            cat << 'EOF' > jcasc-refresher.groovy
            import com.cloudbees.plugins.credentials.CredentialsProvider
            import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials
            import hudson.model.User
            import jenkins.model.Jenkins
            def runCommand(command, workingDir) {
              def process = new ProcessBuilder(command.split(" "))
                    .directory(new File(workingDir))
                    .redirectErrorStream(true)
                    .start()
                  process.inputStream.eachLine { println it }
                  process.waitFor()
            }
            def credentialsId = "sinfo-one-jenkins-gitlab"
            def credentials = CredentialsProvider.lookupCredentials(
              UsernamePasswordCredentials.class,
              Jenkins.instance,
              null,
              null
            ).find { it.id == credentialsId }
            if(credentials){
              def repo_username_token = "${credentials.username}:${credentials.password.toString()}"
              def repoUrl = "https://"+repo_username_token+"@gitlab.sinfo-one.it/FDS/internship/devops/jenkins-pipelines.git"
              def branch = "master"
              def workspace = "/usr/share/jenkins/jenkins-pipelines"

              def repoDir = new File(workspace)

              if (!repoDir.exists() || repoDir.listFiles().length == 0) {
                  println "Repository Directory not found OR No Files exists in Repository Directory."
              } 
              else 
              {
                  println "Repository found. Pulling latest changes..."
                  runCommand("git pull ${repoUrl} ${branch}", workspace)
                  println "Pull completed successfully!"
              }
            } 
            else {println "Credentials not found!"}
EOF
        ''')
        
    shell('echo "Listing files after writing Groovy script:" && ls -l')

    shell('''if [ ! -f "jenkins-cli.jar" ]; then
        echo "Downloading Jenkins CLI..."
        curl -o jenkins-cli.jar $JENKINS_URL/jnlpJars/jenkins-cli.jar
      else
        echo "Jenkins CLI already exists."
      fi
    ''')

        shell('''#!/bin/bash
set +x

if [[ -z "$JENKINS_USER_ID" || -z "$JENKINS_API_TOKEN" ]]; then
  echo "JENKINS_USER_ID or JENKINS_API_TOKEN not set. Aborting."
  exit 1
fi

if java -jar jenkins-cli.jar -s "${JENKINS_URL}" -auth "${JENKINS_USER_ID}:${JENKINS_API_TOKEN}" groovy = < jcasc-refresher.groovy > /dev/null 2>&1; then
    echo "jcasc-refresher.groovy executed"
else
    echo "jcasc-refresher.groovy not executed"
fi
''')

    shell('''#!/bin/bash
set +x

if [[ -z "$JENKINS_USER_ID" || -z "$JENKINS_API_TOKEN" ]]; then
  echo "JENKINS_USER_ID or JENKINS_API_TOKEN not set. Aborting."
  exit 1
fi

if java -jar jenkins-cli.jar -s "${JENKINS_URL}" -auth "${JENKINS_USER_ID}:${JENKINS_API_TOKEN}" reload-jcasc-configuration > /dev/null 2>&1; then
    echo "JCasC reload successful"
else
    echo "JCasC reload failed"
fi
''')
  }
}
