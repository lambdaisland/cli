(ns lambdaisland.cli
  (:require [clojure.string :as str]
            [clojure.set :as set]))

;; I've tried to be somewhat consistent with variable naming

;; - cmdspec: the map passed into dispatch, with :commands and :flags, possibly augmented with :flagspecs
;; - cli-args: the vector of cli arguments as the come in, or the tail of it if part has been processed
;; - flagspecs: map of the possible flags with metadata, expanded to serve direct lookup, e.g. {"-i" {,,,} "--input" {,,,} "--no-input" {,,,}}
;; - flagspec: map of how to deal with a given flag {:flag "--foo", :key :foo, :short? false, :argcnt 1}
;; - flagstr: string representation of a flagspec, e.g. "-i, --input FILE"
;; - argcnt: number of arguments a given flag consumes (usually zero or one, but could be more)
;; - args/pos-args: vector of positional arguments that will go to the command
;; - opts: options that will go the command, based on any parsed flags
;; - commands: specification of (sub)-commands, can be vector (for order) or map
;; - raw-flagspecs: flags as specified in the cmdspec, without normalization
;; - cmd: a single (sub) command like `"add"` or `"widgets"`

(def ^:dynamic *opts* nil)

(defn transpose [m]
  (apply mapv vector m))

(defn print-table
  ([rows]
   (print-table rows nil))
  ([rows {:keys [pad] :or {pad 2}}]
   (when (seq rows)
     (let [col-widths (map #(apply max (map count %)) (transpose rows))
           fstr (str/join "   " (map #(str  "%" (if (= 0 %) "" (- %))  "s") col-widths))]
       (doseq [row rows]
         (println (apply format (str (format (str "%" pad "s") "") fstr) row)))))))

(defn coerce-to-pairs [o]
  (if (vector? o)
    (partition 2 o)
    o))

(defn short? [f]
  (re-find #"^-[^-]$" f))

(defn long? [f]
  (re-find #"^--.*" f))

(defn print-help [cmd-name doc command-pairs argnames flagpairs]
  (let [desc #(str (first (str/split (:doc % "") #"\R"))
                   (when-let [d (:default %)] (str " (default " (pr-str d) ")")))]
    (println "NAME")
    (println " " cmd-name " — " (first (str/split doc #"\R")))
    (println)

    (println "SYNOPSIS")
    (println (str "  " cmd-name
                  (when (seq argnames)
                    (str/join (for [n argnames]
                                (str " <" (name n) ">"))))
                  (str/join (for [[_ {:keys [flags argdoc]}] flagpairs]
                              (str " [" (str/join " | " flags) argdoc "]")))
                  (when (seq command-pairs)
                    (str " ["
                         (str/join " | " (map first command-pairs))
                         "]"))
                  " [<args>...]"))
    (println)
    (when-let [doc (next (str/split doc #"\R"))]
      (println "DESCRIPTION")
      (println (str/join "\n" (drop-while #(str/blank? %) doc)))
      (println))
    (when (seq flagpairs)
      (let [has-short? (some short? (mapcat (comp :flags second) flagpairs))
            has-long?  (some long? (mapcat (comp :flags second) flagpairs))]
        (println "FLAGS")
        (print-table
         (for [[_ {:keys [flags argdoc required] :as flagopts}] flagpairs]
           (let [short (some short? flags)
                 long (some long? flags)]
             [(str (cond
                     short
                     (str short ", ")
                     has-short?
                     "    "
                     :else
                     "")
                   long
                   argdoc)
              (desc flagopts)
              (if required "(required)" "")
              ]))))
      (println))
    (when (seq command-pairs)
      (println "POSITIONAL ARGUMENTS")
      (print-table
       (for [[cmd cmdopts] command-pairs]
         [(str cmd (if (:commands cmdopts)
                     (str " <" (str/join "|" (map first (:commands cmdopts))) ">")
                     (:argdoc cmdopts)))
          (desc cmdopts)])))))

(defn parse-error! [& msg]
  (throw (ex-info (str/join " " msg) {:type ::parse-error})))

(defn add-middleware [opts {mw :middleware}]
  (let [mw (if (or (nil? mw) (sequential? mw)) mw [mw])]
    (update opts ::middleware (fnil into []) mw)))

(defn call-handler [handler flags & args]
  (binding [*opts* flags]
    (apply handler flags args)))

(defn assoc-flag
  ([flags flagspec]
   (add-middleware
    (if-let [handler (:handler flagspec)]
      (call-handler handler flags)
      (assoc flags (:key flagspec) (:value flagspec)))
    flagspec))
  ([flags flagspec & args]
   (add-middleware
    (if-let [handler (:handler flagspec)]
      (apply call-handler handler flags args)
      (assoc flags (:key flagspec)
             (if (= 1 (count args))
               (first args)
               (vec args))))
    flagspec)))

(defn update-flag [flags flagspec f & args]
  (add-middleware
   (if-let [handler (:handler flagspec)]
     (apply call-handler handler flags args)
     (apply update flags (:key flagspec) f args))
   flagspec))

(defn default-parse [s]
  (cond
    (re-find #"^-?\d+$" s)
    (parse-long s)
    (re-find #"^-?\d+\.\d+$" s)
    (parse-double s)
    :else
    s))

(defn handle-flag [{:keys [flagmap strict?] :as cmdspec} flag cli-args args flags]
  (reduce
   (fn [[cli-args args flags] f]
     (let [[f arg] (str/split f #"=")]
       (if-let [{:keys [argcnt flagstr value handler middleware parse] :as flagspec
                 :or {parse default-parse}} (get flagmap f)]
         (cond
           (or (nil? argcnt)
               (= 0 argcnt))
           [cli-args args (if (:value flagspec)
                            (assoc-flag flags flagspec)
                            (update-flag flags flagspec (fnil inc 0)))]
           (= 1 argcnt)
           (cond
             arg
             [cli-args args (assoc-flag flags flagspec (parse arg))]
             (first cli-args)
             [(next cli-args) args (assoc-flag flags flagspec (parse (first cli-args)))]
             :else
             (parse-error! flagstr "expects an argument, but no more positional arguments available."))
           :else
           [(drop argcnt cli-args) args (assoc-flag flags flagspec (map parse (take argcnt cli-args)))])
         (if strict?
           (parse-error! "Unknown flag:" f)
           [cli-args args (update-flag flags {:key (keyword (str/replace f #"^-+" ""))} #(or arg ((fnil inc 0) %)))]))))
   [cli-args args flags]
   (if (re-find #"^-\w+" flag)
     (map #(str "-" %) (next flag))
     [flag])))

(def args-re #" ([A-Z][A-Z_-]*)|[= ]<([^>]+)>")
(def flag-re #"^(--?)(\[no-\])?(.+)$")

(defn parse-arg-names [str]
  [(str/split (str/replace str args-re "") #",\s*")
   (str/join (map first (re-seq args-re str)))
   (mapv (fn [[_ u l]] (keyword (str/lower-case (or u l)))) (re-seq args-re str))])

(defn to-cmdspec [?var]
  (cond
    (var? ?var)
    (assoc (meta ?var) :command ?var)

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
      (cmd->flags (get (into {} (prepare-cmdpairs (:commands cmdspec)))
                       (first args))
                  (rest args)))
    (:flags cmdspec)))

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

(defn prepare-flagpairs [flagstrs]
  (when (seq flagstrs)
    (map (fn [[flagstr flagopts]]
           [flagstr (parse-flagstr flagstr (if (string? flagopts) {:doc flagopts} flagopts))])
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
        (assoc :value (not negative?))
        :->
        (merge flagopts)))))

(defn parse-flagstrs [flagpairs]
  (into {"-h" {:key :help :value true}
         "--help" {:key :help :value true}}
        (comp
         (mapcat build-flagmap-entries)
         (map (juxt :flag identity)))
        flagpairs))

(defn add-defaults [init flagpairs]
  (reduce (fn [opts flagspec]
            (if-let [d (:default flagspec)]
              (if-let [h (:handler flagspec)]
                (binding [*opts* opts]
                  (h opts (if (and (string? d) (:parse flagspec))
                            ((:parse flagspec default-parse) d)
                            d)))
                (assoc opts (:key flagspec) d))
              opts))
          init
          (map second flagpairs)))

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

(defn split-flags
  "Main processing loop, go over raw arguments, split into positional and flags,
  building up an argument vector, and flag/options map."
  [cmdspec cli-args init]
  (loop [cmdspec          cmdspec
         [arg & cli-args] cli-args
         args             []
         seen-prefixes    #{}
         flags            init]
    ;; Handle additional flags by nested commands
    (let [extra-flags (when-not (seen-prefixes args)
                        (cmd->flags cmdspec args))
          flags       (add-defaults flags (prepare-flagpairs extra-flags))
          cmdspec     (add-processed-flags cmdspec extra-flags)]
      (cond
        (nil? arg)
        [cmdspec args flags]

        (= "--" arg)
        [cmdspec (into args cli-args) flags]

        (and (= \- (first arg))
             (not= 1 (count arg))) ; single dash is considered a positional argument
        (let [[cli-args args flags] (handle-flag cmdspec arg cli-args args flags)]
          (recur (dissoc cmdspec :flags) cli-args args (conj seen-prefixes args) flags))

        :else
        (recur (dissoc cmdspec :flags)
               cli-args
               (conj args (str/replace arg #"^\\(.)" (fn [[_ o]] o)))
               (conj seen-prefixes args)
               flags)))))

(defn missing-flags
  "Return a set of required flags in `flagmap` not present in `opts`, or `nil` if
  all required flags are present."
  [flagmap opts]
  (let [required    (->> flagmap vals (filter (comp true? :required)) (map :key) set)
        received    (->> opts keys set)
        missing (map (fn [key]
                       (->> flagmap vals (map #(vector (:key %) (:flags %))) (into {}) key))
                     (set/difference required received))]
    (seq missing)))

(defn dispatch*
  ([cmdspec]
   (dispatch* (to-cmdspec cmdspec) *command-line-args*))
  ([{:keys [flags init] :as cmdspec} cli-args]
   (let [init                     (if (or (fn? init) (var? init)) (init) init)
         [cmdspec pos-args flags] (split-flags cmdspec cli-args init)
         flagpairs                (get cmdspec :flagpairs)]
     (dispatch* cmdspec pos-args flags)))
  ;; Note: this three-arg version of dispatch* is considered private, it's used
  ;; for internal recursion on subcommands.
  ([{:keys        [commands doc argnames command flags flagpairs flagmap]
     :as          cmdspec
     program-name :name
     :or          {program-name "cli"}}
    pos-args opts]

   (cond
     command
     (if (:help opts)
       (print-help program-name doc [] argnames flagpairs)
       (binding [*opts* (-> opts
                            (dissoc ::middleware)
                            (update ::argv (fnil into []) pos-args)
                            (merge (zipmap argnames pos-args)))]
         (if-let [missing (missing-flags flagmap opts)]
           (parse-error! "Missing required flags:" (->> missing (map #(str/join " " %)) (str/join ", ")))
           ((reduce #(%2 %1) command (::middleware opts)) *opts*))))

     commands
     (let [[cmd & pos-args] pos-args
           pos-args         (vec pos-args)
           cmd              (when cmd (first (str/split cmd #"[ =]")))
           opts             (if cmd (update opts ::command (fnil conj []) cmd) opts)
           command-pairs    (prepare-cmdpairs commands)
           command-map      (update-keys (into {} command-pairs)
                                         #(first (str/split % #"[ =]")))
           command-match    (get command-map cmd)
           argnames         (:argnames command-match)
           arg-count        (count argnames)]
       (cond
         (and command-match
              (<= arg-count (count pos-args)))
         (dispatch*
          (-> cmdspec
              (dissoc :command :commands)
              (merge command-match)
              (assoc :name (str program-name " " cmd)))
          (drop arg-count pos-args)
          (-> opts
              (update ::argv (fnil into []) (take arg-count pos-args))
              (merge (zipmap argnames pos-args))))

         (or (nil? command-match)
             (nil? commands)
             (:help opts)
             (< (count pos-args) arg-count))
         (do
           (cond
             (and cmd (or (nil? command-match) (nil? commands)))
             (println "No matching command found:" cmd "\n")
             (< (count pos-args) arg-count)
             (println "Positional arguments missing:"
                      (->> argnames
                           (drop (count pos-args))
                           (map #(str "<" (name %) ">"))
                           (str/join " "))
                      "\n"))
           (if cmd
             (print-help (str program-name " " cmd)
                         (if command-match
                           (:doc command-match)
                           doc)
                         (for [[k v] (if command-match
                                       (-> command-match :commands prepare-cmdpairs)
                                       command-pairs)]
                           [k (if (:commands v)
                                (update v :commands prepare-cmdpairs)
                                v)])
                         argnames
                         flagpairs)
             (print-help program-name
                         doc
                         (for [[k v] command-pairs]
                           [k (if (:commands v)
                                (update v :commands prepare-cmdpairs)
                                v)])
                         argnames
                         flagpairs)))

         :else
         (parse-error! "Expected either :command or :commands key in" cmdspec))))))

(defn dispatch
  "Main entry point for com.lambdaisland/cli.

  Takes either a single var, or a map describing the commands and flags that
  your CLI tool accepts. At a minimum it should contain either a `:command` or
  `:commands`, optionally followed by a vector of positional command line
  arguments (this second argument can generally be omitted, since we can access
  these through [[*command-line-args*]]).

  - `:name` Name of the script/command as used in the shell, used in the help text
  - `:command` Function that implements your command logic, receives a map of
    parsed CLI args. Can be a var, in which case additional configuration can be
    done through var metadata.
  - `:commands` Map or flat vector of command-string command-map pairs
  - `:doc` Docstring, taken from `:command` if it is a var.
  - `:flags` Map or flat vector of flag-string flag-map
  - `:argnames` Vector of positional argument names, only needed on the top
    level, for subcommands use the command-string to specify these.
  - `:init` map or zero-arity function that provides the base options map, that
    parsed flags and arguments are added onto

  These flags can also be used in (sub)command maps, with the exception of
  `:name`, `:argnames`, and `:init`.

  A command-string consists of the name of the command, optionally followed by
  any named positional argument, either in all-caps, or delineated by angle
  brackets, e.g. `create <id> <name>` or `delete ID`.

  A flag-string consists of command separated short (single-dash) or
  long (double-dash) flags, optionally followed by an argument name, either in
  all-caps, or delineated by angle brackets. The flag and argument are separated
  by either a space or an equals sign. e.g. `--input=<filename>`, `-o, --output
  FILENAME`.

  Flag-maps can contain
  - `:doc` Docstring, used in the help text
  - `:parse` Function that parses/coerces the flag argument from string.
  - `:default` Default value, gets passed through `:parse` if it's a string.
  - `:handler` Function that transforms the options map when this flag is
    present. Zero-arity for boolean (no-argument) flag, one-arity for flags that
    take an argument.
  - `:middleware` Function or sequence of functions that will wrap the command
    function if this flag is present.
  - `:required` Boolean value to indicate if the flag is required.

  This docstring is just a summary, see the `com.lambdaisland/cli` README for
  details.
   "
  [& args]
  (try
    (apply dispatch* args)
    (catch Exception e
      (binding [*out* *err*]
        (println "[FATAL]" (.getMessage e))
        (if-let [d (ex-data e)]
          (clojure.pprint/pprint d)))
      (System/exit (:exit (ex-data e) 1)))))


;;
;; ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
;; lambdaisland/cli aka the futility of ergonomics — by Arne Brasseur
;; ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
;;
;; Conceived and created in Marrakech and Setti Fatma, February 2024.
;;
;; Dedicated to my father.
;;
