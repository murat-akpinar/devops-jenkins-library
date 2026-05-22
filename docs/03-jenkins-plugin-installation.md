# Jenkins Plugin Installation Guide

> Turkce dokumantasyon icin: **[03-jenkins-plugin-kurulumu.md](./03-jenkins-plugin-kurulumu.md)**

This guide lists the plugins that must be installed in Jenkins for the shared library to function.

---

## Required Plugins

| Plugin Name | Plugin ID | Used For |
|-------------|-----------|----------|
| **Pipeline (Workflow Aggregator)** | `workflow-aggregator` | Declarative Pipeline, `@Library`, shared library support |
| **Git** | `git` | Fetching the shared library from Git SCM |
| **Pipeline Utility Steps** | `pipeline-utility-steps` | `readYaml`, `readFile`, `writeFile`, `fileExists` steps |
| **SonarQube Scanner** | `sonar` | `withSonarQubeEnv()`, `waitForQualityGate()` |
| **Email Extension** | `email-ext` | `emailext()` — HTML email notifications |
| **Credentials Binding** | `credentials-binding` | `withCredentials()`, `usernamePassword()` |
| **Credentials** | `credentials` | Jenkins credential management (Nexus, Harbor) |

---

## Installation Methods

### Method 1: Jenkins UI (Recommended)

1. Go to **Manage Jenkins** → **Plugins** → **Available plugins**
2. Search for each plugin name and check the box next to it
3. After selecting all plugins, click the **Install** button
4. When installation is complete, check **Restart Jenkins when installation is complete**

### Method 2: Jenkins CLI

```bash
java -jar jenkins-cli.jar -s http://JENKINS_URL/ install-plugin \
  workflow-aggregator \
  git \
  pipeline-utility-steps \
  sonar \
  email-ext \
  credentials-binding \
  credentials
```

### Method 3: plugins.txt (for Docker/IaC setups)

If you are using a Jenkins Docker image, add to `plugins.txt`:

```
workflow-aggregator:latest
git:latest
pipeline-utility-steps:latest
sonar:latest
email-ext:latest
credentials-binding:latest
credentials:latest
```

---

## Post-Installation Configuration

### Configuring the SonarQube Server

The SonarQube server must be registered in Jenkins for `sonarQubeAnalysis()` to work:

1. **Manage Jenkins** → **Configure System** → **SonarQube servers**
2. Click **Add SonarQube**
3. Fill in the following:

   | Field | Value |
   |-------|-------|
   | **Name** | Must match the `SONAR_SERVER` value in `globalConfig.groovy` |
   | **Server URL** | SonarQube server URL |
   | **Server authentication token** | Token generated from SonarQube (add as a Jenkins credential) |

4. Click **Save**

### Configuring the Email Server

SMTP server configuration is required for `sendEmailNotification()` to work:

1. **Manage Jenkins** → **Configure System** → **Extended E-mail Notification**
2. Enter the SMTP server details:

   | Field | Description |
   |-------|-------------|
   | **SMTP server** | SMTP server address |
   | **SMTP Port** | Usually `587` (TLS) or `465` (SSL) |
   | **Credentials** | SMTP username and password |
   | **Use SSL/TLS** | Check according to your SMTP server |

3. Optionally add a default recipient in the **Default Recipients** field
4. Click **Save**

---

## Verifying the Installation

After all plugins are installed, run the following test:

```groovy
// Test pipeline — create a new Pipeline job in Jenkins and run it
pipeline {
    agent any
    stages {
        stage('Plugin Test') {
            steps {
                script {
                    // Pipeline Utility Steps test
                    writeFile file: 'test.yaml', text: 'key: value'
                    def data = readYaml file: 'test.yaml'
                    echo "readYaml works: ${data.key}"
                    sh 'rm test.yaml'
                }
            }
        }
    }
}
```

---

## Common Issues

**`readYaml` step not found:**
- Verify that **Pipeline Utility Steps** is installed
- Restart Jenkins

**`withSonarQubeEnv` step not found:**
- Verify that **SonarQube Scanner** is installed
- Check that a server is defined under **Manage Jenkins** → **Configure System** → **SonarQube servers**

**`emailext` step not found:**
- Verify that **Email Extension** is installed
- Check that SMTP settings are configured under **Manage Jenkins** → **Configure System** → **Extended E-mail Notification**

**`withCredentials` / `usernamePassword` not working:**
- Verify that **Credentials Binding** is installed
- Check that the relevant credentials are defined in Jenkins (`Nexus_Credentials`, `Harbor_Credentials`)

---

## Next Step

After plugin installation is complete:

1. Edit `globalConfig.groovy` for your environment
2. Define Jenkins credentials (`Nexus_Credentials`, `Harbor_Credentials`)
3. Register the shared library in Jenkins

For more details, see [`01-cicd-preparation.md`](./01-cicd-preparation.md) and [`02-pipeline-setup.md`](./02-pipeline-setup.md).
