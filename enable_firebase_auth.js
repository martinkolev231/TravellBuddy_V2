const fs = require('fs');
const os = require('os');
const path = require('path');

const OUT = '/Users/martinkolev/AndroidStudioProjects/TravellBuddy/app/firebase_auth_setup.log';
fs.writeFileSync(OUT, 'SCRIPT_STARTED\n');

async function main() {
  try {
    const cfgPath = path.join(os.homedir(), '.config', 'configstore', 'firebase-tools.json');
    const cfg = JSON.parse(fs.readFileSync(cfgPath, 'utf8'));
    const rt = cfg.tokens.refresh_token;
    const cid = '563584335869-fgrhgmd47bqnekij5i8b5pr03ho849e6.apps.googleusercontent.com';
    const cs = 'j9iVZfS8kkCEFUPaAeJV0sAi';

    // Get access token
    const tokenRes = await fetch('https://oauth2.googleapis.com/token', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: `grant_type=refresh_token&client_id=${cid}&client_secret=${cs}&refresh_token=${encodeURIComponent(rt)}`
    });
    const tokenData = await tokenRes.json();
    if (!tokenData.access_token) {
      fs.writeFileSync(OUT, 'TOKEN_ERROR: ' + JSON.stringify(tokenData));
      process.exit(1);
    }
    const token = tokenData.access_token;
    const projectId = 'travellbuddy-faf0e';
    let log = 'TOKEN: OK\n';

    // Enable Identity Toolkit API
    const enableRes = await fetch(
      `https://serviceusage.googleapis.com/v1/projects/${projectId}/services/identitytoolkit.googleapis.com:enable`,
      { method: 'POST', headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' }, body: '{}' }
    );
    const enableData = await enableRes.json();
    log += 'ENABLE_API: ' + JSON.stringify(enableData).substring(0, 150) + '\n';

    // Patch config to enable Email/Password
    const patchRes = await fetch(
      `https://identitytoolkit.googleapis.com/admin/v2/projects/${projectId}/config?updateMask=signIn.email`,
      {
        method: 'PATCH',
        headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({ signIn: { email: { enabled: true, passwordRequired: true } } })
      }
    );
    const patchData = await patchRes.json();
    log += 'PATCH: ' + JSON.stringify(patchData.signIn || patchData.error || patchData).substring(0, 300) + '\n';

    const success = patchData.signIn && patchData.signIn.email && patchData.signIn.email.enabled;
    log += 'RESULT: ' + (success ? 'SUCCESS' : 'FAILED') + '\n';

    fs.writeFileSync(OUT, log);
    process.exit(success ? 0 : 1);
  } catch (e) {
    fs.writeFileSync(OUT, 'CATCH: ' + e.message + '\n' + e.stack);
    process.exit(1);
  }
}

main().then(() => {
  console.log('Done. Check ' + OUT);
}).catch(e => {
  fs.writeFileSync(OUT, 'UNCAUGHT: ' + e.message + '\n' + e.stack);
  console.error(e);
  process.exit(1);
});




