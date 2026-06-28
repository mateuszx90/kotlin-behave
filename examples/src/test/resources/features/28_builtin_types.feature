Feature: Built-in type conversions

  @Type can target an enum or a built-in type with no hand-written @TypeConverter:
  enums convert case-insensitively via valueOf, kotlin.time.Duration via Duration.parse.

  Scenario: an enum parameter needs no converter
    When I paint the wall "red"
    Then the wall is painted red

  Scenario: a Duration parameter is parsed by the built-in
    When the request times out after "1500ms"
    Then the timeout is at least one second
