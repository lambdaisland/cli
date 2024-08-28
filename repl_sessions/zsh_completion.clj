(ns zsh-completion)

#!/usr/bin/env bb
(ns platform.bin.complete
  (:require
   [clojure.string :as str]
   [lambdaisland.cli :as cli]))

(defn foo
  "Do something"
  [opts]
  )

(def commands
  ["foo" #'foo
   ])

(def flags
  ["-v, --verbose" "Increase verbosity"
   "-n, --dry-run" "Show shell commands, but don't execute them"])

(let [argcnt  (parse-long (first *command-line-args*))
      args    (next *command-line-args*)
      cmdspec (cli/to-cmdspec
               {:name     "bin/dev"
                :commands commands
                :flags    flags})
      commands (apply array-map (:commands cmdspec))
      flags (apply array-map (:flags cmdspec))]
  (println "local _desc _vals")
  (println (str "_desc="
                (pr-str
                 (for [[cmd {:keys [doc]}] commands]
                   (str cmd " -- " (if doc (first (str/split doc #"\R")) "foo"))))
                #_
                (str/replace
                 #"\"" "'")))
  (println (str "_vals="
                (pr-str
                 (for [[cmd _] commands]
                   cmd))
                #_(str/replace

                   #"\"" "'")))
  )

;; _bin_dev_completions() {
;;   eval $($service --zsh-completion ${#words} $words)
;;   compadd -d _desc -a _vals
;; }
