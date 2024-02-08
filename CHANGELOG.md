# Unreleased

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
