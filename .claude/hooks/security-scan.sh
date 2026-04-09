#!/usr/bin/env bash
# Security pre-scan for Write/Edit operations.
# Reads PreToolUse JSON from stdin, warns on detected secrets.
#
# Exit codes:
#   0 — OK or warnings only (non-blocking)
#   2 — Block this tool call (change exit 0 at bottom to exit 2 to enable)

set -euo pipefail

INPUT=$(cat)

# Extract content from Write (content field) or Edit (new_string field)
CONTENT=$(echo "$INPUT" | python3 -c '
import sys, json
try:
    data = json.loads(sys.stdin.read())
    tool_input = data.get("tool_input", {})
    content = tool_input.get("content", "") or ""
    content += tool_input.get("new_string", "") or ""
    print(content)
except Exception:
    print("")
') || CONTENT=""

if [ -z "$CONTENT" ]; then
    exit 0
fi

WARNINGS=()

# Hardcoded passwords/secrets (value assigned in quotes, not from env/function)
# Skip lines where the value is an env-var reference (${...}) or a placeholder like "changeme"
if echo "$CONTENT" | grep -iE \
    '(password|passwd|secret|api_key|apikey|access_token|auth_token|private_key)\s*[=:]\s*["'"'"'][^"'"'"']{6,}' \
    | grep -qvE '(\$\{|\bchangeme\b|\bsecret\b|\bpassword\b|\bexample\b|\btest\b|\bplaceholder\b)'; then
    WARNINGS+=("Potential hardcoded secret (password/key/token)")
fi

# AWS access key IDs
if echo "$CONTENT" | grep -qE 'AKIA[0-9A-Z]{16}'; then
    WARNINGS+=("AWS access key ID pattern detected (AKIA...)")
fi

# Private key PEM headers
if echo "$CONTENT" | grep -q "BEGIN.*PRIVATE KEY"; then
    WARNINGS+=("Private key content detected — store in a file reference instead")
fi

# High-entropy value assigned to secret-sounding variable name
if echo "$CONTENT" | grep -qiE \
    '(SECRET|TOKEN|CREDENTIAL)\s*=\s*[A-Za-z0-9/+]{32,}'; then
    WARNINGS+=("High-entropy value assigned to secret-sounding variable")
fi

if [ ${#WARNINGS[@]} -gt 0 ]; then
    echo "SECURITY WARNING — potential issue in content being written:"
    for w in "${WARNINGS[@]}"; do
        echo "  * $w"
    done
    echo ""
    echo "If intentional (e.g. test fixture), proceed. To make this blocking,"
    echo "change 'exit 0' to 'exit 2' at the bottom of security-scan.sh."
fi

# Non-blocking by default. Change to exit 2 to block.
exit 0
