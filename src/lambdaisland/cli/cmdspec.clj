(ns lambdaisland.cli.cmdspec
  "Some of the command spec parsing extracted so it can be reused in
  lambdaisland.cli.completions"
  (:require
   [clojure.string :as str]))

(def args-re #" ([A-Z][A-Z_-]*)|[= ]<([^>]+)>(\.\.\.)?")

(defn parse-arg-names [str]
  [(str/split (str/replace str args-re "") #",\s*")
   (str/join (map first (re-seq args-re str)))
   (mapv (fn [[_ u l more]] (let [arg-kw (keyword (str/lower-case (or u l)))]
                              (if more
                                [:& arg-kw]
                                arg-kw))) (re-seq args-re str))])

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

(defn cmd->flags [cmdspec args]
  (if (seq args)
    (when-let [cmds (:commands cmdspec)]
      (recur (get (into {} (prepare-cmdpairs (:commands cmdspec)))
                  (first args))
             (rest args)))
    (:flags cmdspec)))

(def flag-re #"^(--?)(\[no-\])?(.+)$")

(defn parse-flagstr [flagstr flagopts]
  (let [;; support "--foo=<hello>" and "--foo HELLO"
        [flags argdoc argnames] (parse-arg-names flagstr)
        argcnt                  (count argnames)
        ;; e.g. "-i,--input, --[no-]foo ARG"
        flagstrs                (map #(re-find flag-re %) flags)
        flag-key                (keyword (or (some (fn [[_ dash no flag]]
                                                     (when (= "--" dash) flag))
                                                   flagstrs)
                                             (last (first flagstrs))))]
    (merge {:flagstr flagstr
            :argdoc  argdoc
            :flags   flags
            :args    argnames
            :key     flag-key
            :argcnt  argcnt}
           flagopts)))

(defn coerce-to-pairs [o]
  (if (vector? o)
    (partition 2 o)
    o))

(defn prepare-flagpairs [flagstrs]
  (when (seq flagstrs)
    (map (fn [[flagstr flagopts]]
           (let [flagopts (if (var? flagopts)
                            {:doc (:doc (meta flagopts))
                             :handler flagopts}
                            flagopts)]
             [flagstr (parse-flagstr flagstr (if (string? flagopts) {:doc flagopts} flagopts))]))
         (coerce-to-pairs flagstrs))))

(defn build-flagmap-entries [[flagstr flagopts]]
  (let [{:keys [args key argcnt flags] :as parsed-flagstr} flagopts
        flags                                              (map #(re-find flag-re %) flags)]
    (for [[_ dash no flag] flags
          negative?        (if no [true false] [false])]
      (cond-> {:flag   (str dash (if negative? "no-") flag)
               :key    key
               :argcnt argcnt}
        (= dash "-")
        (assoc :short? true)
        (seq args)
        (assoc :args args)
        no
        (assoc :value (not negative?)
               :boolean? true)
        :->
        (merge flagopts)))))

(defn parse-flagstrs [flagpairs]
  (into {"-h" {:doc "Show help text" :key :help :value true}
         "--help" {:doc "Show help text" :key :help :value true}}
        (comp
         (mapcat build-flagmap-entries)
         (map (juxt :flag identity)))
        flagpairs))

(defn add-processed-flags
  "We process flag information for easier use, this results in
  `:flagpairs` (ordered sequence of pairs, mainly used in printing help
  information), and `:flagmap` (for easy lookup), added to the `cmdspec`. As we
  process arguments we may need to add additional flags, based on the current
  subcommand. This function is used both for the top-level as for subcommand
  handling of flags."
  [cmdspec extra-flags]
  (let [flagpairs (prepare-flagpairs extra-flags)
        flagmap   (parse-flagstrs flagpairs)]
    (-> cmdspec
        (update :flagpairs (fn [fp]
                             (into (vec fp)
                                   ;; This prevents duplicates. Yes, this is not pretty. I'm very sorry.
                                   (remove #((into #{} (map first) fp) (first %)))
                                   flagpairs)))
        (update :flagmap merge flagmap))))
