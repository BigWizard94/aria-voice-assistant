# Setting Up APK Signing for Aria

This guide explains how to set up cryptographic signing for release APKs,
matching the CI/CD pipeline in `.github/workflows/build.yml`.

---

## Step 1: Generate a Keystore (One-time setup)

```bash
keytool -genkey -v \
  -keystore aria-release.keystore \
  -alias aria-key \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

You'll be prompted for:
- Keystore password (save this securely!)
- Key alias: `aria-key`
- Key password (can be same as keystore password)
- Your name, organization, location

**⚠️ NEVER commit the `.keystore` file to Git. It's in `.gitignore`.**

---

## Step 2: Encode Keystore for GitHub Secrets

```bash
# On Linux/Mac:
base64 -w 0 aria-release.keystore > keystore_base64.txt

# On Windows (PowerShell):
[Convert]::ToBase64String([IO.File]::ReadAllBytes("aria-release.keystore")) > keystore_base64.txt
```

---

## Step 3: Add GitHub Secrets

Go to your GitHub repo → **Settings → Secrets and variables → Actions → New repository secret**

Add these 4 secrets:

| Secret Name | Value |
|---|---|
| `KEYSTORE_BASE64` | Contents of `keystore_base64.txt` |
| `KEYSTORE_PASSWORD` | Your keystore password |
| `KEY_ALIAS` | `aria-key` |
| `KEY_PASSWORD` | Your key password |

---

## Step 4: Create a Release

```bash
# Tag a release — CI/CD will automatically build and sign
git tag v1.0.0
git push origin v1.0.0
```

The GitHub Actions pipeline will:
1. Build the signed release APK
2. Create a GitHub Release automatically
3. Upload the APK as a release asset
4. Users can download directly from the Releases page

---

## Local Signing (Manual)

To sign locally without CI/CD:

```bash
./gradlew assembleRelease \
  -Pandroid.injected.signing.store.file=/path/to/aria-release.keystore \
  -Pandroid.injected.signing.store.password=YOUR_PASSWORD \
  -Pandroid.injected.signing.key.alias=aria-key \
  -Pandroid.injected.signing.key.password=YOUR_KEY_PASSWORD
```

---

## Verify APK Signature

```bash
# Verify the APK is properly signed
apksigner verify --verbose app/build/outputs/apk/release/Aria-v1.0.0.apk

# View certificate details
keytool -printcert -jarfile app/build/outputs/apk/release/Aria-v1.0.0.apk
```