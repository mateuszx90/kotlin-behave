#noinspection CucumberUndefinedStep
Feature: Tag filtering

    Scenario: No filter runs all scenarios
        Given a feature with a smoke scenario and a wip scenario
        When I run with no tag filter
        Then both scenarios pass

    @debug
    Scenario: Tag filter skips non-matching scenarios
        Given a feature with a smoke scenario and a wip scenario
        When I run with tag filter "@smoke"
        Then only the smoke scenario runs
        And the wip scenario is skipped

    @debug
    Scenario: After hooks receive fresh ctx for skipped scenarios in run
        Given a feature with a smoke scenario and a wip scenario
        And an after hook that records the ctx instance
        When I run with tag filter "@smoke"
        Then the skipped scenario after hook received a different ctx than the smoke scenario

    @debug
    Scenario: Per-scenario runner does not call after hooks for skipped scenarios
        Given a feature with a smoke scenario and a wip scenario
        And an after hook that records how many times it ran
        When I run the per-scenario runner with tag filter "@smoke"
        Then the after hook ran exactly 1 time
