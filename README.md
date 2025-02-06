# cli

<!-- badges -->
[![cljdoc badge](https://cljdoc.org/badge/com.lambdaisland/cli)](https://cljdoc.org/d/com.lambdaisland/cli) [![Clojars Project](https://img.shields.io/clojars/v/com.lambdaisland/cli.svg)](https://clojars.org/com.lambdaisland/cli)
<!-- /badges -->

Command line parser with good subcommand and help handling

## Features

<!-- installation -->
## Installation

To use the latest release, add the following to your `deps.edn` ([Clojure CLI](https://clojure.org/guides/deps_and_cli))

```
com.lambdaisland/cli {:mvn/version "0.20.81"}
```

or add the following to your `project.clj` ([Leiningen](https://leiningen.org/))

```
[com.lambdaisland/cli "0.20.81"]
```
<!-- /installation -->

## Rationale

This is an opinionated CLI argument handling library. It is meant for command
line tools with subcommands (for example git, which has `git commit`, `git log`
and so forth). It works exactly how we like it, which mostly means it sticks to
common conventions (in particular the prominent GNU conventions), needs little
ceremony, and provides your tool with built-in help facilities automagically.

It is Babashka compatible, and in fact pairs really nicely with `bb` for making
home-grown or general purpose tools.

It scales from extremely low ceremony basic scripts, to fairly complex setups.

This library helps you write [Well-behaved Command Line
Tools](https://lambdaisland.com/blog/2020-07-28-well-behaved-command-line-tools)

## Usage

The main entrypoint is `lambdaisland.cli/dispatch`, usually you're fine with the
single-argument version, which defaults to consuming `*command-line-args*`.

At its simplest you just pass it a var of a function named the same as your
script (this matters for the help text, as well see below).

```clj
#!/usr/bin/env bb

(require '[lambdaisland.cli :as cli]
         '[clojure.pprint :as pprint])

(defn cli-test 
  "Ground breaking tool breaks new ground."
  [flags]
  (pprint/pprint flags))
  
(cli/dispatch #'cli-test)
```

This is enough to get a basic `--help` output.

``` shell
$ cli-test --help
Usage: cli-test [<args>]

Ground breaking tool breaks new ground.
```

And you can start passing positional arguments and basic flags to your script,
your function will receive these all as a single map.

```shell
$ cli-test --abc hello world --format=txt
{:abc 1, :format "txt", :lambdaisland.cli/argv ["hello" "world"]}
```

To do more interesting things we first wrap the var in a map.

```clj
(cli/dispatch {:command #'cli-test})
```

`:command` doesn't have to be a var, it can be a simple function, but with a var
the docstring will still be used, as well as the var name, so this is equivalent
to:

```clj
(cli/dispatch 
 {:command #'cli-test
  :doc "Ground breaking tool breaks new ground."
  :name "cli-test"})
```

### Flags

Now we can add extra things to the dispatch configuration map, notably `:flags`
and `:commands`. We'll explain flags first.

Because you haven't told lambdaisland/cli about the flags your script
understands, it has to guess how to handle them. If you have arguments like
`--input world.txt`, then without extra information we don't know if `world.txt`
is a positional argument to the script itself, or to `--input`. In this case
`cli` assumes that it's a separate positional argument. If you use the
`--input=world.txt` then it does know that this is the argument to `--input`,
but ideally these two (with or without the `=`) should behave the same.

`:flags` takes a vector or map of "flag specification" + "flag options" pairs,
where the options can be simply a string if you only need to set a documentation
string. The reason we also support a vector is so you can be explicit about the
order that flags should be shown in the help text. (The same is true for
`:commands`).

```clj
(cli/dispatch
 {:command #'cli-test
  :flags ["-v, --verbose" "Increases verbosity"
          "--input FILE" "Specify the input file"
          "--env=<dev|prod|staging>" {:doc "Select an environment"}] })
```

This is what the help text looks like now:

```shell
$ cli-test --help
Usage: cli-test [-v | --verbose] [--input FILE] [--env=<dev|prod|staging>] [<args>]

Ground breaking tool breaks new ground.

  -v, --verbose                  Increases verbosity
      --input FILE               Specify the input file
      --env=<dev|prod|staging>   Select an environment 
```

And if we invoke it

```
$ cli-test -vvv --input=world.txt --env prod --cool=123
{:verbose 3,
 :input "world.txt",
 :env "prod",
 :cool "123",
 :lambdaisland.cli/argv []}
```

So your `:command` function receives a map, which contains the parsed flags.
Positional argument are also added to the map, if they are given a name (which
we'll show below), but they are also gathered under the special key
`::cli/argv`.

Note that this map is also bound to the dynamic var `cli/*opts*`, so it's not
necessary to pass it around everywhere. This is especially useful for flags that
influence global behavior, like `--verbose`, `--silent`, or `--dry-run`.

At this point a few things are worth calling out.

- To specifiy arguments to flags or subcommands we support both the all-caps
  version or the angle brackets, version, so `--flag ARG` or `--flag <arg>`.
- You can optionally add an equal sign in between when specifying the argument,
  e.g. `--flag=<arg>` .
- When invoking the command, both the version with or without `=` are
  understood, regardless of how the flag was specified.
- If a flag is not predefined then only the `--flag=<arg>` version is able to
  pass along an argument, with `--flag arg` we assume that flag does not take
  any arguments, and arg is instead treated as a positional argument to the
  (sub-)command.
- For flags without arguments, the default behavior is to count the number of
  flags. This is useful for things like `--verbose` that can be specified
  multiple times. For other cases you can simply treat it as a boolean.
- The key that is used in the options map is based on the long-form.
  (double-dash) form. So `-v, --verbose` means you'll get a `:verbose` key, for
  `-v` or `--verbose`.
- You can use the `--[no-]foo` syntax for adding both a `--foo` and a `--no-foo`
  flag, in this case the resulting value in the opts map will be `:foo true` or
  `:foo false`.
- You can add a `:default` to a flag, like `["--port PORT" {:default 8080}]`.
- You can set a `:parse` function which will be used to parse/coerce the
  argument. The default will parse numbers (basic longs and doubles, no special
  formats), and nothing else.
- If you have a `:default` which is a string, and you have a `:parse` function,
  then the default will be run through parse as well. It's generally best to set
  the default to a string or a number, this will look better in the help text,
  where we show the default.
- A single dash (`-`) is considered a positional argument, conventially
  indicating stdin/stdout.
- To pass a positional argument that starts with a dash, prefix it with a
  backslash. lambdaisland/cli will remove the backslash, and treat the remainder
  as a positional argument rather than a flag. Note that the shell does its own
  backspace (character escape) handling, so in practice this means prefixing
  with two backslashes, e.g. `\\--foo`.
- For flags that are expected to be passed multiple times, e.g. `-u foo -u bar`,
  set `:coll? true`, in that case you will always receive them as a vector, even
  if there is only one.

You can also explicitly set which key to use with `:key`, as well as setting a
specific `:value`, for instance:

```clj
(cli/dispatch
 {:command #'cli-test
  :flags
  ["--prod" {:doc "Use the production environment"
             :key :env
             :value :prod}
   "--staging" {:doc "Use the staging environment"
                :key :env
                :value :staging}]})
```

```shell
$ cli-test --staging
{:env :staging, :lambdaisland.cli/argv []}
```

Alternatively you can set a handler function, which determines what happens when
this flag is used. It is passed the options map, and any arguments to the flag,
but you could also use it for instance to change global state.

```clj
(cli/dispatch
 {:command #'cli-test
  :flags
  ["--prod" {:doc "Use the production environment"
             :handler (fn [opts] (assoc opts :env :prod))}
   "--input FILE" {:doc "Use the staging environment"
                   :handler (fn [opts file] (assoc opts :input-file file))}]})
```

```shell
$ cli-test --input foo
{:input-file "foo", :lambdaisland.cli/argv []}
```

Note that if you have a `:default` and a `:handler`, then the handler will be
called with the default first, always, and possibly later again with based on
any CLI flags.

<!-- (Note that a flag CAN have multiple positional arguments (e.g. `"--foo A B"`), -->
<!-- but this is discouraged since it goes contrary to expectations of command line -->
<!-- utilities, and it can not be used with the `=` syntax.) -->

If you are explicit about which flags you accept, then you may prefer not to let
`lambdaisland/cli` play loosey goosey and simply accept anything. In this case
you can set `:strict? true`. In this mode only explicitly configured flags are
accepted, others throw an error.

Another possibility is to set `:middleware` for a flag, this is a function or
list of functions that get wrapped around the final command.

```clj
(cli/dispatch
 {:commands
  ["ls" {:command #'list-widgets
         :flags
         ["-l, --long"
          {:doc        "Use long format"
           :middleware [(fn [cmd]
                          (fn [opts]
                            (binding [*format* :long]
                              (cmd opts))))]}]}]})
```

Finally, it's possible to set `:required`, to indicate for users that a flag
must always be passed:

```clj
(cli/dispatch
 {:command #'cli-test
  :flags ["-v, --verbose" "Increases verbosity"
          "--input FILE" "Specify the input file"
          "--env=<dev|prod|staging>" {:doc "Select an environment"
                                      :required true}] })
```

#### Flag option reference

- `:doc` docstring
- `:default` default value
- `:value` value to be associated with the key (for flags that don't take arguments)
- `:handler` handler function, `(fn [opts & flag-args] ,,,)` 
- `:middelware` functions which wrap the final command handler
- `:coll?` flag can be specified  multiple times, will result in a vector 
- `:parse` function used to coerce the flag argument

### Commands

`lambdaisland/cli` is specifically meant for CLI tools with multiple subcommands
(and sub-sub-commands, and so forth). In this way it forms an appealing
alternative to bb tasks, for people who prefer conventional CLI ergonomics, as
well as keeping everything in a single script.

Specifying commands is similar to specifying flags, you provide a vector of map
with pairs, where the first element in the pair is a string that specifies the
command, and any arguments, and the second element is a map specifying how that
command should be used. Instead of a map you can also provide a var, in which
case the docstring (`:doc`) and command (`:command`) are taken from the var.

The way this works is we take the var metadata, and add `:command` pointing at
the var itself. If you provide a map with a var `:command`, then we merge the
metadata and whatever values you provided. So you can also specify additional
keys through var metadata.

```clj
(cli/dispatch
 {:name    "cli-test"
  :doc     "This is my cool CLI tool. Use it well."
  :strict? true
  :commands
  ["ls"       #'list-widgets
   "add <id>" #'add-widget
   "auth" {:doc      "Auth commands"
           :commands ["login"  #'auth-login
                      "logout" #'auth-logout]}]
  :flags
  ["-v,--verbose" "Increase verbosity"
   "--input=<foo>" "Input file"]})
```

```shell
$ cli-test auth login x y z
{:lambdaisland.cli/command ["auth" "login"],
 :lambdaisland.cli/argv ["x" "y" "z"]}
```

Notice now CLI has split the positional argument into a "command" and "argv"
(argument vector), and made both available in the map passed to the command (and
bound to `cli/*opts*`).

Here you start seeing the benefits of this stuff managed for you, things like
requesting the help information for a subcommand "just works".

```shell
$ cli-test --help   
Usage: cli-test [-v | --verbose] [--input=<foo>] [ls | auth | add] [<args>...]

This is my cool CLI tool. Use it well.

  -v, --verbose       Increase verbosity
      --input=<foo>   Input file        

  ls                    List widgets 
  auth <login|logout>   Auth commands
  add <id>              Add a widget 
```

```shell
$ cli-test auth --help
Usage: cli-test auth [-v | --verbose] [--input=<foo>] [login | logout] [<args>...]

Auth commands

  -v, --verbose       Increase verbosity
      --input=<foo>   Input file        

  login    Login with your account
  logout   Log out of your account
```

Commands can specify additional flags, which will be available for that command
or any subcommands.

```clj
(cli/dispatch
 {:name    "cli-test"
  :doc     "This is my cool CLI tool. Use it well."
  :commands
  ["ls"       {:command #'list-widgets
               :flags   ["-l, --long" "Use long format"]}
   "add <id>" #'add-widget]
  :flags
  ["-v,--verbose" "Increase verbosity"]})
```

In this case the `--long` option is only available for `cli-test ls`.

```shell
$ cli-test --help     
Usage: cli-test [-v | --verbose] [ls | add] [<args>...]

This is my cool CLI tool. Use it well.

  -v, --verbose   Increase verbosity

  ls                     
  add <id>   Add a widget
```

```shell
$ cli-test ls --help
Usage: cli-test ls [-v | --verbose] [-l | --long] [<args>...]

This is my cool CLI tool. Use it well.

  -v, --verbose   Increase verbosity
  -l, --long      Use long format   
```

### Processing Order

Most of what lambdaisland/cli does is combine the command and flag descriptions
with the incoming command line arguments to build up a map, which then gets
passed to the command handler. This map gets built up in multiple steps.

The `:init` configuration flag, if present, provides the starting point. It can
be a map, or a zero-arity function returning a map.

Then we add `:default` values for top-level `:flags`. Normally these are simply
assoc'ed into the map provided by `:init`. If the flag also has a `:handler`,
then the opts map we have so far is passed to the handler, which can manipulate
it and return an updated version.

Then we actually start processing command line arguments, splitting them into
flags (start with a dash), or positional argument (does not start with a dash,
or a single dash). Flag arguments are processed as we encounter them,
potentially calling their handler, with the opts map we have so far, from
`:init`, defaults, and earlier flags and flag handlers. The positional arguments
are then used to determine which (sub-)command to invoke.

During any handler execution `cli/*opts*` is bound to the intermediate opts map
that we have so far, so any utility functions that are called can assume this is
available.

Sub-commands can add additional flag specifications, if we encounter those then
their defaults are added to the opts map, either directly or through their
defined handler. This does mean that these kind of flags can only be used
*after* the command. This may change in the future, since this is an unfortunate
asymmetry.

Finally the opts map gets bound to `cli/*opts*`, middleware gets invoked (so
`*opts*` can be accessed during middleware execution).

Pay attention to the fact that the opts map is built up in multiple steps, and
so flag handlers will only see a partial `opts`/`*opts*`. Because of this we
recommend mainly using handlers to manipulate the opts map, and not much else.
Any behavior should be reserved for the main command handler, and for
middleware, which are guaranteed to see all possible flags reflected in the opts
map they receive.

<!-- opencollective -->
## Lambda Island Open Source

Thank you! cli is made possible thanks to our generous backers. [Become a
backer on OpenCollective](https://opencollective.com/lambda-island) so that we
can continue to make cli better.

<a href="https://opencollective.com/lambda-island">
<img src="https://opencollective.com/lambda-island/organizations.svg?avatarHeight=46&width=800&button=false">
<img src="https://opencollective.com/lambda-island/individuals.svg?avatarHeight=46&width=800&button=false">
</a>
<img align="left" src="https://github.com/lambdaisland/open-source/raw/master/artwork/lighthouse_readme.png">

&nbsp;

cli is part of a growing collection of quality Clojure libraries created and maintained
by the fine folks at [Gaiwan](https://gaiwan.co).

Pay it forward by [becoming a backer on our OpenCollective](http://opencollective.com/lambda-island),
so that we continue to enjoy a thriving Clojure ecosystem.

You can find an overview of all our different projects at [lambdaisland/open-source](https://github.com/lambdaisland/open-source).

&nbsp;

&nbsp;
<!-- /opencollective -->

<!-- contributing -->
## Contributing

We warmly welcome patches to cli. Please keep in mind the following:

- adhere to the [LambdaIsland Clojure Style Guide](https://nextjournal.com/lambdaisland/clojure-style-guide)
- write patches that solve a problem 
- start by stating the problem, then supply a minimal solution `*`
- by contributing you agree to license your contributions as MPL 2.0
- don't break the contract with downstream consumers `**`
- don't break the tests

We would very much appreciate it if you also

- update the CHANGELOG and README
- add tests for new functionality

We recommend opening an issue first, before opening a pull request. That way we
can make sure we agree what the problem is, and discuss how best to solve it.
This is especially true if you add new dependencies, or significantly increase
the API surface. In cases like these we need to decide if these changes are in
line with the project's goals.

`*` This goes for features too, a feature needs to solve a problem. State the problem it solves first, only then move on to solving it.

`**` Projects that have a version that starts with `0.` may still see breaking changes, although we also consider the level of community adoption. The more widespread a project is, the less likely we're willing to introduce breakage. See [LambdaIsland-flavored Versioning](https://github.com/lambdaisland/open-source#lambdaisland-flavored-versioning) for more info.
<!-- /contributing -->

<!-- license -->
## License

Copyright &copy; 2024 Arne Brasseur and Contributors

Licensed under the term of the Mozilla Public License 2.0, see LICENSE.
<!-- /license -->
