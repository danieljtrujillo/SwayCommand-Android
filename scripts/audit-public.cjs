const fs = require('node:fs');
const { execFileSync } = require('node:child_process');

// Audit tracked content. Gitignore alone does not remove an already tracked file.
const files = execFileSync('git', ['ls-files', '-z'], { encoding: 'utf8' }).split('\0').filter(Boolean);
const findings = [];
const excluded = /^(?:build|dist|node_modules|android\/assets|docs\/guide|\.agents|\.codex|\.aws|\.vscode|\.idea)\/|^\.env(?:\.|$)|\.(?:keystore|jks|p12|pfx|pem|key|apk|aab|pdf|log)$/i;
const checks = [
  ['user home directory', /(?:[A-Z]:\\+Users\\+[^\\\s"']+|\/(?:Users|home)\/[A-Za-z0-9_.-]+)/i],
  ['private key', /-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----/],
  ['GitHub token', /\b(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{30,})\b/],
  ['AWS access key', /\b(?:AKIA|ASIA)[A-Z0-9]{16}\b/],
  ['embedded URL credential', /https?:\/\/[^\s/:]+:[^\s/@]+@/],
  ['personal email provider', /[A-Z0-9._%+-]+@(?:gmail|outlook|hotmail|yahoo|icloud)\.com/i],
];
for (const file of files) {
  if (excluded.test(file)) findings.push(`${file}: excluded artifact is tracked`);
  const bytes = fs.readFileSync(file);
  if (bytes.includes(0)) continue;
  const text = bytes.toString('utf8');
  for (const [label, pattern] of checks) if (pattern.test(text)) findings.push(`${file}: ${label}`);
}
if (findings.length) { console.error(findings.join('\n')); process.exitCode = 1; }
else console.log(`Public-file audit passed for ${files.length} tracked files.`);
