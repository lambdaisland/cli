(ns lambdaisland.cli-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer :all]
   [lambdaisland.cli :as cli :refer :all]))

(set! *print-namespace-maps* false)

(def last-args (atom nil))
(def last-flags (atom nil))

(defn capture-cmd [args flags]
  (reset! last-args args)
  (reset! last-flags flags))

(defn do-dispatch [cli-args cmdspec]
  (reset! last-args :not-called)
  (reset! last-flags :not-called)
  (let [out (with-out-str
              (dispatch cmdspec cli-args))]
    (if (seq out)
      [@last-args @last-flags out]
      [@last-args @last-flags])))

(defn lines [& args]
  (str/join "\n" args))

(deftest base-case
  (is (= [nil {::cli/command ["run"]}]
         (do-dispatch
          ["run"]
          {:commands
           ["run" {:command capture-cmd}]}))))


(deftest help
  (let [cmdspec {:commands
                 ["run" {:description "Do the thing"
                         :command capture-cmd}]}
        help-out [:not-called
                  :not-called
                  (lines  "Usage: cli [command...] [flags-or-args...]"
                          ""
                          "  run   Do the thing"
                          "")]]
    (is (= help-out (do-dispatch [] cmdspec)))
    (is (= help-out (do-dispatch ["help"] cmdspec)))
    (is (= help-out (do-dispatch ["--help"] cmdspec)))))

(deftest arguments
  (testing "remaining positional arguments are passed to the command"
    (is (= [["hello"] {::cli/command ["run"]}]
           (do-dispatch
            ["run" "hello"]
            {:commands ["run" {:command capture-cmd}]})))))

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
           (do-dispatch ["widget" "ls" "x"] cmdspec)))

    #_
    (is (= [["x"] {::cli/command ["widget" "ls"]}]
           (do-dispatch ["widget" "ls" "--help"] cmdspec)))))

(deftest flags
  (is (= [nil {:input "INPUT"
               :output "OUTPUT"
               ::cli/command ["run"]}]
         (do-dispatch
          ["run" "--input" "INPUT" "--output" "OUTPUT"]
          {:commands {"run" {:command capture-cmd
                             :description "Do something"}}
           :flags ["-i,--input FILE" {:desc "Specify input file"}
                   "--output FILE" "Specify output file"]}))))


;; (parse-flagspec
;;  "-i,--input FILE" {:desc "Specify input file"})

;; (parse-flagspecs
;;  ["-i,--input FILE" {:desc "Specify input file"}
;;   "--output FILE" "Specify output file"
;;   "--[no-]foo" ""])

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
;;    (assoc cmdspec :flagspecs (parse-flagspecs (:flags cmdspec)))
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
