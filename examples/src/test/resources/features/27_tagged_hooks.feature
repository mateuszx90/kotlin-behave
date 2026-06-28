Feature: Tag-scoped hooks

  A Before/After hook can be scoped to a tag expression so it only runs for
  scenarios that match — e.g. seeding a database only for @db scenarios.

  @db
  Scenario: a database-backed scenario gets the db hook
    Given a clean slate
    Then the database was prepared

  Scenario: a plain scenario is left untouched
    Given a clean slate
    Then the database was not prepared
