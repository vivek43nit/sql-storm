# Security Policy

## Supported Versions

| Version | Supported |
|---------|-----------|
| latest (master) | ✅ |

## Reporting a Vulnerability

Please **do not** open a public GitHub issue for security vulnerabilities.

Email **vivek43nit@gmail.com** with:
- Description of the vulnerability
- Steps to reproduce
- Potential impact

You can expect an acknowledgement within 48 hours and a fix or mitigation plan within 7 days.

## Scope

- SQL injection via user-supplied query input
- Authentication bypass
- Insecure direct object references (accessing another user's connections)
- XSS in rendered database values

## Out of Scope

- Attacks requiring physical access to the server
- Vulnerabilities in dependencies not yet reported upstream
- Issues in example / sample configuration files
