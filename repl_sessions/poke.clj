(ns poke
  (:require
   [lambdaisland.cli :as cli]))

(def ^:dynamic *v* "o")

(defn make-mw [ch]
  (fn [cmd]
    (fn [opts]
      (prn ch opts)
      (cmd (assoc opts ch true))
      #_
      (binding [*v* (str ch *v* ch)]
        (cmd opts)))))

(def commands
  ["xxx"
   {:middleware [(make-mw "x")]
    :flags ["--bar XXX" {:middleware [(make-mw "b")]}]
    :commands
    ["yyy"
     {:middleware [(make-mw "y")]
      :command
      (fn [opts]
        (prn *v*)
        (prn opts))
      }  ]}])

(cli/dispatch*
 {:commands commands
  :flags ["--foo" {:middleware [(make-mw "f")]}]
  :middleware [(make-mw "*")]}
 ["--foo" "xxx" "yyy"  "--bar" "zzz"])
