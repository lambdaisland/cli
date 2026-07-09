This branch (`let-go-compat`) is a quick experiment to see what's needed to make
lambdaisland/cli work with let-go.

Low hanging fruit that should perhaps be best addressed in let-go

- missing `update-keys`
- missing 1-arity (transducer) version of `remove`
- there seems to be a bug in arity detection, `assoc-flag` was defined as

```
(defn assoc-flag 
  ([opts {:keys [middleware handler] :as flagspec} & args]
   ,,,))
```

And that lead to 

```
error: function <mfn assoc-flag 0x16f0f85979b0> doesn't have a 2-arity variant
  --> <default>:196:29

stack trace:
  at assoc-flag (<default>:196:29)
  at reduce (<default>:219:24)
  at handle-flag (<default>:262:36)
  at split-flags (<default>:325:35)
  at dispatch* (<default>:6:7)
```

Removing the extra parentheses around the function tail fixed this, but this
seems to be a let-go bug.

## Regex

There were some differences in regexp handling, which I understand is to be
expected. Go regexp doesn't understand `\R`, I replaced it with `\n` which isn't
quite the same but should work for most cases. This would need closer review, a
more accurate replacement would be something like `(\r\n|\r|\n)`.

It seems in Clojure if an optional capturing group (`(\[no-\])?`) doesn't match
anything, `re-find` yields `nil`, whereas on let-go it yields `""`. Worked
around that with `str/blank?`.

There might be more regexp differences, but basic command and flag support seems
to work with these changes.

## clojure.java.io

I disabled the shell completion support because it requires `clojure.java.io`,
however the only usage patterns are basically `(str (io/file dir path))` for
joining file names, `(spit (io/file ...))`, `(slurp (io/resource))`, and some
operations on `java.io.File` (`.exists`, `.mkdirs`) let-go has a `io/resource`
(but the alias is always there, it can't be required), and it doesn't work with
`slurp` which expects a string for the filename.

Shipping a shim of a subset of `clojure.java.io` / `java.io.File` could go a
long way towards making more clojure code let-go compatible.
