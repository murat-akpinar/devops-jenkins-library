# Jenkins Shared Library - Usage Guide

> Turkce dokumantasyon icin: **[KULLANIM_KILAVUZU.md](./KULLANIM_KILAVUZU.md)**

This guide explains **step by step** how to use the Jenkins Shared Library in a new project.

## Table of Contents

- [Prerequisites](#prerequisites)
- [Step 1: Create services.yml](#step-1-create-servicesyml)
- [Step 2: Create the Jenkinsfile](#step-2-create-the-jenkinsfile)
- [Step 3: Create sonar-project.properties](#step-3-create-sonar-projectproperties)
- [Step 4: Docker Compose File](#step-4-docker-compose-file)
- [Step 5: Create a Pipeline Job in Jenkins](#step-5-create-a-pipeline-job-in-jenkins)
- [Step 6: First Build](#step-6-first-build)
- [Example Scenarios](#example-scenarios)
- [Project-Specific Settings](#project-specific-settings)

---

## Prerequisites

Before starting the CI/CD pipeline setup, make sure the following are ready:

1. **CI/CD Pre-Setup Completed** — All steps in `01-cicd-preparation.md` must be done
2. **Global Library Defined in Jenkins** — `devops-jenkins-library` must be registered
3. **Pipeline Utility Steps Plugin Installed** — Required for YAML reading
4. **Project Has a Git Repository** — Code must be in Git

**[CI/CD Pre-Setup Guide →](./01-cicd-preparation.md)**

---

## Step 1: Create services.yml

Create a `services.yml` file in your project's **root directory**. This file defines the application name, environment configuration, and service list.

### Basic services.yml Structure

```yaml
app_name: my-project                   # Application name — deploy path: /app/my-project/
recipients: 'Team_1, Team_2'           # Email recipients (emailTeams key or direct address)

environments:                          # Deploy targets per environment
  test:
    deploy_targets:
      - 192.168.1.10
  preprod:
    deploy_targets:
      - 192.168.2.10
  prod:
    deploy_targets:
      - 192.168.3.10

services:
  - name: backend                      # Service name (required)
    path: backend/                     # Service directory — for git diff (required)
    dockerfile: Dockerfile             # Dockerfile path (required)
    image_name: myapp_backend          # Docker image name (required)
    env_file: backend/.env             # .env file path (optional)
    docker_compose_file: docker-compose.yml
    extra_files:
      - { src: "default.conf", dest: "/app/my-project/default.conf" }
    test_command: "npm test"           # Test command (optional)

  - name: frontend
    path: frontend/
    dockerfile: Dockerfile
    image_name: myapp_frontend
    env_file: frontend/.env
```

> **Real example:** See `Example/services_example.yml`.

### Field Reference

| Field | Required | Description |
|-------|----------|-------------|
| `app_name` | ✅ | Application name; deploy path becomes `/app/<app_name>/` |
| `recipients` | ❌ | Notification emails (`emailTeams` key or direct address) |
| `environments` | ✅ | `deploy_targets` IP list per environment |
| `services[].name` | ✅ | Service name |
| `services[].path` | ✅ | Service directory (for git diff) |
| `services[].dockerfile` | ✅ | Dockerfile path |
| `services[].image_name` | ✅ | Docker image name |
| `services[].env_file` | ❌ | .env file path (copied from Git for the first service) |
| `services[].docker_compose_file` | ❌ | Compose file path |
| `services[].docker_compose_remote_file` | ❌ | Name to use on the remote server |
| `services[].extra_files` | ❌ | Additional files/directories list |
| `services[].ssh_user` | ❌ | SSH username |
| `services[].test_command` | ❌ | Test command |

### extra_files Detailed Explanation

```yaml
extra_files:
  - default.conf              # String → /app/myapp/default.conf
  - nginx/nginx.conf          # String → /app/myapp/nginx.conf (using basename)
  - backend                   # String → /app/myapp/backend/ (directory)
  - src: webrtc/config.env    # Map format — for custom destination path
    dest: /app/myapp/config.env
```

- Automatically detects whether source is a file or directory
- Automatically cleans up conflicting path types on the server (prevents Docker mount errors)
- Copied **before** the `.env` file

---

## Step 2: Create the Jenkinsfile

Create a Jenkinsfile in your project's **root directory**. The full example is in `Example/Jenkins_example.groovy`.

### Basic Jenkinsfile Structure

```groovy
@Library('devops-jenkins-library') _

pipeline {
    agent { label "linux" }

    parameters {
        choice(name: 'ENVIRONMENT', choices: ['test', 'preprod', 'prod'], description: 'Select deploy environment')
        booleanParam(name: 'FORCE_REBUILD', defaultValue: false, description: 'Force rebuild all images')
        booleanParam(name: 'USE_EXISTING_IMAGE', defaultValue: false, description: 'Skip build/push if tag exists, use existing image')
        string(name: 'VERSION_TAG', defaultValue: 'test-v1.0', description: 'Image tag to deploy')
    }

    environment {
        VERSION = "${params.VERSION_TAG ?: 'test-v1.0'}"
        // Credentials (automatically written to .env file)
        // PSQL_HOST = credentials('psql_myproject_host')
        // PSQL_PASS = credentials('psql_myproject_pass')
    }

    options { timeout(time: 19999, unit: 'SECONDS') }

    stages {
        stage('Initialize') {
            steps {
                script {
                    def CFG = globalConfig()
                    env.NEXUS_URL            = CFG.NEXUS_URL
                    env.NEXUS_REGISTRY_URL   = CFG.NEXUS_REGISTRY_URL ?: CFG.NEXUS_URL
                    env.REGISTRY_PATH        = CFG.REGISTRY_PATH
                    env.SONAR_SERVER         = CFG.SONAR_SERVER
                    env.NEXUS_CREDENTIAL_ID  = CFG.NEXUS_CREDENTIAL_ID ?: 'Nexus_Credentials'
                    env.HARBOR_CREDENTIAL_ID = CFG.HARBOR_CREDENTIAL_ID ?: ''
                    def harborHost = CFG.HARBOR_URL?.trim() ? CFG.HARBOR_URL.replaceAll('^https?://', '') : ''
                    def harborPath = CFG.HARBOR_REGISTRY_PATH?.trim() ?: ''
                    env.HARBOR_REGISTRY = (harborHost && harborPath) ? "${harborHost}/${harborPath}" : ''

                    if (!fileExists('services.yml')) {
                        error "services.yml file not found!"
                    }
                    def servicesConfig = readYaml file: 'services.yml'
                    env.APP             = servicesConfig.app_name ?: error("'app_name' is not defined in services.yml!")
                    env.RECIPIENT_EMAIL = servicesConfig.recipients ?: ''
                    def envConfig = servicesConfig.environments?."${params.ENVIRONMENT}"
                    if (!envConfig) error "'${params.ENVIRONMENT}' environment is not defined in services.yml!"
                    env.GLOBAL_TARGETS = envConfig.deploy_targets
                        ? envConfig.deploy_targets.collect { it.toString().trim() }.join(',')
                        : ''
                    echo "VERSION: ${env.VERSION} | Environment: ${params.ENVIRONMENT} | APP: ${env.APP}"
                }
            }
        }

        stage('SonarQube Analysis')    { steps { sonarQubeAnalysis(env.SONAR_SERVER) } }
        stage('SonarQube Quality Gate') { steps { sonarQubeQualityGate() } }

        stage('Tag Check on Nexus') {
            steps {
                script {
                    boolean force = (params.FORCE_REBUILD?.toString() == 'true')
                    boolean useExisting = (params.USE_EXISTING_IMAGE?.toString() == 'true')
                    if (force) { echo "FORCE_REBUILD active, skipping tag check."; return }
                    def servicesConfig = readYaml file: 'services.yml'
                    def tagExistsMap = [:]
                    servicesConfig.services.each { svc ->
                        def key = normalizeKey(svc.name)
                        def imageName = resolveImageName(svc.name, svc.image_name)
                        def exists = checkTagOnNexus(imageName, env.VERSION, env.NEXUS_URL, env.REGISTRY_PATH, env.NEXUS_CREDENTIAL_ID ?: '')
                        tagExistsMap[key] = exists ? '1' : '0'
                        echo "Tag check [${svc.name}]: ${exists ? 'EXISTS' : 'MISSING'}"
                    }
                    tagExistsMap.each { key, value -> setTagExists(key, value) }
                    if (useExisting) {
                        def allTagsExist = tagExistsMap.every { it.value == '1' }
                        if (!allTagsExist) { error "USE_EXISTING_IMAGE=true but some tags were not found." }
                    }
                }
            }
        }

        stage('Diff Check') {
            steps {
                script {
                    boolean force = (params.FORCE_REBUILD?.toString() == 'true')
                    boolean useExisting = (params.USE_EXISTING_IMAGE?.toString() == 'true')
                    env.PULL_EXISTING = '0'
                    def servicesConfig = readYaml file: 'services.yml'
                    def prev = env.GIT_PREVIOUS_SUCCESSFUL_COMMIT
                    def curr = env.GIT_COMMIT
                    def prevOk = prev?.trim() && (sh(script: "git cat-file -e ${prev}^{commit}", returnStatus: true) == 0)
                    def currOk = curr?.trim() && (sh(script: "git cat-file -e ${curr}^{commit}", returnStatus: true) == 0)
                    def hasBaseline = prevOk && currOk
                    if (useExisting) {
                        servicesConfig.services.each { svc -> setDoFlag(normalizeKey(svc.name), '0') }
                        env.PULL_EXISTING = '1'
                    } else {
                        servicesConfig.services.each { svc ->
                            def key = normalizeKey(svc.name)
                            def exists = tagExists(key)
                            if (force || !exists || !hasBaseline) {
                                setDoFlag(key, '1')
                            } else {
                                def changed = checkDiff(svc.path, prev, curr)
                                setDoFlag(key, (changed == 1) ? '1' : '0')
                            }
                            echo "  - ${svc.name}: ${doFlag(key) == '1' ? 'Will build' : 'Will skip'}"
                        }
                    }
                }
            }
        }

        stage('Build & Push Services') {
            steps {
                script {
                    def servicesConfig = readYaml file: 'services.yml'
                    servicesConfig.services.each { svc ->
                        def key = normalizeKey(svc.name)
                        def imageName = resolveImageName(svc.name, svc.image_name)
                        if (doFlag(key) == '1') {
                            buildAndPushService([
                                serviceName      : imageName,
                                version          : env.VERSION,
                                servicePath      : svc.path,
                                dockerfile       : svc.dockerfile ?: 'Dockerfile',
                                nexusUrl         : env.NEXUS_URL,
                                nexusRegistryUrl : env.NEXUS_REGISTRY_URL,
                                registryPath     : env.REGISTRY_PATH,
                                jenkinsRegistry  : env.HARBOR_REGISTRY,
                                envFile          : svc.env_file
                            ])
                        } else {
                            echo "[${svc.name}]: no changes — skipping build & push."
                        }
                    }
                }
            }
        }

        stage('Trivy Scan')         { steps { trivyScan(env.VERSION) } }
        stage('Trivy Quality Gate') { steps { trivyQualityGate(env.APP, 'C') } }

        stage('Docker Cleanup') { steps { sh 'docker system prune -af || true' } }

        stage('Run Tests') {
            steps {
                script {
                    def servicesConfig = readYaml file: 'services.yml'
                    def targetList = resolveTargets(null)
                    def testTarget = targetList ? targetList[0] : null
                    servicesConfig.services.each { svc ->
                        def key = normalizeKey(svc.name)
                        if (doFlag(key) == '1' && svc.test_command && testTarget) {
                            runTestsInContainer([
                                serviceName      : resolveImageName(svc.name, svc.image_name),
                                version          : env.VERSION,
                                nexusUrl         : env.NEXUS_URL,
                                nexusRegistryUrl : env.NEXUS_REGISTRY_URL,
                                registryPath     : env.REGISTRY_PATH,
                                deployIp         : testTarget,
                                testCommand      : svc.test_command
                            ])
                        } else {
                            echo "[${svc.name}]: skipping tests."
                        }
                    }
                }
            }
        }

        stage('Deploy') {
            steps {
                script {
                    def servicesConfig = readYaml file: 'services.yml'
                    def credentialsMap = collectCredentials()

                    // Pull all images first
                    servicesConfig.services.each { svc ->
                        def imageName = resolveImageName(svc.name, svc.image_name)
                        resolveTargets(null).each { ip ->
                            pullService([
                                serviceName      : svc.name,
                                imageName        : imageName,
                                version          : env.VERSION,
                                deployIp         : ip,
                                nexusUrl         : env.NEXUS_URL,
                                nexusRegistryUrl : env.NEXUS_REGISTRY_URL,
                                registryPath     : env.REGISTRY_PATH,
                                sshUser          : svc.ssh_user ?: 'your-user'
                            ])
                        }
                    }

                    // Deploy (docker compose up on last service)
                    def deployingSvcs = servicesConfig.services.findAll { s ->
                        (doFlag(normalizeKey(s.name)) == '1') || env.PULL_EXISTING == '1'
                    }
                    def lastDeployingSvc = deployingSvcs ? deployingSvcs[-1] : null

                    servicesConfig.services.each { svc ->
                        def key = normalizeKey(svc.name)
                        def imageName = resolveImageName(svc.name, svc.image_name)
                        def shouldDeploy = (doFlag(key) == '1') || env.PULL_EXISTING == '1'
                        if (shouldDeploy) {
                            resolveTargets(null).each { ip ->
                                deployService([
                                    serviceName             : imageName,
                                    version                 : env.VERSION,
                                    appName                 : env.APP,
                                    deployIp                : ip,
                                    nexusUrl                : env.NEXUS_URL,
                                    nexusRegistryUrl        : env.NEXUS_REGISTRY_URL,
                                    registryPath            : env.REGISTRY_PATH,
                                    dockerComposeFile       : svc.docker_compose_file ?: 'docker-compose.yml',
                                    dockerComposeRemoteFile : svc.docker_compose_remote_file ?: svc.docker_compose_file ?: 'docker-compose.yml',
                                    envFile                 : svc.env_file,
                                    extraEnvVars            : (svc == servicesConfig.services[0]) ? credentialsMap : [:],
                                    extraFiles              : svc.extra_files ?: [],
                                    shouldPull              : false,
                                    sshUser                 : svc.ssh_user ?: 'your-user',
                                    runCompose              : (lastDeployingSvc != null && svc == lastDeployingSvc)
                                ])
                            }
                        } else {
                            echo "[${svc.name}]: no changes — skipping deploy."
                        }
                    }
                }
            }
        }
    }

    post {
        success {
            script {
                sendEmailNotification([status: 'success', appName: env.APP, recipientEmail: env.RECIPIENT_EMAIL, nexusUrl: env.NEXUS_URL])
            }
        }
        failure {
            script {
                sendEmailNotification([status: 'failure', appName: env.APP, recipientEmail: env.RECIPIENT_EMAIL, nexusUrl: env.NEXUS_URL])
            }
        }
        aborted {
            script {
                sendEmailNotification([status: 'aborted', appName: env.APP, recipientEmail: env.RECIPIENT_EMAIL, nexusUrl: env.NEXUS_URL])
            }
        }
        always {
            cleanWs(deleteDirs: true, disableDeferredWipeout: true, notFailBuild: true)
        }
    }
}

// Helper functions — add to the end of your Jenkinsfile
def normalizeKey(String name) { name?.trim()?.toUpperCase() }
def resolveImageName(String name, String imageName) { imageName ?: name }
def setTagExists(String key, String value) { env."TAG_EXISTS_${key}" = value }
def tagExists(String key) { env."TAG_EXISTS_${key}" == '1' }
def setDoFlag(String key, String value) { env."DO_${key}" = value }
def doFlag(String key) { env."DO_${key}" ?: '0' }

// Priority: DEPLOY_TARGETS env var → services.yml environments → empty list
def resolveTargets(svc) {
    if (env.DEPLOY_TARGETS?.trim()) {
        return env.DEPLOY_TARGETS.split(',').collect { it.trim() }.findAll { it }
    }
    if (env.GLOBAL_TARGETS?.trim()) {
        return env.GLOBAL_TARGETS.split(',').collect { it.trim() }.findAll { it }
    }
    return []
}
```

> **Full example:** See `Example/Jenkins_example.groovy`.

---

## Step 3: Create sonar-project.properties

Create a `sonar-project.properties` file in your project's **root directory**:

```properties
sonar.projectKey=my-project-key
sonar.projectName=My Project Name
sonar.projectVersion=1.0
sonar.sourceEncoding=UTF-8
sonar.sources=backend/src
sonar.tests=backend/tests
sonar.exclusions=**/__pycache__/**,**/node_modules/**
```

---

## Step 4: Docker Compose File

Create a Docker Compose file in the project root. The `VERSION` variable from the `.env` file is read automatically:

```yaml
services:
  backend:
    image: myapp_backend:${VERSION}
    volumes:
      - type: bind
        source: /app/my-project/default.conf
        target: /etc/nginx/conf.d/default.conf
    environment:
      - VERSION=${VERSION}

  frontend:
    image: myapp_frontend:${VERSION}
```

> **Note:** Docker Compose reads the `.env` file in the same directory by default. The `--env-file` parameter is not needed.

---

## Step 5: Create a Pipeline Job in Jenkins

1. Jenkins → **New Item**
2. Give it a name (e.g., `my-project-pipeline`)
3. Select **Pipeline** and click **OK**
4. In the **Pipeline** tab:
   - **Definition**: Pipeline script from SCM
   - **SCM**: Git
   - **Repository URL**: Your project's Git URL
   - **Credentials**: Repo access credentials if needed
   - **Branch Specifier**: `*/main`
   - **Script Path**: `Jenkinsfile` (or `Jenkinsfile_test.groovy`)
5. Click **Save**

---

## Step 6: First Build

1. Click **Build with Parameters** on the pipeline job page
2. Set the parameters:
   - **ENVIRONMENT**: `test`
   - **VERSION_TAG**: `test-v1.0`
   - **FORCE_REBUILD**: `true` (for the first build)
   - **USE_EXISTING_IMAGE**: `false`
3. Click **Build**

---

## Example Scenarios

### Scenario 1: Single Service

```yaml
app_name: my-app
recipients: 'dev@company.com'
environments:
  test:
    deploy_targets:
      - 192.168.1.10

services:
  - name: backend
    path: backend/
    dockerfile: Dockerfile
    image_name: myapp_backend
    env_file: backend/.env
    test_command: "npm test"
```

### Scenario 2: Backend + Frontend

```yaml
app_name: my-app
recipients: 'Team_1'
environments:
  test:
    deploy_targets:
      - 192.168.1.10
  prod:
    deploy_targets:
      - 192.168.3.10

services:
  - name: backend
    path: backend/
    dockerfile: Dockerfile
    image_name: myapp_backend
    env_file: .env

  - name: frontend
    path: frontend/
    dockerfile: Dockerfile
    image_name: myapp_frontend
```

### Scenario 3: Backend + Frontend + Nginx (with extra_files)

```yaml
app_name: my-app
recipients: 'Team_1, Team_2'
environments:
  test:
    deploy_targets:
      - 192.168.1.10

services:
  - name: backend
    path: backend/
    dockerfile: Dockerfile
    image_name: myapp_backend
    env_file: .env
    extra_files:
      - { src: "default.conf", dest: "/app/my-app/default.conf" }

  - name: frontend
    path: frontend/
    dockerfile: Dockerfile
    image_name: myapp_frontend

  - name: nginx
    path: nginx/
    dockerfile: Dockerfile
    image_name: myapp_nginx
    extra_files:
      - nginx/nginx.conf
```

---

## Project-Specific Settings

### Defining Credentials

Use credentials defined in Jenkins in the `environment` block:

```groovy
environment {
    VERSION = "${params.VERSION_TAG ?: 'test-v1.0'}"

    // Credentials — automatically written to .env file
    PSQL_HOST = credentials('psql_myproject_host')
    PSQL_PASS = credentials('psql_myproject_pass')
    JWT_SECRET_KEY = credentials('jwt_myproject_secret')
}
```

In the `deployService()` call, all credentials are automatically collected and written to the `.env` file via `extraEnvVars: collectCredentials()`.

### Updating Email Recipients

Add team email lists to the `emailTeams.groovy` file:

```groovy
def call() {
    return [
        'Team_1': 'dev1@company.com, dev2@company.com',
        'Team_2': 'ops@company.com',
    ]
}
```

Use in `services.yml` with `recipients: 'Team_1, Team_2'`.

### Updating globalConfig.groovy

Fill in `vars/globalConfig.groovy` for your environment:

```groovy
def call() {
    return [
        NEXUS_URL           : 'http://192.168.1.100:8081',
        NEXUS_REGISTRY_URL  : 'http://192.168.1.100:8090',
        REGISTRY_PATH       : 'my-registry',
        NEXUS_CREDENTIAL_ID : 'Nexus_Credentials',
        HARBOR_URL          : '',   // Leave empty if Harbor is not used
        HARBOR_REGISTRY_PATH: '',
        HARBOR_CREDENTIAL_ID: '',
        SONAR_SERVER        : 'my-sonar-server',
        TRIVY_HOST          : '192.168.1.200',
        TRIVY_SSH_USER      : 'jenkins',
        TRIVY_SCRIPT_PATH   : '/app/trivy-dashboard/trigger-nexus.sh',
    ]
}
```

---

## Important Notes

1. **Helper Functions:** Don't forget to add the helper functions at the end of your Jenkinsfile
2. **services.yml:** The `app_name` and `environments` fields are required
3. **SonarQube:** Each project must have a `sonar-project.properties` file
4. **SSH Access:** SSH access to the deploy server must be configured (`01-cicd-preparation.md`)
5. **pullService:** Called separately before running Compose; `deployService` uses `shouldPull: false`

---

## Troubleshooting

**services.yml not found:** Make sure the file is in the project root directory and has been committed to Git.

**Shared library not found:** Jenkins → Manage Jenkins → Configure System → Global Pipeline Libraries → Check that `devops-jenkins-library` is defined.

**extra_files not being copied:** Check logs for the `extra file/directory will be copied...` message and verify the source file exists in the project.

For detailed troubleshooting: **[FAQ.md](./FAQ.md)**

---

## More Information

- **Function References:** [../README.md](../README.md)
- **Frequently Asked Questions:** [FAQ.md](./FAQ.md)
- **Real Examples:** [../Example/](../Example/)
- **CI/CD Pre-Setup:** [01-cicd-preparation.md](./01-cicd-preparation.md)
