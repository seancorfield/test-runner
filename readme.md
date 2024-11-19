# test-runner

`test-runner` is a small library for discovering and running tests in
projects using native Clojure deps (i.e, those that use only Clojure's
built-in dependency tooling, not Leiningen/boot/etc.)

This fork -- `seancorfield` -- allows the `test-runner` to be used with
codebases that contain both `clojure.test`-compatible tests (including
[Expectations](https://github.com/clojure-expectations/clojure-test))
and [Lazytest](https://github.com/NoahTheDuke/lazytest) tests. It requires
at least Clojure 1.10.0. If you are using LazyTest, you will need to add
it as a dependency in your `deps.edn` file, in addition to `test-runner`.

## Rationale

See [Cognitect's original README](https://github.com/cognitect-labs/test-runner#rationale)
for the Rationale, based on tooling introduced with Clojure 1.9.0.

## Configuration

Include a dependency on this project in your `deps.edn`. You will
probably wish to put it in the `test` alias:

```clojure
:aliases {:test {:extra-paths ["test"]
                 :extra-deps {io.github.seancorfield/test-runner
                              {:git/tag "v0.6.0" :git/sha "d5f18c5"}}
                 :main-opts ["-m" "cognitect.test-runner"]
                 :exec-fn cognitect.test-runner.api/test}}
```

If you are using LazyTest, you will need to include it as a dependency
(in `:extra-deps`) as well (1.4.0 or later):

```clojure
                              io.github.noahtheduke/lazytest {:mvn/version "1.4.0"}
```

### Invoke with `clojure -X` (exec style)

Invoking the test-runner with `clojure -X` will call the test function with a map of arguments,
which can be supplied either in the alias (via `:exec-args`) or on the command-line, or both.

Invoke it with:

```bash
clj -X:test ...args...
```

This will scan your project's `test` directory for any tests defined
using `clojure.test` and run them.

You may also supply any of the additional command line options:

```
  :dirs - coll of directories containing tests, default= ["test"]
  :nses - coll of namespace symbols to test
  :patterns - coll of regex strings to match namespaces (clojure.test-only)
  :vars - coll of fully qualified symbols to run tests on
  :includes - coll of test metadata keywords to include
  :excludes - coll of test metadata keywords to exclude
  :outputs - coll of LazyTest-only output reporters to use
```

If neither :dirs or :nses is supplied, will use:

```
  :patterns [".*-test$"]
```

Note that when supplying collections of values via `clj -X`, you will need to quote those vectors (also see [quoting](https://clojure.org/reference/deps_and_cli#quoting) for more):

```
clj -X:test :dirs '["test" "integration"]'
```

### Invoke with `clojure -M` (clojure.main)

To use the older clojure.main command line style:

```bash
clj -M:test ...args...
```

Use any of the additional command line options:

```
  -d, --dir DIRNAME            Name of the directory containing tests. Defaults to "test".
  -n, --namespace SYMBOL       Symbol indicating a specific namespace to test.
  -r, --namespace-regex REGEX  Regex for namespaces to test. Defaults to #".*-test$"
                               (i.e, only namespaces ending in '-test' are evaluated)
                               (clojure.test-only)
  -v, --var SYMBOL             Symbol indicating the fully qualified name of a specific test.
  -i, --include KEYWORD        Run only tests that have this metadata keyword.
  -e, --exclude KEYWORD        Exclude tests with this metadata keyword.
      --output SYMBOL          Output format (LazyTest-only). Can be given multiple times. (Defaults to nested.)
  -H, --test-help              Display this help message
```

## Operation

There are three main steps to test execution:

* Find dirs to scan - by default "test"
* Find namespaces in those dirs (either by specific name or regex pattern or both) - by default all ending in "-test"
* Find vars to invoke in those namespaces - by default all, unless specific vars are listed, further filtered by include and exclude metadata

### Using Inclusions and Exclusions

You can use inclusions and exclusions to run only a subset of your tests, identified by metadata on the test var.

For example, you could tag your integration tests like so:

```clojure
(deftest ^:integration test-live-system
  (is (= 200 (:status (http/get "http://example.com")))))
```

Then to run only integration tests, you could do one of:

```
clj -X:test :includes '[:integration]'
clj -M:test -i :integration
```

Or to run all tests *except* for integration tests, one of:

```
clj -X:test :excludes '[:integration]'
clj -M:test -e :integration
```

If both inclusions and exclusions are present, exclusions take priority over inclusions.

## Copyright and License

Copyright © 2018-2024 Cognitect, LazyTest integration © 2024 Sean Corfield

Licensed under the Eclipse Public License, Version 2.0
