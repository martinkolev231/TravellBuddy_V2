#!/bin/bash
# Enable Firebase Auth Email/Password via Identity Toolkit REST API
cd /Users/martinkolev/AndroidStudioProjects/TravellBuddy
export PATH="$(pwd)/.tools/node-v25.8.0-darwin-arm64/bin:$PATH"

# Get fresh access token
node -e "
const cfg = require(require('os').homedir() + '/.config/configstore/firebase-tools.json');
const rt = cfg.tokens.refresh_token;
const clientId = '563584335869-fgrhgmd47bqnekij5i8b5pr03ho849e6.apps.googleusercontent.com';
const clientSecret = 'j9iVZfS8kkCEFUPaAeJV0sAi';
const body = 'grant_type=refresh_token&client_id=' + clientId + '&client_secret=' + clientSecret + '&refresh_token=' + encodeURIComponent(rt);
fetch('https://oauth2.googleapis.com/token', {method:'POST', headers:{'Content-Type':'application/x-www-form-urlencoded'}, body})
  .then(r => r.json())
  .then(d => {
    if (!d.access_token) { console.log('TOKEN_ERROR:', JSON.stringify(d)); process.exit(1); }
    const token = d.access_token;
    const projectId = 'travellbuddy-faf0e';

    // Step 1: Enable Identity Toolkit API
    fetch('https://serviceusage.googleapis.com/v1/projects/' + projectId + '/services/identitytoolkit.googleapis.com:enable', {
      method: 'POST', headers: { 'Authorization': 'Bearer ' + token, 'Content-Type': 'application/json' }, body: '{}'
    }).then(r => r.json()).then(r1 => {
      console.log('ENABLE_IDTK_API:', r1.done !== undefined ? 'OK' : JSON.stringify(r1).substring(0, 200));

      // Step 2: Get current auth config
      return fetch('https://identitytoolkit.googleapis.com/admin/v2/projects/' + projectId + '/config', {
        headers: { 'Authorization': 'Bearer ' + token }
      });
    }).then(r => r.json()).then(cfg => {
      console.log('CURRENT_SIGN_IN_CONFIG:', JSON.stringify(cfg.signIn || {}).substring(0, 300));

      // Step 3: Enable Email/Password sign-in
      const patchBody = JSON.stringify({
        signIn: {
          email: {
            enabled: true,
            passwordRequired: true
          }
        }
      });
      return fetch('https://identitytoolkit.googleapis.com/admin/v2/projects/' + projectId + '/config?updateMask=signIn.email', {
        method: 'PATCH',
        headers: { 'Authorization': 'Bearer ' + token, 'Content-Type': 'application/json' },
        body: patchBody
      });
    }).then(r => r.json()).then(result => {
      console.log('PATCH_RESULT:', JSON.stringify(result.signIn || result.error || result).substring(0, 300));
      if (result.signIn && result.signIn.email && result.signIn.email.enabled) {
        console.log('SUCCESS: Email/Password auth is now ENABLED');
      } else if (result.error) {
        console.log('ERROR:', result.error.message);
      }
    });
  });
"

