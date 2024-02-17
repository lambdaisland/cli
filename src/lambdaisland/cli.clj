(ns lambdaisland.cli
  (:require [clojure.string :as str]))

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

(defn print-help [cmd-name doc command-pairs flagpairs]
  (let [desc #(str (first (str/split (:doc % "") #"\R"))
                   (when-let [d (:default %)] (str " (default " (pr-str d) ")")))]
    (println (str "Usage: " cmd-name
                  (str/join (for [[_ {:keys [flags argdoc]}] flagpairs]
                              (str " [" (str/join " | " flags) argdoc "]")))
                  (when (seq command-pairs)
                    (str " ["
                         (str/join " | " (map first command-pairs))
                         "]"))
                  " [<args>...]"))
    (println)
    (when doc
      (println doc)
      (println))
    (when (seq flagpairs)
      (let [has-short? (some short? (mapcat (comp :flags second) flagpairs))
            has-long?  (some long? (mapcat (comp :flags second) flagpairs))]
        (print-table
         (for [[_ {:keys [flags argdoc] :as flagopts}] flagpairs]
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
              (desc flagopts)]))))
      (println))
    (print-table
     (for [[cmd cmdopts] command-pairs]
       [(str cmd (if (:commands cmdopts)
                   (str " <" (str/join "|" (map first (:commands cmdopts))) ">")
                   (:argdoc cmdopts)))
        (desc cmdopts)]))))

(defn parse-error! [& msg]
  (throw (ex-info (str/join " " msg) {:type ::parse-error})))

(defn add-middleware [opts {mw :middleware}]
  (let [mw (if (or (nil? mw) (sequential? mw)) mw [mw])]
    (update opts ::middleware (fnil into []) mw)))

(defn assoc-flag
  ([flags flagspec]
   (add-middleware
    (if-let [handler (:handler flagspec)]
      (handler flags)
      (assoc flags (:key flagspec) (:value flagspec)))
    flagspec))
  ([flags flagspec & args]
   (add-middleware
    (if-let [handler (:handler flagspec)]
      (apply handler flags args)
      (assoc flags (:key flagspec)
             (if (= 1 (count args))
               (first args)
               (vec args))))
    flagspec)))

(defn update-flag [flags flagspec f & args]
  (add-middleware
   (if-let [handler (:handler flagspec)]
     (apply handler flags args)
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
       (if-let [{:keys [argcnt value handler middleware parse] :as flagspec
                 :or {parse default-parse}} (get flagmap f)]
         (cond
           (or (nil? argcnt)
               (= 0 argcnt))
           [cli-args args (if (:value flagspec)
                            (assoc-flag flags flagspec)
                            (update-flag flags flagspec (fnil inc 0)))]
           (= 1 argcnt)
           (if arg
             [cli-args args (assoc-flag flags flagspec (parse arg))]
             [(next cli-args) args (assoc-flag flags flagspec (parse (first cli-args)))])
           :else
           [(drop argcnt cli-args) args (assoc-flag flags flagspec (map parse (take argcnt cli-args)))])
         (if strict?
           (parse-error! "Unknown flag: " f)
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
  (if (var? ?var) (assoc (meta ?var) :command ?var) ?var))

(defn prepare-cmdpairs [commands]
  (let [m (if (vector? commands) (apply hash-map commands) commands)]
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
  (into {"--help" {:key :help :value true}}
        (comp
         (mapcat build-flagmap-entries)
         (map (juxt :flag identity)))
        flagpairs))

(defn split-flags [cmdspec cli-args]
  (loop [cmdspec          cmdspec
         [arg & cli-args] cli-args
         args             []
         flags            {}]
    ;; Handle additional flags by nested commands
    (let [extra-flags (cmd->flags cmdspec args)
          flagpairs   (prepare-flagpairs extra-flags)
          flagmap     (parse-flagstrs flagpairs)
          cmdspec     (-> cmdspec
                          (update :flagpairs (fn [fp] (into (vec fp) (remove #((into #{} (map first) fp) (first %))) flagpairs))) ; This prevents duplicates. Yes, this is not pretty. I'm very sorry.
                          (update :flagmap merge flagmap))]

      (cond
        (nil? arg)         [cmdspec args flags]
        (= "--" arg)       [cmdspec (into args cli-args) flags]
        (= \- (first arg)) (let [[cli-args args flags] (handle-flag cmdspec arg cli-args args flags)]
                             (recur cmdspec cli-args args flags))
        :else              (recur cmdspec cli-args (conj args arg) flags)))))

(defn default-flags [flagpairs]
  (reduce (fn [opts flagspec]
            (if-let [d (:default flagspec)]
              (if-let [h (:handler flagspec)]
                (h opts (if (and (string? d) (:parse flagspec))
                          ((:parse flagspec default-parse) d)
                          d))
                (assoc opts (:key flagspec) d))
              opts))
          {}
          (map second flagpairs)))

(defn dispatch
  ([cmdspec]
   (dispatch (to-cmdspec cmdspec) *command-line-args*))
  ([{:keys [flags] :as cmdspec} cli-args]
   (let [flagpairs                (prepare-flagpairs flags)
         flagmap                  (parse-flagstrs flagpairs)
         cmdspec                  (assoc cmdspec :flagpairs flagpairs :flagmap flagmap)
         [cmdspec pos-args flags] (split-flags cmdspec cli-args)
         flagpairs                (get cmdspec :flagpairs)]
     (dispatch (merge (meta (:command cmdspec)) cmdspec)
               pos-args
               (merge (default-flags flagpairs) flags))))
  ([{:keys        [commands doc argnames command flags flagpairs flagmap]
     :as          cmdspec
     program-name :name
     :or          {program-name "cli"}}
    pos-args opts]
   (cond
     command
     (if (:help opts)
       (print-help program-name doc [] flagpairs)
       (binding [*opts* (-> opts
                            (dissoc ::middleware)
                            (assoc ::argv pos-args)
                            (merge (zipmap argnames pos-args)))]
         ((reduce #(%2 %1) command (::middleware opts)) *opts*)))

     commands
     (let [[cmd & pos-args] pos-args
           pos-args         (vec pos-args)
           cmd              (when cmd (first (str/split cmd #"[ =]")))
           opts             (if cmd (update opts ::command (fnil conj []) cmd) opts)
           command-pairs    (prepare-cmdpairs commands)
           command-map      (into {} command-pairs)
           command-match    (get command-map cmd)]

       (cond
         command-match
         (dispatch (assoc (merge (dissoc cmdspec :command :commands) command-match)
                          :name (str program-name " " cmd)) pos-args opts)

         (or (nil? command-match)
             (nil? commands)
             (:help opts))
         (print-help program-name doc (for [[k v] command-pairs]
                                        [k (if (:commands v)
                                             (update v :commands prepare-cmdpairs)
                                             v)])
                     flagpairs)

         :else
         (parse-error! "Expected either :command or :commands key in" cmdspec))))))


;;
;; ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
;; lambdaisland/cli aka the futility of ergonomics — by Arne Brasseur
;; ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
;;
;; Conceived and created in Marrakech and Setti Fatma, February 2024.
;;
;; Dedicated to my father.
;;
