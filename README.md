[![License: GPL v3](https://img.shields.io/badge/license-GPLv3-1a1a1a?style=flat-square&labelColor=1a1a1a&color=8a6f3a)](LICENSE)
[![Built with Claude Code](https://img.shields.io/badge/built%20with-Claude%20Code-1a1a1a?style=flat-square&labelColor=1a1a1a&color=d8b66b)](https://claude.ai/claude-code)
[![Status](https://img.shields.io/badge/status-stable-1a1a1a?style=flat-square&labelColor=1a1a1a&color=2d7a2d)](https://github.com/murat-akpinar/devops-jenkins-library)
[![Jenkins](https://img.shields.io/badge/Jenkins-Shared%20Library-1a1a1a?style=flat-square&labelColor=1a1a1a&color=D33833&logo=jenkins&logoColor=fff)](https://www.jenkins.io/doc/book/pipeline/shared-libraries/)
[![Groovy](https://img.shields.io/badge/Groovy-4.x-1a1a1a?style=flat-square&labelColor=1a1a1a&color=4298B8&logo=apachegroovy&logoColor=fff)](https://groovy-lang.org)
[![Docker](https://img.shields.io/badge/Docker-compose-1a1a1a?style=flat-square&labelColor=1a1a1a&color=2496ED&logo=docker&logoColor=fff)](https://www.docker.com)
[![SonarQube](https://img.shields.io/badge/SonarQube-analysis-1a1a1a?style=flat-square&labelColor=1a1a1a&color=4E9BCD&logo=sonarqube&logoColor=fff)](https://www.sonarsource.com/products/sonarqube/)
[![Trivy](https://img.shields.io/badge/Trivy-security%20scan-1a1a1a?style=flat-square&labelColor=1a1a1a&color=1904DA)](https://trivy.dev)
[![Nexus](https://img.shields.io/badge/Nexus-registry-1a1a1a?style=flat-square&labelColor=1a1a1a&color=1B75BB)](https://www.sonatype.com/products/sonatype-nexus-repository)

# Jenkins Shared Library

> Turkce dokumantasyon icin: **[README.TR.md](README.TR.md)**

A shared library for Jenkins CI/CD pipelines containing common functions and configurations.

## Table of Contents

- [Quick Start](#quick-start)
- [Directory Structure](#directory-structure)
- [Installation](#installation)
- [Usage](#usage)
- [Functions](#functions)
- [services.yml Configuration](#servicesyml-configuration)
- [Multi-Server Deployment](#multi-server-deployment)
- [Troubleshooting](#troubleshooting)

---

## Quick Start

### Pre-CI/CD Preparation

Before setting up a CI/CD pipeline, **you must** read `docs/01-cicd-preparation.md` for the required preparation steps:

- Requesting network permissions
- Docker insecure registry settings
- Nexus connection configuration
- SSH key setup

**[CI/CD Pre-Pipeline Preparation Guide →](docs/01-cicd-preparation.md)**

### Using in a New Project

1. **Complete the preparation steps** (`docs/01-cicd-preparation.md`)
2. **Create a Jenkinsfile** (example: `Example/Jenkins_example.groovy`)
3. **Create services.yml** (example: `Example/services_example.yml`)
4. **Create a pipeline job in Jenkins**

For detailed steps: **[Usage Guide →](docs/USAGE_GUIDE.md)**

---

## Directory Structure

```
devops-jenkins-library/
├── vars/                              # Pipeline functions (Groovy)
│   ├── globalConfig.groovy            # Shared config (Nexus, Harbor, Trivy, SonarQube)
│   ├── collectCredentials.groovy      # Credentials collection
│   ├── sonarQubeAnalysis.groovy       # SonarQube analysis
│   ├── sonarQubeQualityGate.groovy    # SonarQube Quality Gate
│   ├── checkTagOnNexus.groovy         # Nexus tag check
│   ├── checkDiff.groovy               # Git diff check
│   ├── buildAndPushService.groovy     # Docker build & push
│   ├── pullService.groovy             # Docker image pull to server
│   ├── runTestsInContainer.groovy     # Run tests in container
│   ├── deployService.groovy           # Deploy operation
│   ├── trivyScan.groovy               # DockerScan security scan
│   ├── trivyQualityGate.groovy        # DockerScan Quality Gate check
│   ├── checkovScan.groovy             # Checkov IaC security scan
│   ├── osvScan.groovy                 # OSV dependency vulnerability scan
│   ├── sendEmailNotification.groovy   # Email notification
│   └── emailTeams.groovy              # Team email list
├── Example/                           # Example files
│   ├── Jenkins_example.groovy         # Full Jenkinsfile example
│   └── services_example.yml          # services.yml example
├── docs/                              # Documentation
│   ├── 01-cicd-hazirliklari.md       # Pre-CI/CD preparation guide
│   ├── 02-pipeline-kurulumu.md       # Pipeline setup steps
│   ├── 03-jenkins-plugin-kurulumu.md # Required Jenkins plugins
│   ├── KULLANIM_KILAVUZU.md          # Detailed usage guide
│   └── SSS.md                        # Frequently asked questions
├── credentials_test.groovy            # Bulk load test environment credentials
├── credentials_prod.groovy            # Bulk load prod environment credentials
└── README.md                          # This file
```

---

## Installation

### 1. Define the Global Library in Jenkins

1. **Manage Jenkins** → **Configure System** → **Global Pipeline Libraries**
2. Click **Add** under **Global Untrusted Pipeline Libraries**
3. Enter the following:
   - **Name**: `devops-jenkins-library`
   - **Default version**: `main`
   - **Retrieval method**: **Modern SCM**
   - **Source Code Management**: **Git**
   - **Project Repository**: URL of this repo
   - **Credentials**: Repo access credentials if required

### 2. Define Jenkins Credentials

**Manage Jenkins** → **Credentials** → **System** → **Global credentials** → **Add Credentials**

| ID | Type | Description |
|----|------|-------------|
| `Nexus_Credentials` | Username with password | Nexus Docker registry username and password |
| `Harbor_Credentials` | Username with password | Harbor registry (optional, leave empty if not used) |

> **Note:** IDs are case-sensitive and must match the `NEXUS_CREDENTIAL_ID` value in `globalConfig.groovy`.

### 3. Required Jenkins Plugins

The following plugins must be installed in Jenkins:

| Plugin | Plugin ID | Why Required |
|--------|-----------|--------------|
| Pipeline (Workflow Aggregator) | `workflow-aggregator` | Declarative Pipeline and `@Library` support |
| Git | `git` | Fetching the shared library from Git |
| Pipeline Utility Steps | `pipeline-utility-steps` | `readYaml`, `readFile`, `writeFile`, `fileExists` |
| SonarQube Scanner | `sonar` | `withSonarQubeEnv()`, `waitForQualityGate()` |
| Email Extension | `email-ext` | HTML email notifications (`emailext`) |
| Credentials Binding | `credentials-binding` | `withCredentials()`, `usernamePassword()` |
| Credentials | `credentials` | Nexus/Harbor credential management |

For installation steps and troubleshooting: **[docs/03-jenkins-plugin-installation.md](docs/03-jenkins-plugin-installation.md)**

---

## Usage

### Basic Usage

```groovy
@Library('devops-jenkins-library') _

pipeline {
    agent { label "linux" }

    parameters {
        choice(name: 'ENVIRONMENT', choices: ['test', 'preprod', 'prod'], description: 'Select deploy environment')
        booleanParam(name: 'FORCE_REBUILD', defaultValue: false, description: 'Force rebuild all images')
        string(name: 'VERSION_TAG', defaultValue: 'test-v1.0', description: 'Image tag to deploy')
    }

    environment {
        VERSION = "${params.VERSION_TAG ?: 'test-v1.0'}"
    }

    stages {
        stage('Initialize') {
            steps {
                script {
                    def CFG = globalConfig()
                    env.NEXUS_URL           = CFG.NEXUS_URL
                    env.NEXUS_REGISTRY_URL  = CFG.NEXUS_REGISTRY_URL ?: CFG.NEXUS_URL
                    env.REGISTRY_PATH       = CFG.REGISTRY_PATH
                    env.SONAR_SERVER        = CFG.SONAR_SERVER
                    env.NEXUS_CREDENTIAL_ID = CFG.NEXUS_CREDENTIAL_ID ?: 'Nexus_Credentials'

                    def servicesConfig = readYaml file: 'services.yml'
                    env.APP             = servicesConfig.app_name
                    env.RECIPIENT_EMAIL = servicesConfig.recipients ?: ''
                    def envConfig = servicesConfig.environments?."${params.ENVIRONMENT}"
                    env.GLOBAL_TARGETS = envConfig?.deploy_targets
                        ? envConfig.deploy_targets.collect { it.toString().trim() }.join(',')
                        : ''
                }
            }
        }
        // Other stages...
    }
}
```

Full example: **[Example/Jenkins_example.groovy](Example/Jenkins_example.groovy)**

---

## Functions

### `globalConfig()`

Returns the shared configuration (Nexus, Harbor, SonarQube, Trivy). Edit `globalConfig.groovy` to match your environment.

**Return value:**
```groovy
[
    // Nexus REST API (for tag checks)
    NEXUS_URL           : "http://YOUR_NEXUS_IP:8081",
    // Nexus Docker registry (for push/pull)
    NEXUS_REGISTRY_URL  : "http://YOUR_NEXUS_IP:8090",
    REGISTRY_PATH       : "your-registry",
    NEXUS_CREDENTIAL_ID : "Nexus_Credentials",

    // Harbor (optional — leave empty if not used)
    HARBOR_URL          : "",
    HARBOR_REGISTRY_PATH: "",
    HARBOR_CREDENTIAL_ID: "",

    // SonarQube
    SONAR_SERVER        : "your-sonar-server",

    // Trivy — DockerScan: https://github.com/murat-akpinar/DockerScan
    DOCKERSCAN_HOST              : "YOUR_DOCKERSCAN_HOST_IP",
    DOCKERSCAN_SSH_USER          : "your-user",
    DOCKERSCAN_SCRIPT_PATH       : "/app/DockerScan/trigger-nexus.sh",
    DOCKERSCAN_BACKEND_PORT      : "3018",

    // Checkov — IaC security scan
    CHECKOV_ENABLED     : true,                // false → skip step
    CHECKOV_SOFT_FAIL   : true,                // true → pipeline continues on failure
    CHECKOV_SCRIPT_PATH : "/app/vaultscan/trigger-checkov.sh",

    // OSV-Scanner — dependency vulnerability scan
    OSV_ENABLED         : true,                // false → skip step
    OSV_SOFT_FAIL       : true,                // true → pipeline continues on failure
    OSV_SCRIPT_PATH     : "/app/vaultscan/trigger-osv.sh",
]
```

---

### `collectCredentials()`

Automatically collects credentials from the pipeline `environment` block.

**Return value:** `Map<String, String>`

**Features:**
- Automatically filters system variables (`JENKINS_*`, `BUILD_*`, `DO_*`, `GIT_*`, etc.)
- No manual list required

**Usage:**
```groovy
def credentialsMap = collectCredentials()
deployService([
    // ...
    extraEnvVars: credentialsMap
])
```

---

### `sonarQubeAnalysis(serverName)`

Runs SonarQube code analysis.

**Parameters:**
- `serverName` (String): SonarQube server name as defined in Jenkins

> A `sonar-project.properties` file must exist in the project root.

```groovy
sonarQubeAnalysis(env.SONAR_SERVER)
```

---

### `sonarQubeQualityGate()`

Checks the SonarQube Quality Gate result.

```groovy
sonarQubeQualityGate()
```

---

### `checkTagOnNexus(serviceName, version, nexusUrl, registryPath[, credentialId])`

Checks whether the specified tag exists in the Nexus registry.

**Parameters:**
- `serviceName` (String): Service/image name
- `version` (String): Tag version
- `nexusUrl` (String): Nexus REST API URL
- `registryPath` (String): Registry path
- `credentialId` (String, optional): Jenkins credential ID (default: `'Nexus_Credentials'`)

**Return value:** `true` or `false`

```groovy
def exists = checkTagOnNexus(imageName, env.VERSION, env.NEXUS_URL, env.REGISTRY_PATH, env.NEXUS_CREDENTIAL_ID)
```

---

### `checkDiff(servicePath, prevCommit, currCommit)`

Checks for git diff at the specified path.

**Parameters:**
- `servicePath` (String): Path to check (e.g. `backend/`)
- `prevCommit` (String): Previous commit hash
- `currCommit` (String): Current commit hash

**Return value:** `1` (changes detected) or `0` (no changes)

```groovy
def changed = checkDiff(svc.path, env.GIT_PREVIOUS_SUCCESSFUL_COMMIT, env.GIT_COMMIT)
```

---

### `buildAndPushService(config)`

Builds a Docker image and pushes it to Nexus and optionally Harbor. Before building, it automatically runs `checkovScan` (IaC security scan) and `osvScan` (dependency vulnerability scan).

**Parameters (Map):**

| Parameter | Required | Description |
|-----------|----------|-------------|
| `serviceName` | ✅ | Service/image name |
| `version` | ✅ | Tag version |
| `servicePath` | ✅ | Service directory (Docker context) |
| `dockerfile` | ❌ | Dockerfile path (default: `Dockerfile`) |
| `nexusUrl` | ✅ | Nexus REST API URL |
| `nexusRegistryUrl` | ❌ | Nexus Docker registry URL (default: `nexusUrl`) |
| `registryPath` | ✅ | Registry path |
| `jenkinsRegistry` | ❌ | Harbor registry address (`HOST/PROJECT`) |
| `envFile` | ❌ | .env file path |
| `nexusRepoCredentialId` | ❌ | Nexus credential ID (default: `NEXUS_CREDENTIAL_ID`) |
| `harborCredentialId` | ❌ | Harbor credential ID |

```groovy
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
```

---

### `pullService(config)`

Pulls a Docker image from Nexus to the target server and tags it locally.

**Parameters (Map):**

| Parameter | Required | Description |
|-----------|----------|-------------|
| `serviceName` | ✅ | Service name |
| `imageName` | ❌ | Image name (default: `serviceName`) |
| `version` | ✅ | Tag version |
| `deployIp` | ✅ | Target server IP |
| `nexusUrl` | ✅ | Nexus URL |
| `nexusRegistryUrl` | ❌ | Nexus Docker registry URL |
| `registryPath` | ✅ | Registry path |
| `sshUser` | ❌ | SSH username (default: `your-user`) |
| `nexusCredentialId` | ❌ | Nexus credential ID |

```groovy
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
```

---

### `runTestsInContainer(config)`

Runs tests inside a container.

**Parameters (Map):**
- `serviceName` (String): Service/image name
- `version` (String): Tag version
- `nexusUrl` (String): Nexus URL
- `nexusRegistryUrl` (String, optional): Nexus Docker registry URL
- `registryPath` (String): Registry path
- `deployIp` (String): Test server IP
- `testCommand` (String): Test command

```groovy
runTestsInContainer([
    serviceName      : imageName,
    version          : env.VERSION,
    nexusUrl         : env.NEXUS_URL,
    nexusRegistryUrl : env.NEXUS_REGISTRY_URL,
    registryPath     : env.REGISTRY_PATH,
    deployIp         : testTarget,
    testCommand      : svc.test_command
])
```

---

### `deployService(config)`

Deploys a service to the target server.

**Parameters (Map):**

| Parameter | Required | Description |
|-----------|----------|-------------|
| `serviceName` | ✅ | Service name |
| `version` | ✅ | Tag version |
| `appName` | ✅ | Application name (deploy path: `/app/<appName>/`) |
| `deployIp` | ✅ | Deploy server IP |
| `nexusUrl` | ✅ | Nexus URL |
| `registryPath` | ✅ | Registry path |
| `dockerComposeFile` | ❌ | Docker Compose file (default: `docker-compose.yml`) |
| `dockerComposeRemoteFile` | ❌ | Name to use on the remote server |
| `envFile` | ❌ | .env file path |
| `extraFiles` | ❌ | Additional files/directories list |
| `extraEnvVars` | ❌ | Extra environment variables (credentials) |
| `shouldPull` | ❌ | Pull Docker image (default: `false`) |
| `imageName` | ❌ | Docker image name (default: `serviceName`) |
| `sshUser` | ❌ | SSH username (default: `your-user`) |
| `runCompose` | ❌ | Run `docker compose up -d` (default: `true`) |

```groovy
deployService([
    serviceName             : imageName,
    version                 : env.VERSION,
    appName                 : env.APP,
    deployIp                : ip,
    nexusUrl                : env.NEXUS_URL,
    registryPath            : env.REGISTRY_PATH,
    dockerComposeFile       : svc.docker_compose_file ?: 'docker-compose.yml',
    dockerComposeRemoteFile : svc.docker_compose_remote_file ?: 'docker-compose.yml',
    envFile                 : svc.env_file,
    extraEnvVars            : credentialsMap,
    extraFiles              : svc.extra_files,
    shouldPull              : false,
    sshUser                 : svc.ssh_user ?: 'your-user',
    runCompose              : true
])
```

**extraFiles Formats:**
```yaml
extra_files:
  - default.conf                    # → /app/myapp/default.conf
  - nginx/nginx.conf                # → /app/myapp/nginx.conf (basename)
  - backend                         # → /app/myapp/backend/ (directory)
  - src: webrtc/config.env          # Map format
    dest: /app/myapp/config.env     # Custom destination path
```

---

### `trivyScan(tag)`

Triggers a DockerScan security scan on built images. Only services whose `DO_` flag is `'1'` (built) are scanned.

**Parameters:**
- `tag` (String): Image tag to scan

> A `services.yml` file must exist in the project root.

> This function SSH-connects to the Trivy host and runs the scan via the **[DockerScan](https://github.com/murat-akpinar/DockerScan)** trigger script. DockerScan must be installed and running on the host defined in `DOCKERSCAN_HOST`.

```groovy
trivyScan(env.VERSION)
```

---

### `trivyQualityGate(projectName, grade)`

Queries the DockerScan Dashboard API to check the security grade of the project.

**Parameters:**
- `projectName` (String): Project name in DockerScan Dashboard (usually `env.APP`)
- `grade` (String, optional): Minimum passing grade (default: `'C'`)

**Grade scale:** `A` (best) → `B` → `C` → `D` → `F` (worst)

```groovy
trivyQualityGate(env.APP, 'C')  // A, B, C pass; D, F fail the pipeline
trivyQualityGate(env.APP, 'D')  // A, B, C, D pass; only F fails
```

---

### `checkovScan(servicePath, imageName, tag)`

Runs a Checkov IaC (Infrastructure as Code) security scan over the service source files. Triggered automatically by `buildAndPushService()` before each build.

**Parameters:**
- `servicePath` (String): Service directory (source files to scan)
- `imageName` (String): Service/image name
- `tag` (String): Image tag

**Controlled via `globalConfig()`:**

| Key | Default | Description |
|-----|---------|-------------|
| `CHECKOV_ENABLED` | `true` | `false` → skip step |
| `CHECKOV_SOFT_FAIL` | `true` | `true` → pipeline continues on failure |
| `CHECKOV_SCRIPT_PATH` | `/app/vaultscan/trigger-checkov.sh` | Trigger script path on remote host |
| `TRIVY_HOST` | `YOUR_TRIVY_HOST_IP` | Remote scan host |
| `TRIVY_SSH_USER` | `your-user` | SSH username |

---

### `osvScan(servicePath, imageName, tag)`

Runs an OSV-Scanner dependency vulnerability scan over the service source files. Triggered automatically by `buildAndPushService()` before each build.

**Parameters:**
- `servicePath` (String): Service directory (source files to scan)
- `imageName` (String): Service/image name
- `tag` (String): Image tag

**Controlled via `globalConfig()`:**

| Key | Default | Description |
|-----|---------|-------------|
| `OSV_ENABLED` | `true` | `false` → skip step |
| `OSV_SOFT_FAIL` | `true` | `true` → pipeline continues on failure |
| `OSV_SCRIPT_PATH` | `/app/vaultscan/trigger-osv.sh` | Trigger script path on remote host |
| `TRIVY_HOST` | `YOUR_TRIVY_HOST_IP` | Remote scan host |
| `TRIVY_SSH_USER` | `your-user` | SSH username |

---

### `sendEmailNotification(config)`

Sends HTML email notifications for pipeline results. Automatically resolves team keys defined via `emailTeams()` to email addresses.

**Parameters (Map):**
- `status` (String): `'success'`, `'failure'`, or `'aborted'`
- `appName` (String): Application name
- `recipientEmail` (String): Recipient address or `emailTeams()` key (comma-separated for multiple)
- `nexusUrl` (String, optional): Nexus URL

```groovy
sendEmailNotification([
    status        : 'success',
    appName       : env.APP,
    recipientEmail: env.RECIPIENT_EMAIL,  // e.g. 'Team_1, Team_2'
    nexusUrl      : env.NEXUS_URL
])
```

---

### `emailTeams()`

Returns a map of team name → email address. Used automatically by `sendEmailNotification()`.

**Return value:** `Map<String, String>`

Fill in `emailTeams.groovy` with your team list and email addresses:

```groovy
def call() {
    return [
        'Team_1': 'dev1@company.com, dev2@company.com',
        'Team_2': 'ops@company.com',
    ]
}
```

Used in `services.yml` with `recipients: 'Team_1, Team_2'`.

---

## services.yml Configuration

`services.yml` defines the project configuration, environments, and services.

### Full Structure

```yaml
app_name: my-project                   # Application name (deploy path: /app/my-project/)
recipients: 'Team_1, Team_2'           # Email recipients (emailTeams key or direct address)

environments:                          # Deploy targets per environment
  test:
    deploy_targets:
      - 192.168.1.10
      - 192.168.1.11
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
    docker_compose_file: docker-compose.yml       # Compose file (optional)
    docker_compose_remote_file: docker-compose.yml # Remote file name (optional)
    extra_files:                       # Additional files/directories (optional)
      - { src: "default.conf", dest: "/app/my-project/default.conf" }
    ssh_user: your-user                # SSH username (optional)
    test_command: "npm test"           # Test command (optional)

  - name: frontend
    path: frontend/
    dockerfile: Dockerfile
    image_name: myapp_frontend
    env_file: frontend/.env
```

### Field Reference

**Top-level Fields:**

| Field | Required | Description |
|-------|----------|-------------|
| `app_name` | ✅ | Application name (read as `env.APP` in the pipeline) |
| `recipients` | ❌ | Email recipients (comma-separated team keys or addresses) |
| `environments` | ✅ | Deploy targets per environment |

**Service Fields:**

| Field | Required | Description |
|-------|----------|-------------|
| `name` | ✅ | Service name |
| `path` | ✅ | Service directory (for git diff) |
| `dockerfile` | ✅ | Dockerfile path |
| `image_name` | ✅ | Docker image name |
| `env_file` | ❌ | .env file path (copied from Git for the first service) |
| `docker_compose_file` | ❌ | Compose file path |
| `docker_compose_remote_file` | ❌ | Name to use on the remote server |
| `extra_files` | ❌ | Additional files/directories list |
| `ssh_user` | ❌ | SSH username |
| `test_command` | ❌ | Test command (run in the Run Tests stage) |

### .env File Management

- **First service:** If `env_file` is specified, the file from Git is used; `VERSION` and credentials are added/updated
- **Other services:** The existing `.env` on the server is read; only `VERSION` is updated
- All services share the common `/app/<app_name>/.env` file

---

## Multi-Server Deployment

Deploy targets are read from the `environments` section of `services.yml`:

```yaml
environments:
  test:
    deploy_targets:
      - 192.168.1.10
      - 192.168.1.11
  prod:
    deploy_targets:
      - 192.168.3.10
```

The pipeline uses the `deploy_targets` list for the selected `ENVIRONMENT` parameter (`test`, `preprod`, `prod`) and deploys to all targets sequentially.

---

## Troubleshooting

**services.yml not found:**
- Ensure the file is in the project root directory
- Ensure it has been committed to Git

**Shared library not found:**
- Jenkins → Manage Jenkins → Configure System → Global Pipeline Libraries
- Check that `devops-jenkins-library` is defined

**readYaml error:**
- Check that the Pipeline Utility Steps plugin is installed

**extra_files not being copied:**
- Check that `extra_files` is defined in `services.yml`
- Check logs for the `extra file/directory will be copied...` message

For detailed troubleshooting: **[docs/FAQ.md](docs/FAQ.md)**

---

## Documentation

- **[docs/01-cicd-preparation.md](docs/01-cicd-preparation.md)** — Pre-CI/CD pipeline preparation guide
- **[docs/02-pipeline-setup.md](docs/02-pipeline-setup.md)** — Pipeline setup steps
- **[docs/03-jenkins-plugin-installation.md](docs/03-jenkins-plugin-installation.md)** — Required Jenkins plugins and configuration
- **[docs/USAGE_GUIDE.md](docs/USAGE_GUIDE.md)** — Detailed usage guide
- **[docs/FAQ.md](docs/FAQ.md)** — Frequently asked questions
- **[Example/Jenkins_example.groovy](Example/Jenkins_example.groovy)** — Full Jenkinsfile example
- **[Example/services_example.yml](Example/services_example.yml)** — services.yml example

---

## Contributing

1. Make your changes
2. Test them
3. Open a Pull Request

---

## License

This project is for corporate use.
