# Part 1B Evaluation Harness

The harness is independent of the Part 1A application. The reminder agent
produces action proposals only; it has no reminder persistence, lookup, or
mutation interface.

Evaluation configuration and scripts belong in `config/` and `scripts/`.
When evaluations are implemented, each run's evidence must be written to a
unique `logs/failed/<run-id>/` or `logs/passed/<run-id>/` directory based on
the observed result. Initial and post-repair runs must remain separate and
must never overwrite earlier evidence. Do not store credentials or secrets in
the harness or its evidence.
