# AI-Augmented SDLC Assignment

**Course:** AI-Augmented Software Development Life Cycle (SE ZG534)

**Assignment:** Spec-Driven Development and Harness Engineering

## Overview

This repository contains two applications developed using Spec-Driven Development (SDD), with independent validation harnesses for each assignment part.

## Applications

### Part 1A – Smart Todo Management System

A deterministic task-management application built with Java and Spring Boot. Its implementation and validation results are documented in [the Part 1A harness README](./part-1a-smart-todo/harness/README.md).

### Part 1B – Natural-Language Reminder Agent

A non-deterministic application that interprets natural-language reminder requests and produces structured proposals. Its implementation and evaluation evidence are documented in [the Part 1B harness README](./part-1b-reminder-agent/harness/README.md).

## Technology Stack

- Java 21
- Spring Boot
- Maven
- JUnit 5
- Git and GitHub
- GitHub Spec Kit
- VS Code and IntelliJ IDEA

## Development Approach

The applications were developed using the Spec Kit workflow:

1. Constitution
2. Specify
3. Clarify
4. Plan
5. Tasks
6. Analyze
7. Implement
8. Independent harness validation
9. Feedback and repair
10. Revalidation and human review

## Validation status

- **Part 1A:** The latest Dependency-Check scan completed its NVD update and
  vulnerability analysis, reported 18 vulnerability entries across 18 unique
  identifiers (maximum CVSS 9.8), and failed the mandatory security gate.
  The final security decision is **REJECT**.
- **Part 1B:** The preserved local Surefire reports show 75 tests passed. The
  live Gemini evaluation remains incomplete after an HTTP 503 diagnostic; no
  acceptance is claimed.

## Repository Structure

- `part-1a-smart-todo/` – Deterministic application
- `part-1b-reminder-agent/` – AI-powered reminder application

## Security

No API keys, credentials or other secrets will be committed to this repository.