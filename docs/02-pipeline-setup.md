# CI/CD Pipeline Setup (Step 2)

> Turkce dokumantasyon icin: **[02-pipeline-kurulumu.md](./02-pipeline-kurulumu.md)**

This document explains how to prepare the required files (Jenkinsfile and services.yml) to create a Jenkins pipeline after the prerequisites have been completed.

---

## 1. Creating the Jenkinsfile

The `Jenkinsfile` defines your pipeline steps. A full example is available in this repo:

- `Example/Jenkins_example.groovy`

### Recommended approach

1. Copy or create a `Jenkinsfile` in the project root.
2. Fill in `vars/globalConfig.groovy` with your environment details (Nexus, Harbor, Trivy, SonarQube addresses).
3. Add project credentials to the `environment` block (they are automatically written to the `.env` file).

### Pipeline Parameters

```groovy
parameters {
    choice(name: 'ENVIRONMENT', choices: ['test', 'preprod', 'prod'], description: 'Select deploy environment')
    booleanParam(name: 'FORCE_REBUILD', defaultValue: false, description: 'Force rebuild all images')
    booleanParam(name: 'USE_EXISTING_IMAGE', defaultValue: false, description: 'Skip build/push if tag exists, use existing image')
    string(name: 'VERSION_TAG', defaultValue: 'test-v1.0', description: 'Image tag to deploy')
}
```

### Defining Credentials

```groovy
environment {
    VERSION = "${params.VERSION_TAG ?: 'test-v1.0'}"

    // Credentials — automatically written to .env file
    PSQL_HOST = credentials('psql_myproject_host')
    PSQL_PASS = credentials('psql_myproject_pass')
}
```

> Note: See `USAGE_GUIDE.md` for detailed usage.

---

## 2. Creating the services.yml File

`services.yml` defines which services the pipeline processes and with which parameters. Example:

- `Example/services_example.yml`

### Full structure

```yaml
app_name: my-project                   # Application name — deploy path: /app/my-project/
recipients: 'Team_1, Team_2'           # Email recipients

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
  - name: backend
    path: backend/
    dockerfile: Dockerfile
    image_name: myapp_backend
    env_file: backend/.env
    extra_files:
      - { src: "default.conf", dest: "/app/my-project/default.conf" }
    test_command: "npm test"

  - name: frontend
    path: frontend/
    dockerfile: Dockerfile
    image_name: myapp_frontend
    env_file: frontend/.env
```

### Important notes

- `app_name` is required — the pipeline reads `env.APP` from this value
- `deploy_targets` under `environments` must match the `ENVIRONMENT` parameter selection in the pipeline
- `image_name` must be consistent with the image name in the Nexus/Harbor registry

---

## 3. Creating the Pipeline Job in Jenkins

1. Jenkins UI → **New Item** → **Pipeline**
2. Enter your Git repo URL as the SCM; add credentials if needed
3. Script Path: `Jenkinsfile` (if in the repo root)
4. Save and run

For the first run:
- Set `FORCE_REBUILD: true` (forces the build if the tag doesn't exist)
- Select `ENVIRONMENT` (must match the environment in services.yml)
- Make sure credentials and SSH access are ready

---

## Checklist

- [ ] `Jenkinsfile` created in the project root
- [ ] `vars/globalConfig.groovy` updated for your environment
- [ ] `services.yml` created — `app_name`, `environments`, `services` fields populated
- [ ] Jenkins job created and SCM connection verified
- [ ] Jenkins credentials defined (`Nexus_Credentials`, etc.)
- [ ] First build completed successfully with `FORCE_REBUILD: true`
