# AI-Augmented SDLC Assignment

**Course:** AI-Augmented Software Development Life Cycle (SE ZG534)

**Assignment:** Spec-Driven Development and Harness Engineering

## Overview

This repository contains two applications developed using Spec-Driven Development (SDD) and independently validated using harness engineering.

## Applications

### Part 1A – Smart Todo Management System

A deterministic task management application built using Java and Spring Boot.

Planned features:
- Create, update and delete tasks
- Task status and priority management
- Tag-based filtering
- Priority-based sorting
- JSON file persistence
- Automated validation using an independent harness

### Part 1B – Natural-Language Reminder Agent

A non-deterministic application that interprets natural-language reminder requests.

Planned features:
- Extract reminder title, date and time
- Handle missing or ambiguous information
- Return structured JSON responses
- Validate outputs using structural, invariant and semantic checks
- Evaluate against a test dataset

## Technology Stack

- Java 21
- Spring Boot
- Maven
- JUnit 5
- Git and GitHub
- GitHub Spec Kit
- VS Code and IntelliJ IDEA

## Development Approach

Both applications will follow the Spec Kit workflow:

1. Constitution
2. Specify
3. Clarify
4. Plan
5. Tasks
6. Analyze
7. Implement
8. Independent harness validation
9. Feedback and repair
10. Revalidation and human acceptance

## Repository Structure

- `part-1a-smart-todo/` – Deterministic application
- `part-1b-reminder-agent/` – AI-powered reminder application
- `reflection/` – Assignment reflection report

## Security

No API keys, credentials or other secrets will be committed to this repository.