(ns lambdaisland.cli-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer :all]
   [lambdaisland.cli :as cli :refer :all]))

(deftest flagstr-parsing-test
  (is (= {"--help"              {:key :help :value true}
          "-C"                  {:flag        "-C"
                                 :key         :C
                                 :argcnt      0
                                 :short?      true
                                 :description "Change working directory"}
          "-v"                  {:flag        "-v"
                                 :key         :verbose
                                 :argcnt      0
                                 :short?      true
                                 :description "Increase verbosity"}
          "--verbose"           {:flag        "--verbose"
                                 :key         :verbose
                                 :argcnt      0
                                 :description "Increase verbosity"}
          "-i"                  {:flag        "-i"
                                 :key         :input
                                 :argcnt      1
                                 :short?      true
                                 :args        [:input-file]
                                 :description "Set input file"}
          "--input"             {:flag        "--input"
                                 :key         :input
                                 :argcnt      1
                                 :args        [:input-file]
                                 :description "Set input file"}
          "--output"            {:flag        "--output"
                                 :key         :output
                                 :argcnt      1
                                 :args        [:output-file]
                                 :description "Set output file"}
          "--capture-output"    {:flag        "--capture-output"
                                 :key         :capture-output
                                 :argcnt      0
                                 :value       true
                                 :description "Enable/disable output capturing"}
          "--no-capture-output" {:flag        "--no-capture-output"
                                 :key         :capture-output
                                 :argcnt      0
                                 :value       false
                                 :description "Enable/disable output capturing"}}
         (parse-flagstrs ["-C" "Change working directory"
                          "-v, --verbose" "Increase verbosity"
                          "-i, --input INPUT-FILE" {:description "Set input file"}
                          "--output=<output-file>" {:description "Set output file"}
                          "--[no-]capture-output" "Enable/disable output capturing"] ))))

(deftest command-argument-parsing
  (is (= {"run" {:description "Run the thing" :argnames []}
          "remove" {:description "remove with id" :argnames [:id]}
          "add" {:description "Add with id" :argnames [:id]}}
         (prepare-cmdmap ["run" {:description "Run the thing"}
                          "add ID" {:description "Add with id"}
                          "remove <id>" {:description "remove with id"}]))))
