(ns test-scratch
  (:require  [clojure.test :as t]))



(defmacro def-are [name & pairs]
  `(deftest ~name
     ~@(for [[a b] (partition 2 pairs)]
         (is (= ~b ~a)))))

(set! *print-namespace-maps* false)

(def last-flags (atom nil))

(defn capture-cmd [flags]
  (reset! last-flags flags))

(defn cli [cli-args cmdspec]
  (reset! last-flags :not-called)
  (let [out (with-out-str
              (dispatch cmdspec cli-args))]
    (if (seq out)
      [@last-flags out]
      @last-flags)))

(defn lines [& args]
  (str/join "\n" args))

(def cmds-run ["run" {:description "Do the thing"
                      :command capture-cmd}])

(def flags-input ["-i,--input FILE" "Specify input file"])

(def-are base-case
  (cli ["run"] {:commands cmds-run})
  {:lambdaisland.cli/command ["run"], :lambdaisland.cli/argv []})

(def-are help-rendering
  (cli [] {:commands cmds-run})
  [:not-called
   (lines "Usage: cli [command...] [flags-or-args...]"
          ""
          "  run   Do the thing"
          "")]

  (cli ["help"] {:commands cmds-run})
  [:not-called
   (lines "Usage: cli [command...] [flags-or-args...]"
          ""
          "  run   Do the thing"
          "")]

  (cli ["help"] {:commands cmds-run})
  [:not-called
   (lines "Usage: cli [command...] [flags-or-args...]"
          ""
          "  run   Do the thing"
          "")]
  (cli ["--help"] {:commands cmds-run
                   :flags flags-input})
  [:not-called
   (lines "Usage: cli [command...] [flags-or-args...]"
          ""
          "  -i,--input FILE   Specify input file"
          ""
          "  run   Do the thing"
          "")])

(def-are arguments
  (cli ["run" "hello"] {:commands cmds-run})
  {:lambdaisland.cli/command ["run"]
   :lambdaisland.cli/argv ["hello"]})

(deftest subcommands
  (let [cmdspec {:commands {"run" {:command capture-cmd
                                   :description "Do something"}
                            "widget" {:description "Work with widgets"
                                      :commands
                                      ["ls" {:description "List widgets"
                                             :command capture-cmd}
                                       "add" {:description "Add widget"
                                              :command capture-cmd}]}}}]
    (is (= [["x"] {::cli/command ["widget" "ls"]}]
           (cli ["widget" "ls" "x"] cmdspec)))))

(deftest flags
  (is (= [nil {:input "INPUT"
               :output "OUTPUT"
               ::cli/command ["run"]}]
         (cli
          ["run" "--input" "INPUT" "--output" "OUTPUT"]
          {:commands {"run" {:command capture-cmd
                             :description "Do something"}}
           :flags ["-i,--input FILE" {:desc "Specify input file"}
                   "--output FILE" "Specify output file"]}))))

(deftest parse-flagstr-test
  (testing "single short flag"
    (is (= {:flagstr     "-i"
            :flags       ["-i"]
            :description "Specify input file"
            :argdoc      ""
            :key         :i
            :argcnt      0
            :args        []}
           (parse-flagstr "-i" {:description "Specify input file"}))))
  (testing "single long flag"
    (is (= {:flagstr     "--input"
            :flags       ["--input"]
            :description "Specify input file"
            :argdoc      ""
            :key         :input
            :argcnt      0
            :args        []}
           (parse-flagstr "--input" {:description "Specify input file"}))))

  (testing "short and long - with argument"
    (is (= [
            {:flag        "-i"
             :short?      true
             :args        '(:file)
             :key         :input
             :argcnt      1
             :description "Specify input file"}
            {:flag        "--input"
             :short?      false
             :args        '(:file)
             :key         :input
             :argcnt      1
             :description "Specify input file"}]
           (build-flagmap-entries ["-i,--input FILE" {:description "Specify input file"}]))))

  (testing "override key, value"
    (is (= [{:flag        "--foo"
             :short?      false
             :args        ()
             :key         :bar
             :argcnt      0
             :description "Do a foo"
             :value       123}]
           (build-flagmap-entries
            ["--foo" {:description "Do a foo"
                      :key         :bar
                      :value       123}]))))

  (testing "--[no-]"
    (is (= [{:flag        "--no-foo"
             :short?      false
             :args        ()
             :key         :foo
             :argcnt      0
             :value       false
             :description "Enable/disable foo"}
            {:flag        "--foo"
             :short?      false
             :args        ()
             :key         :foo
             :argcnt      0
             :value       true
             :description "Enable/disable foo"}]
           (build-flagmap-entries ["--[no-]foo" {:description "Enable/disable foo"}])))))

(deftest parse-arg-names-test
  (is (= '[["-i"] "" ()]                     (parse-arg-names "-i")))
  (is (= '[["-i"] " FOO BAR" (:foo :bar)]    (parse-arg-names "-i FOO BAR")))
  (is (= '[["-i" "--input"] "=<foo>" (:foo)] (parse-arg-names "-i, --input=<foo>")))
  (is (= '[["cmd"] " FILE" (:file)]          (parse-arg-names "cmd FILE")))
  (is (= '[["cmd"] " <file>" (:file)]        (parse-arg-names "cmd <file>"))))

(mapcat build-flagmap-entries
        (coerce-to-pairs ["-i, --input ARG" {:description "foo"}]))
(parse-flagstrs ["-i, --input, --[no-]foo ARG" {:description "foo"}])
(parse-flagstrs ["-i, --input, --[no-]foo=<arg>" {:description "foo"}])
(build-flagmap-entries ["-i, --input ARG" {:description "foo"}])


;; (let [cmdspec
;;       {:commands {"run" {:command (show-args "run")
;;                          :description "Do something"}
;;                   "widget" {:description "Work with widgets"
;;                             :commands
;;                             ["ls" {:description "List widgets"
;;                                    :command (show-args "widget ls")}
;;                              "add" {:description "Add widget"
;;                                     :command (show-args "widget add")}]}}
;;        :flags ["-i,--input FILE" {:desc "Specify input file"
;;                                   :key "XXX"}
;;                "--output FILE" "Specify output file"]}]
;;   (split-flags
;;    (assoc cmdspec :flagspecs (parse-flagstrs (:flags cmdspec)))
;;    ["widget" "ls" "--help" "--input" "INPUT" "--output" "OUTPUT"]))

;; (dispatch
;;  {:commands {"run" {:command (show-args "run")
;;                     :description "Do something"}
;;              "widget" {:description "Work with widgets"
;;                        :commands
;;                        ["ls" {:description "List widgets"
;;                               :command (show-args "widget ls")
;;                               :help "List widgets in the order they exist."}
;;                         "add" {:description "Add widget"
;;                                :command (show-args "widget add")}]}}
;;   :flags ["-i,--input FILE" {:desc "Specify input file"}
;;           "--output FILE" "Specify output file"]}
;;  ["widget" "ls" "--help"])

;; (cli/dispatch
;;  {:commands
;;   ["run"
;;    {:description "Run the thing"
;;     :command (fn [args flags]
;;                ,,,)}

;;    "widgets"
;;    {:description "Work with widgets"
;;     :subcommands
;;     ["ls"
;;      {:description "List widgets"
;;       :command (fn [args flags] ,,,)}
;;      "create NAME"
;;      {:description "Create a new widget"
;;       :command (fn [args flags] ,,,)}
;;      "delete ID"
;;      {:description "Delete the widget with the given ID"
;;       :command (fn [args flags] ,,,)}]}]

;;   :flags
;;   ["-v,--verbose" {:description "Increase verbosity"}]})
