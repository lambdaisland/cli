# Unreleased

## Added

## Fixed

## Changed

# 0.3.19-alpha (2024-02-11 / d79ac0c)

Much expanded and improved version, see README for all details. This is
approaching the envisioned scope for this library.

- take docstring/command from var
- `:strict?` mode
- `:handler` and `:middleware` on flags
- Much improved help text rendering
- More lenient flag parsing
- Add `:default` and `:parse`

# 0.2.11-alpha (2024-02-08 / 0a352bc)

## Added

- Handle named command arguments
- Support `--flag FOO` and `--flag=<foo>` (and `--flag <foo>`)
- Boolean flags count by default, e.g. `-vvv` => `{:verbose 3}`

## Changed

- Command handlers take a single unified map

# 0.1.6-alpha (2024-02-04 / 593806e)

## Added

- subcommand handling 
- rudimentary flag handling
- help text generation