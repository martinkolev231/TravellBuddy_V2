#!/bin/bash
# Step 1: Get access token from Firebase CLI stored refresh token
export PATH="/Users/martinkolev/AndroidStudioProjects/TravellBuddy/.tools/node-v25.8.0-darwin-arm64/bin:$PATH"
OUTDIR="/Users/martinkolev/AndroidStudioProjects/TravellBuddy/app"

RT=$(node -p "JSON.parse(require('fs').readFileSync(require('os').homedir()+'/.config/configstore/firebase-tools.json','utf8')).tokens.refresh_token")

echo "RT_LEN=${#RT}" > "$OUTDIR/auth_setup_log.txt"

# Step 2: Exchange refresh token for access token
AT=$(curl -s -X POST "https://oauth2.googleapis.com/token" \
  -d "grant_type=refresh_token&client_id=563584335869-fgrhgmd47bqnekij5i8b5pr03ho849e6.apps.googleusercontent.com&client_secret=j9iVZfS8kkCEFUPaAeJV0sAi&refresh_token=$RT" \
  | node -p "JSON.parse(require('fs').readFileSync('/dev/stdin','utf8')).access_token")

echo "AT_LEN=${#AT}" >> "$OUTDIR/auth_setup_log.txt"

# Step 3: Enable Identity Toolkit API
curl -s -X POST \
  "https://serviceusage.googleapis.com/v1/projects/travellbuddy-faf0e/services/identitytoolkit.googleapis.com:enable" \
  -H "Authorization: Bearer $AT" \
  -H "Content-Type: application/json" \
  -d '{}' > "$OUTDIR/enable_api_result.json" 2>&1

echo "ENABLE_API_DONE" >> "$OUTDIR/auth_setup_log.txt"

# Step 4: Enable Email/Password sign-in
curl -s -X PATCH \
  "https://identitytoolkit.googleapis.com/admin/v2/projects/travellbuddy-faf0e/config?updateMask=signIn.email" \
  -H "Authorization: Bearer $AT" \
  -H "Content-Type: application/json" \
  -d '{"signIn":{"email":{"enabled":true,"passwordRequired":true}}}' > "$OUTDIR/patch_auth_result.json" 2>&1

echo "PATCH_DONE" >> "$OUTDIR/auth_setup_log.txt"

# Step 5: Verify
curl -s \
  "https://identitytoolkit.googleapis.com/admin/v2/projects/travellbuddy-faf0e/config" \
  -H "Authorization: Bearer $AT" > "$OUTDIR/verify_auth_config.json" 2>&1

echo "VERIFY_DONE" >> "$OUTDIR/auth_setup_log.txt"
echo "ALL_DONE" >> "$OUTDIR/auth_setup_log.txt"

