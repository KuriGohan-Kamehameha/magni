# Security Policy

## Supported versions

Magni is a prototype project. Only the latest code on the `main` branch receives attention for security issues.

| Version | Supported |
|---------|-----------|
| `main` (latest) | ✅ |
| Older tags / forks | ❌ |

## Reporting a vulnerability

**Please do not open a public GitHub issue for security vulnerabilities.**

Instead, report security issues privately by emailing the maintainers or using [GitHub's private vulnerability reporting](https://github.com/KuriGohan-Kamehameha/magni/security/advisories/new).

When reporting, please include:

1. A description of the vulnerability and its potential impact.
2. Steps to reproduce (device, Android version, reproduction scenario).
3. Any proof-of-concept code or screenshots if applicable.

We will acknowledge your report within **7 days** and aim to publish a fix or advisory within **90 days**, depending on complexity. We appreciate responsible disclosure.

## Scope

Because Magni is a prototype browser, the following are known limitations and are **out of scope** for security reports:

- Deprecation warnings from `WebView.capturePicture()` (no practical exploit surface).
- Privacy features that are not audited to production standards (the README states this explicitly).
- Issues only reproducible on rooted or modified devices.

We will still consider reports outside this list on a case-by-case basis.
