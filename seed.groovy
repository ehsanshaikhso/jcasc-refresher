// Doc: https://jenkinsci.github.io/job-dsl-plugin
// Doc: https://jenkins.dev.aws.sinfo-one.it/plugin/job-dsl/api-viewer/index.html
// Doc: http://docs.groovy-lang.org/docs/latest/html/documentation/core-domain-specific-languages.html

/////////////////////////////////////////////////////////////////////////////
// Constants

def gitlabDomain = binding.variables.gitlabDomain
def gitlabJenkinsPipelinesPath = binding.variables.gitlabJenkinsPipelinesPath
def gitlabDockerImagesPath = binding.variables.gitlabDockerImagesPath

class K {
  static final DEFAULT_GIT_BRANCH = 'master'
  static final JENKINS_GITLAB_CREDENTIALS = 'sinfo-one-jenkins-gitlab'
}

/////////////////////////////////////////////////////////////////////////////
// Utility Functions

def sinfoOneRateLimitBuilds(Map config) {
  if (config.rateLimitBuildsCount) {
    return { delegate ->
      delegate.rateLimitBuilds {
        throttle {
          count(config.rateLimitBuildsCount)
          durationName(config.rateLimitBuildsDurationName ?: 'day')
          userBoost(config.rateLimitBuildsUserBoost ?: false)
        }
      }
    }
  }
  return null
}

def sinfoOneGit(Map config) {
  final configLibrary = config?.library ?: false
  final configCps = config?.cps ?: false
  final configBranch = config?.branch ?: K.DEFAULT_GIT_BRANCH
  def configUrl = '${GITLAB_DOMAIN}${GITLAB_JENKINS_PIPELINES_PATH}'+'.git'
  if (configLibrary) {
    return {
      remote(configUrl)
      credentialsId(K.JENKINS_GITLAB_CREDENTIALS)
    }
  }
  if (configCps) {
    return {
      remote {
        url(configUrl)
        credentials(K.JENKINS_GITLAB_CREDENTIALS)
      }
      branch(configBranch)
    }
  }
  return {}
}

def sinfoOnePipelineLib(String gjpp, String gd) {
  return {
    name('sinfo-one-lib')
    defaultVersion('master')
    allowVersionOverride(true)
    retriever {
      modernSCM {
        scm {
          git sinfoOneGit([library: true, path: gjpp, gd: gd])
        }
        libraryPath('pipeline-lib')
      }
    }
  }
}

/////////////////////////////////////////////////////////////////////////////
// Job Builders

def sinfoOneJenkinsPrune(Map config) {
  final configName = "/jenkins/${config.name}"
  final configDescription = "Jenkins ${config.name}"
  return freeStyleJob(configName) {
    description(configDescription)
    logRotator{
      numToKeep(3)
    }
    triggers {
      if (config.triggerCron != null) {
        cron {
          spec(config.triggerCron)
        }
      }
    }
    steps {
      shell('docker system prune -f -a')
    }
  }
}

def sinfoOnePipelineDockerImage(Map config) 
{
  final configName = config.gdip+config.name
  final configDescription = "SiFides Docker image ${config.name}"
  final rateLimitBuildsBody = sinfoOneRateLimitBuilds(config)
  return pipelineJob(configName) {
    description(configDescription)
    logRotator{
      numToKeep(10)
    }
    properties {
      pipelineTriggers {
        triggers {
          if (config.triggerCron != null) {
            cron {
              spec(config.triggerCron)
            }
          }
          if (config.triggerUpstream != null) {
            upstream {
              upstreamProjects(config.triggerUpstream)
              threshold('SUCCESS')
            }
          }
        }
      }
      if (rateLimitBuildsBody) {
        rateLimitBuildsBody(delegate)
      }
    }
    parameters {
        string {
            name('gitBranch')
            description('Git branch')
            defaultValue('master')
        }
    }
    definition {
      cpsScm {
        scm {
          git sinfoOneGit([cps: true, , path: config.gjpp, gd: config.gd])
        }
        scriptPath('docker-images/Jenkinsfile')
      }
    }
  }
}

/////////////////////////////////////////////////////////////////////////////
// Folders

folder('/FDS') {
  description('Service Line Fides')
  properties {
    folderLibraries {
      libraries {
        libraryConfiguration sinfoOnePipelineLib(gitlabJenkinsPipelinesPath, gitlabDomain)
      }
    }
  }
}

folder('/FDS/DevOps') {
  description('SiFides DevOps')
}

folder('/FDS/DevOps/docker-images') {
  description('SiFides Docker Images')
}

folder('/FDS/DevOps/refresher-JCasC') {
  description('Jenkins JCASC Refresher')
}

/////////////////////////////////////////////////////////////////////////////
// Jobs

sinfoOneJenkinsPrune name: 'prune', triggerCron: 'H 0 * * *'

sinfoOnePipelineDockerImage([name: 'debian', triggerCron: 'H 0 1 * *', gdip: gitlabDockerImagesPath, gjpp: gitlabJenkinsPipelinesPath, gd: gitlabDomain])
sinfoOnePipelineDockerImage([name: 'debian-builder', triggerUpstream: 'debian', gdip: gitlabDockerImagesPath, gjpp: gitlabJenkinsPipelinesPath, gd: gitlabDomain])
sinfoOnePipelineDockerImage([name: 'debian-dev', triggerUpstream: 'debian-builder', gdip: gitlabDockerImagesPath, gjpp: gitlabJenkinsPipelinesPath, gd: gitlabDomain])
sinfoOnePipelineDockerImage([name: 'fides-builder', triggerUpstream: 'debian-dev', gdip: gitlabDockerImagesPath, gjpp: gitlabJenkinsPipelinesPath, gd: gitlabDomain])
sinfoOnePipelineDockerImage([name: 'iscobol-builder', triggerUpstream: 'debian-dev', gdip: gitlabDockerImagesPath, gjpp: gitlabJenkinsPipelinesPath, gd: gitlabDomain])
sinfoOnePipelineDockerImage([name: 'spring-boot-builder', triggerUpstream: 'debian-dev', gdip: gitlabDockerImagesPath, gjpp: gitlabJenkinsPipelinesPath, gd: gitlabDomain])
sinfoOnePipelineDockerImage([name: 'fides-web-server', gdip: gitlabDockerImagesPath, gjpp: gitlabJenkinsPipelinesPath, gd: gitlabDomain])
sinfoOnePipelineDockerImage([name: 'fides-license-server', gdip: gitlabDockerImagesPath, gjpp: gitlabJenkinsPipelinesPath, gd: gitlabDomain])
sinfoOnePipelineDockerImage([name: 'fides-legacy-server', gdip: gitlabDockerImagesPath, gjpp: gitlabJenkinsPipelinesPath, gd: gitlabDomain])
sinfoOnePipelineDockerImage([name: 'fides-legacy-webclient', gdip: gitlabDockerImagesPath, gjpp: gitlabJenkinsPipelinesPath, gd: gitlabDomain])
sinfoOnePipelineDockerImage([name: 'fides-legacy-rest-server', gdip: gitlabDockerImagesPath, gjpp: gitlabJenkinsPipelinesPath, gd: gitlabDomain])
sinfoOnePipelineDockerImage([name: 'fides-legacy-crpp-gimm', gdip: gitlabDockerImagesPath, gjpp: gitlabJenkinsPipelinesPath, gd: gitlabDomain])
sinfoOnePipelineDockerImage([name: 'httpd', triggerUpstream: 'debian-dev', gdip: gitlabDockerImagesPath, gjpp: gitlabJenkinsPipelinesPath, gd: gitlabDomain])
sinfoOnePipelineDockerImage([
  name: 'httpd-acmesh',
  triggerUpstream: 'httpd',
  rateLimitBuildsCount: 1,
  rateLimitBuildsDurationName: 'week', gdip: gitlabDockerImagesPath, gjpp: gitlabJenkinsPipelinesPath, gd: gitlabDomain])
sinfoOnePipelineDockerImage([name: 'svnedge', gdip: gitlabDockerImagesPath, gjpp: gitlabJenkinsPipelinesPath, gd: gitlabDomain])


// Job for Jenkins Configuration as Code (JCasC) refresher
freeStyleJob('/FDS/DevOps/refresher-JCasC/jcasc-refresher') {
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
    exit 1
fi
''')
  }
}
