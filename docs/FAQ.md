# Frequently Asked Questions (FAQ)

> Turkce dokumantasyon icin: **[SSS.md](./SSS.md)**

Common questions and answers about using the Jenkins Shared Library.

## Table of Contents

- [Credentials and Environment Variables](#credentials-and-environment-variables)
- [.env File Usage](#env-file-usage)
- [services.yml Usage](#servicesyml-usage)
- [extra_files Usage](#extra_files-usage)
- [Docker Compose and Version Tags](#docker-compose-and-version-tags)
- [Deploy Process](#deploy-process)
- [Troubleshooting](#troubleshooting)

---

## Credentials and Environment Variables

### Q1: How do I define credentials in the environment block?

**Answer:** Use the `credentials()` function in the `environment` block. Masking is handled automatically.

```groovy
environment {
    VERSION = "${params.VERSION_TAG ?: 'test-v1.0'}"
    APP = 'my-app'

    // Secret text credentials (masked in logs)
    PSQL_HOST = credentials('psql_host')
    PSQL_PASS = credentials('psql_pass')
    JWT_SECRET_KEY = credentials('jwt_secret_key')
}
```

**Important:**
- Defining them in the `environment` block is all you need
- The `deployService()` function automatically writes them to the `.env` file
- They are masked in Jenkins logs

---

### Q2: Do I need to manually write credentials to the .env file?

**Answer:** No! The `deployService()` function automatically writes all credentials from the `environment` block to the `.env` file.

**How it works:**
1. You define credentials in the `environment` block
2. The `collectCredentials()` function automatically collects them
3. When `deployService()` is called, they are written to the `.env` file automatically
4. System variables (`JENKINS_*`, `BUILD_*`, etc.) are filtered out automatically

No separate writing step is needed in the Jenkinsfile!

---

### Q3: Can I use any name for credentials?

**Answer:** Yes, but they must not start with system variable prefixes.

**✅ Works:**
```groovy
environment {
    MY_SECRET = credentials('some-secret')      // → MY_SECRET=... in .env
    CUSTOM_TOKEN = credentials('custom-token')  // → CUSTOM_TOKEN=... in .env
    PSQL_HOST = credentials('psql_host')        // → PSQL_HOST=... in .env
}
```

**❌ Does not work (excluded):**
```groovy
environment {
    JENKINS_SECRET = credentials('secret')  // NOT written to .env!
    BUILD_TOKEN = credentials('token')      // NOT written to .env!
    DO_FLAG = credentials('flag')           // NOT written to .env!
}
```

**Excluded prefixes:**
- `JENKINS_*`, `BUILD_*`, `DO_*`, `TAG_EXISTS_*`
- `GIT_*`, `JOB_*`, `NODE_*`, `HUDSON_*`

---

## .env File Usage

### Q4: Is it required to specify env_file in services.yml?

**Answer:** No, `env_file` is **optional**.

**Scenario 1: env_file not specified**
```yaml
services:
  - name: backend
    # env_file not specified
```
→ If credentials exist in the `environment` block, a `.env` file is created automatically.

**Scenario 2: env_file specified**
```yaml
services:
  - name: backend
    env_file: .env.prod  # or .env.dev, backend/.env, etc.
```
→ The specified file is read, `VERSION` + credentials are appended/updated, and it is copied to the server as `.env`.

---

### Q5: Can I use different environment files like .env.prod or .env.dev?

**Answer:** Yes! You can specify files like `.env.prod` or `.env.dev` in `services.yml`.

**Example:**
```yaml
services:
  - name: backend
    env_file: .env.prod  # For production
```

**How it works:**
1. `.env.prod` is read from the repo
2. `VERSION=${version}` is added/updated
3. Credentials from the environment are added/updated
4. Copied to the server as `/app/${APP}/.env`

**Result:** Stored as `.env.prod` in the repo, used as `.env` on the server (for Docker Compose).

---

### Q6: Can I use different .env files for multiple services?

**Answer:** Technically possible, but **not recommended**. A single central `.env` file is used for all services on the server.

**Example:**
```yaml
services:
  - name: backend
    env_file: backend/.env  # Used for the first service
  - name: frontend
    env_file: frontend/.env  # Ignored even if specified
```

**How it works:**
- When backend is deployed, `backend/.env` is copied from Git and written as `/app/${APP}/.env`
- When frontend is deployed, the existing `.env` on the server is read and only `VERSION` is updated
- The frontend's `env_file` is ignored (backend's `.env` is preserved)

**Recommendation:** Using a single central `.env` file is simpler and easier to manage.

---

### Q7: If I manually run `docker compose up -d` on the server, is the .env file read?

**Answer:** Yes! Docker Compose reads the `.env` file automatically.

**On the server:**
```bash
cd /app/my-app
docker compose up -d
```
→ Docker Compose automatically reads the `.env` file in the same directory.

**Note:** The `--env-file .env` parameter is unnecessary (but harmless). Docker Compose reads `.env` by default.

---

### Q8: Is the .env file content preserved? Will it be overwritten if it exists in the repo?

**Answer:** Content is preserved, not overwritten! `VERSION` and credentials are added/updated in the existing content.

**Example:**

**`.env.prod` in the repo:**
```bash
DB_HOST=prod-db.example.com
DB_PORT=5432
API_URL=https://api.prod.com
```

**`.env` on the server after the first service deploy:**
```bash
# Original content preserved
DB_HOST=prod-db.example.com
DB_PORT=5432
API_URL=https://api.prod.com

# Automatically added
VERSION=test-v1.0
PSQL_HOST=...
PSQL_PASS=...
```

**When other services are deployed:**
- The existing `.env` on the server is read (repo content preserved)
- Only `VERSION` is updated (other variables preserved)

---

## services.yml Usage

### Q9: Do I need to specify env_file for every service?

**Answer:** No, you don't need to specify `env_file` for every service. Specifying it **only for the first service** is enough.

**Single central .env:**
```yaml
services:
  - name: backend
    env_file: .env  # Specified for the first service
  - name: frontend
    # env_file not specified → uses the .env created by backend
```

**How it works:**
- Backend deploy: `.env` from Git is copied, written as `/app/${APP}/.env`
- Frontend deploy: Existing `.env` on the server is read, only `VERSION` is updated

---

### Q10: What happens if I don't specify env_file in services.yml?

**Answer:**
- **For the first service:** If credentials exist in the `environment` block → `.env` is created automatically. If only `VERSION` exists → `.env` is created with only `VERSION`
- **For other services:** The existing `.env` on the server is read, only `VERSION` is updated

**Example:**
```groovy
environment {
    VERSION = 'test-v1.0'
    APP = 'my-app'
    PSQL_HOST = credentials('psql_host')  // Credential present
}
```

```yaml
services:
  - name: backend
    # env_file not specified
```

→ When the first service (backend) is deployed, `.env` is created automatically with `VERSION` + `PSQL_HOST`. Other services use this `.env`.

---

## extra_files Usage

### Q11: Is just writing the filename in extra_files sufficient?

**Answer:** Yes! You can just write the file/directory path without specifying `dest`. It is automatically copied to `/app/<APP>/<filename>`.

**Example:**
```yaml
extra_files:
  - default.conf              # → /app/myapp/default.conf
  - nginx/nginx.conf          # → /app/myapp/nginx.conf (using basename)
  - backend                   # → /app/myapp/backend/ (directory)
```

**Map format (for a custom location):**
```yaml
extra_files:
  - src: webrtc/config.env
    dest: /app/myapp/config.env  # Custom location
```

---

### Q12: Do I need to specify whether an entry in extra_files is a file or directory?

**Answer:** No! The system automatically detects whether the source is a file or directory and copies accordingly.

**How it works:**
- If a file: copied with normal `scp`
- If a directory: copied with `scp -r`
- If the destination path on the server has a conflicting type (file/directory), it is cleaned up automatically

**Example:**
```yaml
extra_files:
  - default.conf    # File → /app/myapp/default.conf
  - config/         # Directory → /app/myapp/config/
  - backend         # Directory → /app/myapp/backend/
```

**Important:** If `/app/myapp/default.conf` exists as a directory on the server and you're copying a file, the system automatically removes the directory and copies the file (prevents Docker mount errors).

---

### Q13: When does the extra_files copy happen?

**Answer:** The `extra_files` copy happens **before** the `.env` file copy. This ensures files are ready if the Docker Compose file depends on them.

**Order of operations:**
1. Docker image is pulled
2. **extra_files are copied** (before `.env`)
3. `.env` file is created/copied
4. Docker Compose file is copied
5. `docker compose up -d` is run

---

### Q14: extra_files are not being copied — what should I do?

**Check:**
1. Is `extra_files` defined in `services.yml`?
2. Do you see the `extra file/directory will be copied...` message in logs?
3. Does the source file/directory exist in the project?
4. Are there any error messages in the logs?

**Example log:**
```
3 extra file(s)/directory(ies) will be copied...
Source type: file (default.conf)
Copied: default.conf → /app/myapp/default.conf
```

If the log shows `extraFiles is empty or not defined`, then `extra_files` is not defined in `services.yml`.

---

### Q15: Can I define different extra_files for multiple services?

**Answer:** Yes! Each service can have its own `extra_files` list.

**Example:**
```yaml
services:
  - name: backend
    path: backend
    dockerfile: Dockerfile
    image_name: myapp_backend
    extra_files:
      - backend
      - frontend
      - nginx

  - name: frontend
    path: frontend
    dockerfile: Dockerfile
    image_name: myapp_frontend
    # no extra_files

  - name: nginx
    path: nginx
    dockerfile: Dockerfile
    image_name: myapp_nginx
    extra_files:
      - default.conf
      - nginx/nginx.conf
```

Each service copies its own `extra_files` list when deployed.

---

## Docker Compose and Version Tags

### Q16: How do I use the version tag in the Docker Compose file?

**Answer:** Use the `${VERSION}` variable in the Docker Compose file. It is automatically read from the `.env` file.

**docker-compose.yml:**
```yaml
services:
  backend:
    image: myapp_backend:${VERSION}  # Automatically read from .env
    environment:
      - VERSION=${VERSION}
```

**How it works:**
1. Jenkins → `VERSION_TAG` parameter is received
2. `VERSION = "${params.VERSION_TAG}"` is set in the `environment` block
3. `deployService()` → writes `VERSION=test-v1.0` to the `.env` file
4. Docker Compose → reads `.env` file automatically
5. `${VERSION}` is resolved from the `.env` file

---

### Q17: Do I need to modify the Docker Compose file at runtime?

**Answer:** No! There is no need to modify the Docker Compose file. Version management is handled via the `.env` file.

**❌ Wrong approach:**
```groovy
sh "sed -i 's/:latest/:${env.VERSION}/g' docker-compose.yml"  // NOT RECOMMENDED
```

**✅ Correct approach (current system):**
```yaml
# docker-compose.yml (in the repo)
services:
  backend:
    image: myapp_backend:${VERSION}  # Read from .env
```

---

## Deploy Process

### Q18: In which file does deployService() write credentials to .env?

**Answer:** In `vars/deployService.groovy`.

**Process flow:**
1. `collectCredentials()` collects credentials from the environment block
2. Inside `deployService()`:
   - If `envFile` is set, the file from Git is read; otherwise, the existing `.env` on the server is read
   - `VERSION` is added/updated
   - `extraEnvVars` (credentials) are added/updated
   - Copied to the server as `/app/${appName}/.env`

---

### Q19: How is the deploy path determined?

**Answer:** The deploy path is automatically set to `/app/${APP}/`.

**Example:**
```groovy
environment {
    APP = 'my-project'
}
```
→ Deploy path: `/app/my-project/`

**Note:** The `deployService()` function sets the path automatically. You don't need to specify it per project.

---

### Q20: Where is the .env file stored after deploy?

**Answer:** On the server at `/app/${APP}/.env`.

**Examples:**
- `APP = 'my-project'` → `/app/my-project/.env`
- `APP = 'my-app'` → `/app/my-app/.env`

Docker Compose reads this file automatically (since it's in the same directory).

---

## Troubleshooting

### Q21: VERSION is not appearing in the .env file

**Check:**
1. Is `VERSION` defined in the `environment` block?
2. Is the `version` parameter passed in the `deployService()` call?
3. Was the `.env` file created in the logs?

**Solution:**
```groovy
environment {
    VERSION = "${params.VERSION_TAG ?: 'test-v1.0'}"
}
```

---

### Q22: Credentials are not being written to the .env file

**Check:**
1. Are credentials defined in the `environment` block?
2. Does the credential name start with a system variable prefix? (`JENKINS_`, `BUILD_`, etc.)
3. Is `collectCredentials()` being called?
4. Is `extraEnvVars` passed to `deployService()`?

**Solution:**
```groovy
// ❌ Wrong
JENKINS_SECRET = credentials('secret')  // Excluded

// ✅ Correct
MY_SECRET = credentials('secret')  // Written

// In the Deploy stage
def credentialsMap = collectCredentials()
deployService([
    // ...
    extraEnvVars: credentialsMap
])
```

---

### Q23: Docker Compose is not reading the .env file

**Check:**
1. Is the `.env` file in the `/app/${APP}/` directory?
2. Is the Docker Compose file in the same directory?
3. Does the `.env` file contain a `VERSION=...` line?

**Manual test:**
```bash
cd /app/my-app
cat .env  # Check the content
docker compose config  # Shows Docker Compose variable resolution
```

---

### Q24: Getting an error that extra_files are not being copied

**Check:**
1. Is `extra_files` defined in `services.yml`?
2. Does the source file/directory exist in the project?
3. Are there any error messages in the logs?
4. Does the destination path already exist on the server and is it the correct type?

**Example log check:**
```
1 extra file(s)/directory(ies) will be copied...
Source type: file (default.conf)
Warning: /app/myapp/default.conf exists as a directory on server, cleaning up to copy file...
Copied: default.conf → /app/myapp/default.conf
```

If no message appears in the logs, `extra_files` is not defined in `services.yml`.

---

### Q25: "not a directory" or "Are you trying to mount a directory onto a file" error

**Problem:** The destination path on the server has the wrong type (file/directory) and Docker produces a mount error.

**Solution:** This error is now fixed automatically. During `extra_files` copying:

1. Source type is checked (file/directory)
2. Destination path type on the server is checked
3. Type mismatch is cleaned up automatically
4. Copied as the correct type

If you're still getting the error:
- Check for `Warning: ... cleaning up...` in the logs
- Manually check the destination path on the server:
  ```bash
  ssh system@[IP] "ls -la /app/myapp/default.conf"
  ```

---

## More Information

- **Function References:** [README.md](../README.md)
- **Detailed Usage Guide:** [USAGE_GUIDE.md](./USAGE_GUIDE.md)
- **Real Examples:** [Example/](../Example/)
- **CI/CD Pre-Setup:** [01-cicd-preparation.md](./01-cicd-preparation.md)

---

## Tip

If you're having issues, check the logs first. Jenkins pipeline logs contain detailed messages for each step:

- `[INFO]` — extra_files operation
- `[SOURCE]` — Source type detection
- `[WARN]` — Warning messages
- `[OK]` — Successful operations
- `[ERROR]` — Error messages
