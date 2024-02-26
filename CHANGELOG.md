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