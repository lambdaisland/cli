(ns lambdaisland.cli.cmdspec
  "Some of the command spec parsing extracted so it can be reused in
  lambdaisland.cli.completions"
  (:require
   [clojure.string :as str]))

(def args-re #" ([A-Z][A-Z_-]*)|[= ]<([^>]+)>")

(defn parse-arg-names [str]
  [(str/split (str/replace str args-re "") #",\s*")
   (str/join (map first (re-seq args-re str)))
   (mapv (fn [[_ u l]] (keyword (str/lower-case (or u l)))) (re-seq args-re str))])

(defn to-cmdspec [?var]
  (cond
    (var? ?var)
    (if (fn? @?var)
      (assoc (meta ?var) :command ?var)
      (merge (meta ?var) @?var))

    (fn? ?var)
    {:command ?var}

    (var? (:command ?var))
    (merge (meta (:command ?var)) ?var)

    :else
    ?var))

(defn prepare-cmdpairs [commands]
  (let [m (if (vector? commands) (partition 2 commands) commands)]
    (map (fn [[k v]]
           (let [v (to-cmdspec v)
                 [[cmd] doc argnames] (parse-arg-names k)]
             [cmd (assoc v :argdoc doc :argnames argnames)]))
         m)))
