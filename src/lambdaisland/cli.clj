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

(defn transpose [m]
  (apply mapv vector m))

(defn print-table
  ([rows]
   (print-table rows nil))
  ([rows {:keys [pad] :or {pad 2}}]
   (let [col-widths (map #(apply max (map count %)) (transpose rows))
         fstr (str/join "   " (map #(str  "%" (if (= 0 %) "" (- %))  "s") col-widths))]
     (doseq [row rows]
       (println (apply format (str (format (str "%" pad "s") "") fstr) row))))))

(defn coerce-to-pairs [o]
  (if (vector? o)
    (partition 2 o)
    o))

(defn short? [f]
  (re-find #"^-[^-]$" f))

(defn long? [f]
  (re-find #"^--.*" f))

(defn print-help [cmd-name command-pairs flagpairs]
  (let [desc #(first (str/split (:doc % "") #"\R"))]
    (println (str "Usage: " cmd-name
                  (str/join (for [[_ {:keys [flags argdoc]}] flagpairs]
                              (str " [" (str/join " | " flags) argdoc "]")))
                  " <command> [<args>]"))
    (println)
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
                   " <command>"
                   (:argdoc cmdopts))) (desc cmdopts)]))))

(defn parse-error! [& msg]
  (throw (ex-info (str/join " " msg) {:type ::parse-error})))

(defn assoc-flag
  ([flags flagspec]
   (assoc flags (:key flagspec) (:value flagspec)))
  ([flags flagspec fallback-value]
   (assoc flags (:key flagspec) (if (contains? flagspec :value) (:value flagspec) fallback-value))))

(defn update-flag [flags flagspec f & args]
  (apply update flags (:key flagspec) f args))

(defn handle-flag [{:keys [flagmap strict?] :as cmdspec} flag cli-args args flags]
  (reduce
   (fn [[cli-args args flags] f]
     (if-let [{:keys [argcnt value] :as flagspec} (get flagmap f)]
       (cond
         (or (nil? argcnt)
             (= 0 argcnt))
         [cli-args args (if (:value flagspec)
                          (assoc-flag flags flagspec)
                          (update-flag flags flagspec (fnil inc 0)))]
         (= 1 argcnt)
         [(next cli-args) args (assoc-flag flags flagspec (first cli-args))]
         :else
         [(drop argcnt cli-args) args (assoc-flag flags flagspec (take argcnt cli-args))])
       (if strict?
         (parse-error! "Unknown flag: " f)
         [cli-args args (update-flag flags {:key (keyword (str/replace f #"^-+" ""))} (fnil inc 0))])))
   [cli-args args flags]
   (if (re-find #"^-\w+" flag)
     (map #(str "-" %) flag)
     [flag])))

(defn split-flags [cmdspec cli-args]
  (loop [[arg & cli-args] cli-args
         args             []
         flags            {}]
    (cond
      (nil? arg)         [args flags]
      (= "--" arg)       [(into args cli-args) flags]
      (= \- (first arg)) (let [[cli-args args flags] (handle-flag cmdspec arg cli-args args flags)]
                           (recur cli-args args flags))
      :else              (recur cli-args (conj args arg) flags))))

(def args-re #" ([A-Z][A-Z_-]*)|[= ]<([\w-_]+)>")

(defn parse-arg-names [str]
  [(str/split (str/replace str args-re "") #",\s*")
   (str/join (map first (re-seq args-re str)))
   (mapv (fn [[_ u l]] (keyword (str/lower-case (or u l)))) (re-seq args-re str))])

(defn parse-flagstr [flagstr flagopts]
  (let [;; support "--foo=<hello>" and "--foo HELLO"
        [flags argdoc argnames] (parse-arg-names flagstr)
        argcnt                  (count argnames)
        ;; e.g. "-i,--input, --[no-]foo ARG"
        flagstrs                (map #(re-find #"^(--?)(\[no-\])?([\w-]+)$" %) flags)
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
  (map (fn [[flagstr flagopts]]
         [flagstr (parse-flagstr flagstr (if (string? flagopts) {:doc flagopts} flagopts))])
       (coerce-to-pairs flagstrs)))

(defn build-flagmap-entries [[flagstr flagopts]]
  (let [{:keys [args key argcnt flags] :as parsed-flagstr} flagopts
        flags                                              (map #(re-find #"^(--?)(\[no-\])?([\w-]+)$" %) flags)]
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

(defn prepare-cmdpairs [commands]
  (let [m (if (vector? commands) (apply hash-map commands) commands)]
    (map (fn [[k v]]
           (let [v (if (var? v) (assoc (meta v) :command v) v)
                 [[cmd] doc argnames] (parse-arg-names k)]
             [cmd (assoc v :argdoc doc :argnames argnames)]))
         m)))

(defn dispatch
  ([cmdspec]
   (dispatch cmdspec *command-line-args*))
  ([{:keys [flags] :as cmdspec} cli-args]
   (let [flagpairs        (prepare-flagpairs flags)
         flagmap          (parse-flagstrs flagpairs)
         cmdspec          (assoc cmdspec :flagpairs flagpairs :flagmap flagmap)
         [pos-args flags] (split-flags cmdspec cli-args)]
     (dispatch cmdspec pos-args flags)))
  ([{:keys [commands flags flagpairs flagmap name] :as cmdspec} pos-args opts]
   (let [[cmd & pos-args]        pos-args
         pos-args                (vec pos-args)
         cmd                     (when cmd (first (str/split cmd #"[ =]")))
         opts                    (update opts ::command (fnil conj []) cmd)
         program-name            (or (:name cmdspec) "cli")
         command-pairs           (prepare-cmdpairs commands)
         command-map             (into {} command-pairs)
         {command     :command
          subcommands :commands
          argnames    :argnames
          ;; TODO: bit tricky because it might influence earlier parsing
          #_#_extra-flags :flags} (get command-map cmd)]

     (cond
       (or (nil? cmd)
           (and (nil? command)
                (nil? commands))
           (= "help" cmd)
           (:help opts))
       (print-help program-name command-pairs flagpairs)

       :else
       (if subcommands
         (dispatch (assoc cmdspec
                          :commands subcommands
                          :name (str program-name " " cmd)) pos-args opts)
         (command (merge (assoc opts ::argv pos-args) (zipmap argnames pos-args))))))))
