# CI/CD Pipeline Pre-Setup Guide

> Turkce dokumantasyon icin: **[01-cicd-hazirliklari.md](./01-cicd-hazirliklari.md)**

This guide covers all preparation steps that must be completed **before** setting up a Jenkins CI/CD pipeline. Intended for first-time CI/CD setup users.

---

## Overview

The following preparations are required for the CI/CD pipeline to function:

1. ✅ **Network Permissions** — Open network connections between servers
2. ✅ **Docker Registry Settings** — Allow Docker to use insecure registries
3. ✅ **Nexus Connection** — Access to the container registry
4. ✅ **SSH Key Setup** — Secure connection between servers
5. ✅ **DockerScan Setup** — Prepare the security scanning tool

---

## 1. Network Permissions (Network-to-Network Request)

The application server must be able to reach specific servers and ports for the CI/CD pipeline to work.

### Step 1: Submit a request to the network team

Use the following template to submit a network access request:

```
Hello,

For our CI/CD processes, the [APP-IP] server needs access to the Jenkins, Nexus, and Harbor servers.
The Jenkins agent server transfers files to the application server over SSH.

Required network permissions:

JENKINS_AGENT_IP > [APP-IP]       PORT 22
[APP-IP]         > NEXUS_IP       PORT 8090
[APP-IP]         > JENKINS_AGENT_IP PORT 7000

Thank you.
```

> **Note:** Replace `[APP-IP]` with the actual application server IP address.

### Step 2: Wait for permissions to be granted

Once the network team opens the ports, verify connectivity:

```bash
# Test from the application server
telnet NEXUS_IP 8090           # Nexus connection
telnet JENKINS_AGENT_IP 7000   # Jenkins registry connection
```

---

## 2. Docker Insecure Registry Settings

The application server's Docker daemon must be configured to connect to internal registries (Nexus, Harbor) via `insecure-registries`.

### Step 1: Check the Docker daemon.json file

```bash
# SSH into the application server
ssh system@[APP-IP]

# Check if the daemon.json file exists
cat /etc/docker/daemon.json
```

### Step 2: Edit daemon.json

If the file does not exist, create it. If it does, add the `insecure-registries` entry:

```bash
# Edit with root privileges
sudo nano /etc/docker/daemon.json
```

The file should look like this:

```json
{
  "other-settings": "other-values",
  "insecure-registries": ["NEXUS_IP:8081", "NEXUS_IP:8082", "YOUR_HARBOR_IP"]
}
```

> **Note:** If the file already has other settings, only add the `insecure-registries` field and don't forget the comma.

### Step 3: Restart the Docker service

```bash
sudo systemctl restart docker.service
sudo systemctl status docker.service
```

### Step 4: Verify the settings

```bash
docker info | grep -A 5 "Insecure Registries"
```

---

## 3. Nexus Container Registry Connection

The application server must log in to the Nexus container registry.

### Step 1: Log in to Nexus

On the application server, log in as the `system` user:

```bash
docker login NEXUS_IP:8082
```

Enter the following when prompted:
- **Username:** `your-nexus-user`
- **Password:** `your-nexus-password`

---

## 4. SSH Key Setup and Connection Test

The Jenkins agent server must be able to connect to the application server via key-based SSH authentication.

### Step 1: Connect to the Jenkins Agent server

```bash
ssh [user]@jenkins-agent-hostname
sudo -i
su - jenkins
```

### Step 2: Copy the SSH key

```bash
ssh-copy-id system@[APP-IP]
```

> **Note:** If connecting for the first time, type `yes` when asked "Are you sure you want to continue connecting (yes/no)?".

Enter the `system` user's password if prompted.

### Step 3: Perform the first connection (Known Hosts)

After copying the SSH key, **you must** connect manually at least once so the server is added to the known hosts list:

```bash
ssh system@[APP-IP]
exit
```

> **Warning:** If you skip this step, the Jenkins pipeline will fail with "Host key verification failed".

### Step 4: Verify passwordless connection

```bash
ssh system@[APP-IP]

# Run a command to verify
ssh system@[APP-IP] "whoami"
# Expected output: system

exit
```

---

## 5. DockerScan Setup

[DockerScan](https://github.com/murat-akpinar/DockerScan) is a Trivy-based security scanning and dashboard tool. The Jenkins pipeline connects via SSH to run `trigger-nexus.sh` on the DockerScan server; the script scans the image with Trivy and saves results as JSON. The quality gate check is performed by the Jenkins agent querying the backend API on `DOCKERSCAN_BACKEND_PORT` (default: `3018`).

> **Repo:** [https://github.com/murat-akpinar/DockerScan](https://github.com/murat-akpinar/DockerScan)

| Port | Service | Description |
|------|---------|-------------|
| `3017` | Frontend (nginx) | Web dashboard UI |
| `3018` | Backend API (Go) | Quality gate queries |

### Step 1: Prepare the DockerScan server

```bash
# Connect to the DockerScan server
ssh [user]@[DOCKERSCAN-IP]

# Clone the repository to /app/DockerScan (default path in globalConfig)
git clone https://github.com/murat-akpinar/DockerScan.git /app/DockerScan

cd /app/DockerScan

# Create the .env file
cp .example.env .env
# Edit FRONTEND_PORT, BACKEND_PORT, TZ in .env if needed

# Start with Docker Compose
docker compose up -d
```

### Step 2: Verify the service is running

```bash
# Backend API health check
curl http://localhost:3018/health

# Check that containers are running
docker compose ps
```

### Step 3: Copy the SSH key from Jenkins Agent

The Jenkins pipeline connects to the DockerScan server via SSH to run the trigger script:

```bash
# On the Jenkins agent server, as the jenkins user
su - jenkins

# Copy the SSH key to the DockerScan server
ssh-copy-id [DOCKERSCAN_SSH_USER]@[DOCKERSCAN-IP]

# Perform the first connection (required for known hosts)
ssh [DOCKERSCAN_SSH_USER]@[DOCKERSCAN-IP]
exit

# Test passwordless connection
ssh [DOCKERSCAN_SSH_USER]@[DOCKERSCAN-IP] "whoami"
```

> **Warning:** If you skip the first connection step, the Jenkins pipeline will fail with "Host key verification failed".

### Step 4: Request network permissions

The following port access is required for DockerScan:

```
JENKINS_AGENT_IP > DOCKERSCAN_IP   PORT 22    (SSH — trigger script execution)
JENKINS_AGENT_IP > DOCKERSCAN_IP   PORT 3018  (Backend API — quality gate query)
```

### Step 5: Update globalConfig.groovy

```groovy
// ── DockerScan ─────────────────────────────────────────────────────────
// Repo: https://github.com/murat-akpinar/DockerScan
DOCKERSCAN_HOST         : 'DOCKERSCAN_IP',                    // DockerScan server IP
DOCKERSCAN_SSH_USER     : 'your-user',                        // SSH user
DOCKERSCAN_SCRIPT_PATH  : '/app/DockerScan/trigger-nexus.sh', // Trigger script path
DOCKERSCAN_BACKEND_PORT : '3018',                             // Backend API port
```

---

## 6. Jenkins Credentials Setup

The following credentials must be created in Jenkins for the pipeline to access Nexus and Harbor.

**Manage Jenkins** → **Credentials** → **System** → **Global credentials** → **Add Credentials**

| ID | Type | Used For |
|----|------|----------|
| `Nexus_Credentials` | Username with password | Docker image push/pull (Nexus) |
| `Harbor_Credentials` | Username with password | Docker image push/pull (Harbor) |

**For each credential:**
1. **Kind**: `Username with password`
2. **Scope**: `Global`
3. **Username**: Registry username
4. **Password**: Registry password
5. **ID**: `Nexus_Credentials` or `Harbor_Credentials` (exactly as written — case-sensitive)
6. Click **Save**

---

## Setup Checklist

Use the checklist below to ensure all steps are completed:

- [ ] Network permissions requested and granted
- [ ] Docker `daemon.json` edited and `insecure-registries` added
- [ ] Docker service restarted and settings applied
- [ ] Logged in to Nexus registry and tested
- [ ] SSH key copied from Jenkins agent to application server
- [ ] First SSH connection made (known hosts)
- [ ] Passwordless SSH connection tested
- [ ] `Nexus_Credentials` credential created in Jenkins
- [ ] `Harbor_Credentials` credential created in Jenkins
- [ ] DockerScan installed on the server and service is running
- [ ] SSH key copied from Jenkins agent to DockerScan server
- [ ] First SSH connection to DockerScan server made (known hosts)
- [ ] DockerScan settings updated in `globalConfig.groovy`

---

## Next Step

Once all preparation steps are complete, you can proceed to set up the CI/CD pipeline:

1. Create a `Jenkinsfile`
2. Create a `services.yml` file
3. Create a pipeline job in Jenkins

For more details, see [`02-pipeline-setup.md`](./02-pipeline-setup.md) and [`USAGE_GUIDE.md`](./USAGE_GUIDE.md).
