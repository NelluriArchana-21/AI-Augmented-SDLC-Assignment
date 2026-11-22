# Smart Todo Manager Validation Harness

The validation harness runs configured checks independently of application
logic. It records each check's result and preserves each run as a separate
evidence directory. The harness does not repair application code.

Run commands from the repository root unless a command says otherwise.

## Prerequisites

- Java 21 and Maven for the configured backend checks.
- Python 3 and the PyYAML package for both harness scripts to read
  `config/validation.yml`.
- Bash, curl, and jq for the configured environment/API checks.
- A running service and isolated task storage for black-box API validation.

The Maven project targets Java 21 (`java.version`, compiler source, and compiler
target are all 21). The configured `environment-prerequisites` check explicitly
requires the active `java -version` output to report Java 21. A prior workspace
inspection reported Java 25.0.5, which does not meet that prerequisite. The
2026-11-22 validation run selected Java 21 and recorded the environment
prerequisite as passed; compatibility under Java 25 remains unestablished.

PyYAML is required even when using the default configuration. If either script
reports that PyYAML is unavailable, install it for the Python 3 interpreter
used to run the script before invoking the harness.

## Run the configured checks

The default configuration is
`part-1a-smart-todo/harness/config/validation.yml`:

```sh
bash part-1a-smart-todo/harness/scripts/run-validation.sh
```

An alternate YAML file can be supplied explicitly:

```sh
bash part-1a-smart-todo/harness/scripts/run-validation.sh \
  --config /path/to/validation.yml
```

The current configuration defines eight mandatory checks: environment
prerequisites, Maven compilation, Spotless, SpotBugs with FindSecBugs,
OWASP Dependency-Check, unit tests, integration tests, and black-box API
validation. Each check has an expected exit code, command, timeout, and optional
prerequisites. The runner executes checks in configuration order and
`continue_after_check_failure: true` means later checks continue after an
individual failure.

The runner records the check ID and category, mandatory flag, command, working
directory when applicable, UTC start/end timestamps, duration, exit code,
result, failure details, and configured expected outcome. It captures stdout
and stderr both in the JSONL record and in per-check `.stdout.txt` and
`.stderr.txt` files. Environment variable values are not written to evidence.

Result states are:

- `passed`: the command's exit code matched its configured expected exit code.
- `failed`: the command ran, but its exit code did not match.
- `blocked`: a declared prerequisite was unavailable, so the command was not
  run. For example, a missing service URL or missing black-box script blocks
  that check.
- `timed_out`: the command exceeded its configured timeout.
- `error`: the working directory was missing or the command could not be
  executed.
- `skipped`: a later check was not run because
  `continue_after_check_failure` was set to `false`. The current configuration
  continues instead.

Prerequisite failures differ by check: missing declared prerequisites produce
`blocked`; the environment check itself runs a command that asserts Java 21 and
required executables, so an unsuccessful assertion is recorded as `failed`.

## Evidence layout and exit status

Each run gets a unique directory name containing a UTC timestamp and a random
suffix. The runner writes to a temporary staging directory first, then moves
the completed run into the configured destination:

```text
part-1a-smart-todo/harness/logs/
├── failed/
│   └── <run-id>/
│       ├── results.jsonl
│       ├── <check-id>.stdout.txt
│       └── <check-id>.stderr.txt
└── passed/
    └── <run-id>/
        ├── results.jsonl
        ├── <check-id>.stdout.txt
        └── <check-id>.stderr.txt
```

There is one stdout and stderr sidecar per configured check; the abbreviated
tree shows the naming pattern, not a complete run. The JSONL file contains one
record per check. Failed and passed runs use separate roots, and unique run
IDs prevent a later run from overwriting an earlier one.

The runner places a run under `failed/` and returns the configured
`final_exit_code_on_mandatory_failure` (currently `1`) if any mandatory check
does not pass, including a blocked, timed-out, errored, or skipped result. If
all mandatory checks pass, it places the run under `passed/` and returns the
configured success code (currently `0`). Optional non-passing checks alone do
not trigger the mandatory-failure exit code. All eight checks in the current
configuration are mandatory.

Raw evidence is preserved for review. The summarizer reads it without changing
or deleting it.

## Summarize a run

Pass either a run directory or its JSONL file to the summarizer. For example,
replace `<run-id>` with the directory name printed by the runner:

```sh
bash part-1a-smart-todo/harness/scripts/summarize-failures.sh \
  part-1a-smart-todo/harness/logs/failed/<run-id>
```

The script resolves `results.jsonl` inside a run directory. It also accepts
the file path directly:

```sh
bash part-1a-smart-todo/harness/scripts/summarize-failures.sh \
  part-1a-smart-todo/harness/logs/passed/<run-id>/results.jsonl
```

The default `validation.yml` is used to check evidence completeness and expected
check metadata. A different configuration can be supplied with
`--config CONFIG_PATH` before the evidence path.

The summary reports total, passed, failed, blocked, timed-out, execution-error,
and skipped checks, plus mandatory and optional non-passing counts. For each
non-passing check it displays the ID, category, result, available exit code,
expected and actual outcomes, recorded details, and available stdout/stderr.
Missing, empty, malformed, incomplete, or configuration-inconsistent evidence
is explicitly marked invalid/incomplete and returns exit code `2`; it is never
reported as a successful run. A valid run with mandatory non-passing checks
returns `1`. A valid run with no mandatory failures returns `0` (including a
run that has optional failures only).

## Implemented functionality and validation status

Implemented harness components:

- `config/validation.yml` defines the current eight checks, their commands,
  prerequisites, expected outcomes, timeouts, and evidence destinations.
- `scripts/run-validation.sh` executes the configured checks, captures output,
  and preserves each completed run in a unique evidence directory.
- `scripts/summarize-failures.sh` validates and summarizes evidence without
  modifying the underlying files.
- `scripts/validate-api-blackbox.sh` is present and was exercised against the
  running Spring Boot service as part of the independent API-contract check.

The validation harness was run on 2026-11-22 under Java 21 with the application
started at `http://localhost:8080` and an isolated JSON storage path. All eight
check commands terminated; seven passed and the mandatory
OWASP Dependency-Check gate failed.

### Evidence and repair record status

The initial and prior revalidation evidence remain preserved:

- Initial evidence: `part-1a-smart-todo/harness/logs/failed/20261113T063931Z-c4d3a39ac37e/`
- Revalidation evidence: `part-1a-smart-todo/harness/logs/failed/20261113T064641Z-0efd1cee479e/`

The new full-harness run is
`part-1a-smart-todo/harness/logs/failed/20261122T025202Z-8d990ff6d57b/`.
Its evidence summary is valid: 8 mandatory checks, 7 passed, 1 failed, with
no blocked, timed-out, errored, or skipped checks. Environment prerequisites,
Maven compilation, Spotless, SpotBugs/FindSecBugs, unit tests, integration
tests, and black-box API validation all passed. Dependency-Check failed with
exit code 1.

Unlike the two earlier runs, the full NVD feed download completed at
430,425/430,425 records. The update took about 36 minutes, but database
ingestion had two CVE record errors, so it was not a clean update.
Dependency-Check 10.0.3 emitted database errors for CVE-2026-6785 and CVE-2026-6786 because
their reference URLs exceeded its `URL VARCHAR(1000)` column (reported URL
lengths 1,585 and 1,115). It nevertheless completed analysis and generated
`dependency-check-report.html`, preserved in the run evidence. The Sonatype
OSS Index analyzer also reported invalid credentials and was disabled; this
is a coverage limitation, not a passing result.

The scan reported findings across eight artifact rows, containing 107 unique
CVE identifiers, with a highest reported CVSS of 9.8. The findings include
Hibernate Validator 8.0.1.Final, Jackson Databind 2.17.2, Log4j API 2.23.1,
Spring Boot 3.3.5, Spring Framework 6.1.14, and Tomcat 10.1.31. Some CVEs are
repeated across related artifacts. The raw scanner report is evidence of
scanner findings, not independent exploitability confirmation. The gate is
configured to fail at CVSS 0.0, and it did; no threshold was changed and no
findings were suppressed.

The original NVD 524 was an external availability failure in the earlier runs.
In the new run the full NVD feed download was reachable, but ingestion of two
records failed because their URLs exceeded the scanner database column width;
the scanner's OSS Index analyzer also could not authenticate. Reported
dependency findings independently failed the mandatory gate. No source or
dependency versions were changed in this validation pass; the findings require
review and remediation rather than suppression.

The human review decision recorded in `part-1a-smart-todo/harness-log.md` is
`REJECT` because the mandatory dependency-security gate failed on findings and
the scanner reported incomplete analyzer/database processing. The functional
checks passed, but the Part 1A acceptance gate is not satisfied.

### Dependency remediation attempt — 2026-11-22

The backend POM was updated to Spring Boot 3.5.16, embedded Tomcat 10.1.62,
Log4j 2.25.5, and Dependency-Check 12.2.2. Jackson is managed by the Boot BOM
at 2.21.4. The Dependency-Check database was configured under the Maven
`target` directory to ensure a new database after the plugin major-version
upgrade. The CVSS threshold remains 0.0; no suppressions were added and no
analyzers were disabled.

The resolved dependency tree confirmed Spring Framework 6.2.19, Jackson
2.21.4, Tomcat embedded core/el/websocket 10.1.62, Hibernate Validator
8.0.3.Final, and Log4j API 2.25.5. The complete Maven test suite passed on
Java 21.0.13.0.2: 40 tests, 0 failures, 0 errors, 0 skipped; `BUILD SUCCESS`.
The application also started successfully with Tomcat 10.1.62. Port 8081 was
already occupied, so the independent black-box validation used port 18081.

Full harness evidence:
`part-1a-smart-todo/harness/logs/failed/20261122T064001Z-a19da4c615b7/`.
The evidence summary is valid: 8 mandatory checks, 7 passed, 1 failed. The
environment, compilation, Spotless, SpotBugs/FindSecBugs, unit, integration,
and black-box API checks passed. Dependency-Check 12.2.2 failed before
vulnerability analysis because the NVD API returned HTTP 429 after 31 retries;
the log also says no NVD API key was provided. There is no report from this
run, so it confirms neither that candidate versions resolve the listed CVEs
nor that the long-reference insertion errors are fixed. The scan stopped
before those records could be tested against the widened database column.

No NVD API key was configured in the environment. No `ossIndexServer` entry
was found in Maven `settings.xml`; the earlier Dependency-Check 10.0.3 run's
OSS Index HTTP 401 remains an unresolved authentication/coverage limitation.
Credentials must be configured locally through supported environment or
Maven settings mechanisms, not committed to the POM. A fresh complete scan
with NVD update and OSS Index authentication is required. Until then, the
remediation is partial and Part 1A remains **REJECTED**.
