(ns lambdaisland.cli-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer :all]
   [lambdaisland.cli :as cli]))

(defn cmdspec-1
  "Builds a cmdspec with a single command."
  [required]
  {:command #'identity
   :flags   ["-x" {:doc      "flag x"
                   :required required}]})

(defn cmdspec-n
  "Builds a cmdspec with multiple commands."
  [required]
  {:commands ["run" {:command #'identity
                     :flags   ["-x" {:doc      "flag x"
                                     :required required}]}]})

(deftest required-flag
  (testing "successful exit"
    (are [input args expected]
        (is (= expected (cli/dispatch* input args)))
      (cmdspec-1 false) []           {:lambdaisland.cli/argv []}
      (cmdspec-1 true)  ["-x"]       {:lambdaisland.cli/argv [] :x 1}
      (cmdspec-n false) ["run"]      {:lambdaisland.cli/argv [] :lambdaisland.cli/command ["run"]}
      (cmdspec-n true)  ["run" "-x"] {:lambdaisland.cli/argv [] :lambdaisland.cli/command ["run"] :x 1}))

  (testing "help exit"
    (are [input args expected]
        (is (= expected (with-out-str (cli/dispatch* input args))))
      (cmdspec-1 false) ["-h"]        "Usage: cli [-x] [<args>...]\n\n  -x,    flag x   \n\n"
      (cmdspec-1 true)  ["-hx"]       "Usage: cli [-x] [<args>...]\n\n  -x,    flag x   (required)\n\n"
      (cmdspec-n false) ["run" "-h"]  "Usage: cli run [-x] [<args>...]\n\nReturns its argument.\n\n  -x,    flag x   \n\n"
      (cmdspec-n true)  ["run" "-hx"] "Usage: cli run [-x] [<args>...]\n\nReturns its argument.\n\n  -x,    flag x   (required)\n\n"))

  (testing "unsuccessful exit"
    (are [input args expected]
        (is (thrown-with-msg? Exception expected (cli/dispatch* input args)))
      (cmdspec-1 true) []      #"Missing required flags: -x"
      (cmdspec-n true) ["run"] #"Missing required flags: -x")))
