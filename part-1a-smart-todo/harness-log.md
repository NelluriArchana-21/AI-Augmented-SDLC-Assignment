# Smart Todo Manager harness evidence log

## Initial run (T027)

- Run ID: `20261113T063931Z-c4d3a39ac37e`
- Evidence directory: `part-1a-smart-todo/harness/logs/failed/20261113T063931Z-c4d3a39ac37e/`
- Summary: `VALID`; total checks 8; passed 7; failed 1; mandatory failures 1.
- Non-passing check: `owasp-dependency-check`
- Observed issue: Dependency-Check failed while updating the NVD database with `NVD Returned Status Code: 524` after `Downloaded 10,000/424,978 (2%)`.
- Supporting file: `part-1a-smart-todo/harness/logs/failed/20261113T063931Z-c4d3a39ac37e/owasp-dependency-check.stdout.txt`

### Root cause

The failure is external to the repository: the OWASP Dependency-Check plugin contacts the NVD service to refresh its local database before scanning. In this environment the NVD API returned HTTP 524 during the update phase, so the plugin aborted before it could analyze the project. This was not an application logic failure.

### Repair assessment

No repository-only code repair was justified for this run. The project code, unit tests, integration tests, and the independent black-box API validation all passed; the only failing gate was the security update dependency on the external NVD service.

## Revalidation run (T029)

- Run ID: `20261113T064641Z-0efd1cee479e`
- Evidence directory: `part-1a-smart-todo/harness/logs/failed/20261113T064641Z-0efd1cee479e/`
- Summary: `VALID`; total checks 8; passed 7; failed 1; mandatory failures 1.
- Result: same upstream NVD failure reproduced without code changes.
- Supporting evidence: `part-1a-smart-todo/harness/logs/failed/20261113T064641Z-0efd1cee479e/owasp-dependency-check.stdout.txt`

## Full harness run after NVD retry — 2026-11-22

- Run ID: `20261122T025202Z-8d990ff6d57b`
- Evidence directory: `part-1a-smart-todo/harness/logs/failed/20261122T025202Z-8d990ff6d57b/`
- Evidence summary: `VALID`; total checks 8; passed 7; failed 1; mandatory failures 1; blocked, timed-out, errored, and skipped checks 0.
- Passed checks: environment prerequisites, Maven compilation, Spotless, SpotBugs/FindSecBugs, unit tests, integration tests, and black-box API validation.
- Failed check: `owasp-dependency-check` (exit code 1; elapsed 2,191.518 seconds).
- NVD feed download: reached 430,425/430,425 records; the two database errors below mean ingestion was not clean. The earlier HTTP 524 was not reproduced in this run.
- Scanner data errors: Dependency-Check 10.0.3 could not store CVE-2026-6785 and CVE-2026-6786 references because their URLs exceeded the database `VARCHAR(1000)` limit. Analysis continued, but those records were not successfully processed.
- Additional analyzer limitation: the Sonatype OSS Index analyzer reported invalid credentials and was disabled.
- Vulnerability findings: 8 artifact rows, 107 unique CVE identifiers, highest reported CVSS 9.8. Findings include Hibernate Validator 8.0.1.Final, Jackson Databind 2.17.2, Log4j API 2.23.1, Spring Boot 3.3.5, Spring Framework 6.1.14, and Tomcat 10.1.31. The complete generated report is preserved as `dependency-check-report.html` in the evidence directory. Findings are scanner results and are not independently confirmed exploitability assessments.
- No dependencies were upgraded and no findings were suppressed. The `failBuildOnCVSS` threshold remains 0.0.
- The result summarizer validated all eight records and classified the run as `VALID` with one mandatory failure.

### Updated root-cause and acceptance assessment

The earlier two runs failed before project analysis because the NVD service
returned HTTP 524. The 2026-11-22 controlled full-harness retry downloaded the
full NVD feed, but the gate still failed because Dependency-Check reported
vulnerable dependency versions at the unchanged CVSS threshold. Dependency-Check
10.0.3 also had database-width errors for two NVD records and could not use its
OSS Index analyzer with the available credentials. These findings do not
justify suppressions or a passing decision. Dependency and scanner remediation
remain unperformed, so acceptance remains **REJECT**.

The latest evidence does not establish that the dependency findings are all
applicable to reachable code paths; the report requires dependency/CVE review.
The full Part 1A acceptance gate is nevertheless not satisfied because the
mandatory scan returned exit code 1.

## Decision after the 2026-11-22T064001Z attempt

REJECT

Reason: all seven non-Dependency-Check mandatory checks passed, but the
mandatory security scan failed on reported dependency findings and also
recorded analyzer/database limitations. The NVD update completed, so the latest
blocker is not the earlier NVD 524 outage. No acceptance is claimed.

## Dependency remediation attempt — 2026-11-22

- POM changes: Spring Boot parent 3.3.5 → 3.5.16; `tomcat.version` 10.1.62;
  `log4j2.version` 2.25.5; Dependency-Check plugin 10.0.3 → 12.2.2; new
  Dependency-Check data directory under `backend/target/dependency-check-data`.
  The Boot BOM manages Jackson at 2.21.4. The CVSS threshold remains 0.0;
  there are no new suppressions or disabled analyzers.
- Resolved dependency tree: Spring Framework 6.2.19, Jackson 2.21.4, embedded
  Tomcat core/el/websocket 10.1.62, Hibernate Validator 8.0.3.Final, Log4j
  API 2.25.5.
- Maven test suite: Java 21.0.13.0.2; 40 tests, 0 failures, 0 errors,
  0 skipped; `BUILD SUCCESS` (26.662 seconds).
- Full harness run ID: `20261122T064001Z-a19da4c615b7`
- Evidence directory:
  `part-1a-smart-todo/harness/logs/failed/20261122T064001Z-a19da4c615b7/`
- Summary: `VALID`; 8 checks, 7 passed, 1 failed; no blocked, timed-out,
  errored, or skipped checks. All checks except Dependency-Check passed.
- Dependency-Check 12.2.2 failed before analysis with NVD HTTP 429 after 31
  retries. Its log states that no NVD API key was provided. No report was
  produced; candidate CVE remediation and the H2 long-reference fix therefore
  remain unverified in a complete scan.
- OSS Index authentication remains unresolved: the previous run recorded
  HTTP 401, and no `ossIndexServer` Maven settings entry is configured here.

Assessment at the time of this attempt: dependency and scanner version updates
were implemented and application tests plus seven other mandatory checks
passed, but this mandatory security scan was incomplete. A fresh complete scan
was required; this attempt was not acceptance.

## Decision after the 2026-11-22T064001Z attempt

REJECT

Reason: the `20261122T064001Z` mandatory Dependency-Check attempt did not
complete its NVD data update because of HTTP 429, and OSS Index credentials
were unavailable. This decision describes that attempt; it is separate from
the later completed scan recorded below.

## Latest standalone Dependency-Check scan — 2026-11-22

- Run ID: `20261122T105739Z-dependency-check-3d7a3ae8`
- Evidence directory:
  `part-1a-smart-todo/harness/logs/failed/20261122T105739Z-dependency-check-3d7a3ae8/`
- Preserved evidence includes the HTML report, JSON report, stdout, stderr,
  exit code, start/completion timestamps, and run metadata.
- The NVD update and vulnerability analysis completed. The JSON report
  contains exactly 18 vulnerability entries across 18 unique identifiers,
  with maximum CVSS 9.8.
- Dependency-Check exited with status 1 because vulnerabilities met the
  mandatory CVSS threshold of 0.0. The threshold is unchanged.
- Security gate: **FAILED**.
- Final Part 1A security decision: **REJECT**.

This is a separate, later scan from the `20261122T064001Z` attempt, which
failed during the NVD update with HTTP 429 before vulnerability analysis.
The completed report records scanner findings; it does not establish that
every finding is exploitable or that remediation is complete. No clean
security result or acceptance is claimed.
