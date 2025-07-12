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

(defn cmdspec-pos
  "cmdspec with a positional argument."
  []
  {:commands ["run <module>"
              {:command #'identity}]})

(deftest required-flag
  (testing "successful exit"
    (are [input args expected]
        (is (= expected (cli/dispatch* input args)))
      (cmdspec-1 false) []           {:lambdaisland.cli/argv []
                                      :lambdaisland.cli/sources {}}
      (cmdspec-1 true)  ["-x"]       {:lambdaisland.cli/argv [] :x 1
                                      :lambdaisland.cli/sources {:x "-x command line flag"}}
      (cmdspec-n false) ["run"]      {:lambdaisland.cli/argv []
                                      :lambdaisland.cli/command ["run"]
                                      :lambdaisland.cli/sources {}}
      (cmdspec-n true)  ["run" "-x"] {:x 1
                                      :lambdaisland.cli/argv []
                                      :lambdaisland.cli/command ["run"]
                                      :lambdaisland.cli/sources {:x "-x command line flag"}}))

  (testing "help exit"
    (are [input args expected]
        (is (= expected (with-out-str (cli/dispatch* input args))))
      (cmdspec-1 false) ["-h"]        "NAME\n  cli \n\nSYNOPSIS\n  cli [-x] [<args>...]\n\nFLAGS\n  -x,    flag x   \n"
      (cmdspec-1 true)  ["-hx"]       "NAME\n  cli \n\nSYNOPSIS\n  cli [-x] [<args>...]\n\nFLAGS\n  -x,    flag x   (required)\n"
      (cmdspec-n false) ["run" "-h"]  "NAME\n  cli run  ——  Returns its argument.\n\nSYNOPSIS\n  cli run [-x] [<args>...]\n\nFLAGS\n  -x,    flag x   \n"
      (cmdspec-n true)  ["run" "-hx"] "NAME\n  cli run  ——  Returns its argument.\n\nSYNOPSIS\n  cli run [-x] [<args>...]\n\nFLAGS\n  -x,    flag x   (required)\n"))

  (testing "unsuccessful exit"
    (are [input args expected]
        (is (thrown-with-msg? Exception expected (cli/dispatch* input args)))
      (cmdspec-1 true) []      #"Missing required flags: -x"
      (cmdspec-n true) ["run"] #"Missing required flags: -x")))

(deftest positional-argument-with-trailing-arguments
  (let [{::cli/keys [argv]
         :keys [module]} (cli/dispatch* (cmdspec-pos) ["run" "foo" "bar"])]
    (is (= "foo" module))
    (is (= ["foo" "bar"] argv))))
