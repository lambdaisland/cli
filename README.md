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
com.lambdaisland/cli {:mvn/version "0.0.0"}
```

or add the following to your `project.clj` ([Leiningen](https://leiningen.org/))

```
[com.lambdaisland/cli "0.0.0"]
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

## Usage

```clj
(require '[lambdaisland.cli :as cli])

(cli/dispatch
 {:commands
  ["run"
   {:description "Run the thing"
    :command (fn [args flags]
               ,,,)}

   "widgets"
   {:description "Work with widgets"
    :subcommands
    ["ls"
     {:description "List widgets"
      :command (fn [args flags] ,,,)}
     "create NAME"
     {:description "Create a new widget"
      :command (fn [args flags] ,,,)}
     "delete ID"
     {:description "Delete the widget with the given ID"
      :command (fn [args flags] ,,,)}]}]

  :flags
  ["-v,--verbose" {:description "Increase verbosity"}]})
```

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
