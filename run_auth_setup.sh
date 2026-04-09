#!/bin/bash
# Run the Firebase auth enable script and capture all output
export PATH="/Users/martinkolev/AndroidStudioProjects/TravellBuddy/.tools/node-v25.8.0-darwin-arm64/bin:$PATH"
cd /Users/martinkolev/AndroidStudioProjects/TravellBuddy

# First verify node works
node -v > app/script_runner_log.txt 2>&1

# Run the auth enable script
node enable_firebase_auth.js >> app/script_runner_log.txt 2>&1
echo "NODE_EXIT=$?" >> app/script_runner_log.txt

# Check if the log was created
if [ -f "app/firebase_auth_setup.log" ]; then
  echo "AUTH_LOG_EXISTS=YES" >> app/script_runner_log.txt
  cat app/firebase_auth_setup.log >> app/script_runner_log.txt
else
  echo "AUTH_LOG_EXISTS=NO" >> app/script_runner_log.txt
fi

