# CF CLI allowed-value wildcards design

## Goal

Support `*` wildcards in dictionary `(allowed)` values while preserving exact, case-insensitive matching for values
without wildcards.

## Matching rules

Each `*` matches one or more nonwhitespace characters. It does not match an empty string, spaces, tabs, or line breaks.
All other characters are literals, including regexp punctuation such as `.`, `-`, and `+`. Matching remains
case-insensitive and must cover the complete detected string.

For example, `*@example.com` compiles to the equivalent of `(?i)^\S+@example\.com$`. It accepts a nonempty,
whitespace-free local part and rejects an empty local part or one containing spaces.

## Integration

The dictionary keeps its existing lowercase exact-value set and adds compiled wildcard expressions. The scanner checks
both through one helper. Grand-report exclusions remain before allowed-value checks so registered exclusions continue to
produce JSON diagnostic events.
