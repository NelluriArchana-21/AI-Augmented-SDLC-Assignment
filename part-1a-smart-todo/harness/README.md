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
requires the active `java -version` output to report Java 21. The current
workspace reports Java 25.0.5, so that check will not pass until a Java 21
runtime is selected. Compatibility of the Maven build and configured plugins
under Java 25 has not been established; no result should be inferred for them.

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

The validation harness was run under Java 21 with the application started at
`http://localhost:8080` and an isolated JSON storage path. The actual recorded
outcome was a single mandatory failure in the OWASP Dependency-Check step.

### Evidence and repair record status

This workspace contains actual evidence from two independent harness runs:

- Initial evidence: `part-1a-smart-todo/harness/logs/failed/20261113T063931Z-c4d3a39ac37e/`
- Revalidation evidence: `part-1a-smart-todo/harness/logs/failed/20261113T064641Z-0efd1cee479e/`

The observed failure was not a code defect in the application itself. The
`owasp-dependency-check` command failed during the NVD database update phase:
`NVD Returned Status Code: 524` after `Downloaded 10,000/424,978 (2%)`.
All application code, unit tests, integration tests, and the black-box API
validation passed in both runs; only the dependency scan was blocked by the
external NVD service. Because the failure is upstream to the project and not a
repository logic error, no repository-only repair was justified in this
environment.

The human review decision recorded in `part-1a-smart-todo/harness-log.md` is
`REJECT` because the mandatory OWASP Dependency-Check gate did not complete
successfully. This is a validation outcome based on explicit evidence and not
an inferred assumption.
