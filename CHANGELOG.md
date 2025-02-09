# Unreleased

## Added

## Fixed

## Changed

# 0.21.84 (2025-02-09 / fa56d06)

## Added

- Support `:middleware` in command specs, not just in flag handlers

# 0.20.81 (2025-02-06 / 4f86a6e)

## Fixed

- Call `parse` of default value, as documented, also when no `:handler` for a
  flag is specified. Fixes #9

# 0.19.78 (2024-12-23 / a1f884e)

## Added

- Add provenance tracking, `:lambdaisland.cli/sources` is a map from options key
  to human readable description of where that key came from, e.g. `--foo command
  line flag`, or `positional command line argument idx=0`. See
  [lambdaisland/config](https://github.com/lambdaisland/config) for a use case.

# 0.18.74 (2024-08-05 / 14b74ba)

## Added

- Allow using a var that points to a map as a command specification

# 0.17.71 (2024-07-24 / ac0239c)

## Fixed

- Add missing require for `clojure.pprint`

# 0.16.68 (2024-07-17 / 3c1b328)

## Added

- Added `:coll?` flagopt for flags that can be specified multiple times
- Correctly print multiline flag docstrings

## Fixed

## Changed

# 0.15.65 (2024-07-16 / 8917f5a)

## Fixed

- Make sure `cli/*opts*` reflects what the main command receives

# 0.14.62 (2024-07-04 / 8a515f3)

## Added

- Allow `:init` also on nested subcommands, gets merged into top level init.
  Flags are processed afterwards, so this is good way to set initial data that
  flags can filter/change
- Flags handlers can now be specified directly as vars, the docstring is used as flag doc

# 0.13.58 (2024-06-07 / 09cd1f9)

## Fixed

- Fix generated help text when subcommand is not recognized

# 0.12.53 (2024-05-30 / 7b52e6d)

## Changed

- When catching a top-level exception, print the ex-data, if any
- If the ex-data contains an `:exit` key, use that as exit code instead of 1
- Rework the help layout be more man-page-like
    
# 0.11.48 (2024-05-27 / d98625f)

## Fixed

- Show correct help for subcommands

## Changed

- Change the help layout to be man-page style
- When catching a top-level exception, print the ex-data (if any)
- Use the `:exit` in the ex-data as exit code (1 otherwise)

# 0.10.45 (2024-05-27 / 025991c)

## Added

- Better help messages: say why we are showing the help (no such command,
  missing positional args)

# 0.9.42 (2024-05-27 / 3b81e73)

## Changed

- Exit with error message if conditional arguments are missing

# 0.8.39 (2024-05-24 / e967e9c)

## Added

- Added `:required` for `:flags`
- Support commands with arguments and subcommands at the same time

# 0.7.33 (2024-02-27 / cb19704)

## Fixed

- Remove debug call

# 0.6.30 (2024-02-27 / c9696a0)

## Added

- `-h` can now be used to get help, in addition to `--help`

## Changed

- When encountering parse errors (invalid arguments), print a message and exit,
  rather than throwing (which looks quite ugly from a bb script)

# 0.5.27 (2024-02-26 / 28f559d)

## Added

- Allow a command spec to be just a function

## Fixed

- Preserve command order in help text when using a vector

# 0.4.24 (2024-02-17 / 5a1e316)

## Added

- Bind the options map to `cli/*opts*`, for easy access.
- Show the default for a flag in the help text.
- Add a docstring to the main entry point (`dispatch`)
- Bind `*opts*` during flag handler execution

## Fixed

- Recognize `-` and `\\--foo` as positional args

## Changed

- When given both a `:default` and a `:handler` for a flag, call the handler
  with the default, rather than just assoc-ing it.
- When given a string `:default` and a `:parse` function for a flag, run the
  default value through the parse function, rather than using it directly. Using
  the unparsed string form for the default is preferable over for instance using
  a keyword, since it leads to better help text rendering.
- Improve and document the processing logic, especially when it comes to
  subcommand flags with handler functions.

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
