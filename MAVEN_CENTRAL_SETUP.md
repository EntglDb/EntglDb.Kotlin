# EntglDb Maven Central Setup Guide

## Step 1: Generate GPG Keys (5 minutes)

Run these commands:

```bash
# Generate key pair (use RSA 4096 for maximum compatibility)
gpg --full-generate-key
```

When prompted:
- Key type: **RSA and RSA**
- Key size: **4096**
- Expiration: **0** (does not expire) or **2y** (2 years)
- Real name: **EntglDb Release**
- Email: **entgldb@users.noreply.github.com**
- Passphrase: **(remember this!)**

```bash
# List your keys to get the KEY_ID
gpg --list-secret-keys --keyid-format LONG

# Output will look like:
# sec   rsa4096/ABCD1234EFGH5678 2024-01-17
# The KEY_ID is: ABCD1234EFGH5678

# Upload public key to keyserver
gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID

# Also upload to other keyservers for redundancy
gpg --keyserver keys.openpgp.org --send-keys YOUR_KEY_ID
gpg --keyserver pgp.mit.edu --send-keys YOUR_KEY_ID
```

## Step 2: Export Keys for CI/CD

```bash
# Export private key in base64 (for GitHub Secrets)
gpg --export-secret-keys YOUR_KEY_ID | base64 > signing-key.txt

# This creates signing-key.txt - KEEP THIS SECURE!
```

## Step 3: Create Sonatype Account (15 minutes setup, 2 days approval)

1. **Create JIRA Account:**
   - Go to: https://issues.sonatype.org/secure/Signup!default.jspa
   - Username: `entgldb` (or your preference)
   - Email: Use your GitHub email

2. **Create New Project Ticket:**
   - Go to: https://issues.sonatype.org/secure/CreateIssue.jspa?issuetype=21&pid=10134
   - Summary: `Request publishing rights for io.github.entgldb`
   - Group ID: `io.github.entgldb`
   - Project URL: `https://github.com/EntglDb/EntglDb.Kotlin`
   - SCM URL: `https://github.com/EntglDb/EntglDb.Kotlin.git`
   - Description:
     ```
     I am the maintainer of EntglDb.Kotlin, a distributed peer-to-peer database library for Android.
     This is the Kotlin/Android port of EntglDb.Net (https://github.com/EntglDb/EntglDb.Net).
     
     I would like to publish artifacts under the io.github.entgldb group ID.
     I have verified ownership by pushing code to the repository.
     ```

3. **Verify Ownership:**
   - Sonatype bot will comment asking you to verify ownership
   - They may ask you to:
     - Create a public GitHub repository: `EntglDb/OSSRH-XXXXX` (ticket number)
     - OR add a comment in your ticket confirming you own the GitHub org

4. **Wait for Approval:**
   - Usually takes 1-2 business days
   - You'll get an email when approved

## Step 4: Configure Local Secrets

Create or edit `~/.gradle/gradle.properties` (NOT in project, in your home directory):

```properties
signing.keyId=LAST_8_CHARS_OF_KEY_ID
signing.password=YOUR_GPG_PASSPHRASE
signing.secretKeyRingFile=/path/to/.gnupg/secring.gpg

ossrhUsername=YOUR_SONATYPE_USERNAME
ossrhPassword=YOUR_SONATYPE_PASSWORD
```

**For Windows:**
```properties
signing.keyId=ABCD5678
signing.password=yourpassphrase
signing.secretKeyRingFile=C:\\Users\\YourUser\\.gnupg\\secring.gpg
ossrhUsername=entgldb
ossrhPassword=yoursonatypepassword
```

## Step 5: Test Local Publishing

```bash
# Build all modules
./gradlew build

# Publish to local Maven repository (test)
./gradlew publishToMavenLocal

# Check artifacts in:
# Windows: C:\Users\<user>\.m2\repository\io\github\entgldb\
```

## Step 6: Setup GitHub Secrets

Once you have GPG keys and Sonatype account:

1. Go to: https://github.com/EntglDb/EntglDb.Kotlin/settings/secrets/actions

2. Add these secrets:
   - `OSSRH_USERNAME`: Your Sonatype username
   - `OSSRH_PASSWORD`: Your Sonatype password
   - `SIGNING_KEY`: Content of `signing-key.txt` (base64 private key)
   - `SIGNING_PASSWORD`: Your GPG passphrase

3. Keep `signing-key.txt` secure and then DELETE IT from your computer

## Step 7: First Release

After Sonatype approval:

```bash
# Create a tag
git tag v0.1.0-alpha
git push origin v0.1.0-alpha
```

GitHub Actions will:
1. Build the project
2. Run tests
3. Sign artifacts with GPG
4. Publish to Maven Central Staging
5. Create GitHub Release

## Step 8: Release on Sonatype (First Time Only)

For the first release, you need to manually promote:

1. Go to: https://s01.oss.sonatype.org/
2. Login with your Sonatype credentials
3. Click "Staging Repositories" on the left
4. Find your repository (io.github.entgldb-XXXX)
5. Select it and click "Close" (wait for validation)
6. Click "Release"

After the first release, you can configure auto-release in future deploys.

## Step 9: Verify Publication

After ~10 minutes:
- Check: https://s01.oss.sonatype.org/content/repositories/releases/io/github/entgldb/

After ~2 hours:
- Check: https://search.maven.org/search?q=g:io.github.entgldb

## Ongoing Releases

After initial setup:

```bash
# Bump version in gradle.properties
VERSION_NAME=0.2.0

# Commit, tag, push
git commit -am "Release 0.2.0"
git tag v0.2.0
git push origin main --tags
```

GitHub Actions handles everything automatically!

## Troubleshooting

**GPG "No secret key" error:**
```bash
# Export secring to legacy format
gpg --export-secret-keys > ~/.gnupg/secring.gpg
```

**Sonatype 401 Unauthorized:**
- Check username/password in gradle.properties
- Verify your token hasn't expired

**Signature verification failed:**
- Ensure all keyservers have your public key
- Wait 10-15 minutes after uploading keys

## Quick Commands Reference

```bash
# List GPG keys
gpg --list-keys

# Test sign a file
gpg --sign test.txt

# Publish to Maven Local (test)
./gradlew publishToMavenLocal

# Publish to Maven Central (after setup)
./gradlew publish

# Check what will be published
./gradlew publish --dry-run
```

---

**Current Status:** You have gradle.properties configured. Next steps:
1. Generate GPG keys (run the commands above)
2. Create Sonatype account and ticket
3. Wait for approval
4. Add secrets to GitHub
5. Make first release!
