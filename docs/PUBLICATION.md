# Public repository audit

The publication set contains source code, build scripts, tests, project templates, and license notices.

Local home-directory paths were removed from project documentation. The Will I Dream template's local audio reference, file hash, and associated audio clips were removed. Its visual scene configuration remains available.

Generated PDFs, HTML guides, device screenshots, debug logs, signing keys, application packages, dependency folders, and editor configuration are excluded from version control. Local copies of excluded files can remain on the development machine.

Copyright names and third-party source credits are retained as license attribution. Public upstream repository links are retained as provenance.

Run `node scripts/audit-public.cjs` to check tracked files for excluded artifacts, home-directory paths, common credential formats, and personal email addresses. Run a dedicated secret scanner before publishing new history. Automated scanning does not prove that a repository contains no sensitive information.

The initial publication audit on 2026-11-15 used Gitleaks 8.30.1 with redacted reporting. The staged source scan found no credential matches. The tracked-file audit found no excluded files, personal home paths, or personal email addresses. Bundled WebP images contained color profiles and image-display metadata; no EXIF or XMP metadata was present.
