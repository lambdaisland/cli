(ns lambdaisland.cli
  (:require [clojure.string :as str]))

;; I've tried to be somewhat consistent with variable naming

;; - cmdspec: the map passed into dispatch, with :commands and :flags, possibly augmented with :flagspecs
;; - cli-args: the vector of cli arguments as the come in, or the tail of it if part has been processed
;; - flagspecs: map of the possible flags with metadata, expanded to serve direct lookup, e.g. {"-i" {,,,} "--input" {,,,} "--no-input" {,,,}}
;; - flagspec: map of how to deal with a given flag {:flag "--foo", :key :foo, :short? false, :argcnt 1}
;; - argcnt: number of arguments a given flag consumes (usually zero or one, but could be more)
;; - args/pos-args: vector of positional arguments that will go to the command
;; - opts: options that will go the command, based on any parsed flags
;; - commands: specification of (sub)-commands, can be vector (for order) or map
;; - raw-flagspecs: flags as specified in the cmdspec, without normalization
;; - cmd: a single (sub) command like `"add"` or `"widgets"`

(defn print-help [{:keys [commands flags] :as cmdspec} _]
  (let [commands (if (vector? commands) (partition 2 commands) commands)]
    (println "Usage:" (or (:name cmdspec) "cli") "[command...] [flags-or-args...]")
    (println)
    (doseq [[cmd {:keys [description]}] commands]
      (println (format (str "  %-" (+ 3 (apply max (map (comp count first) commands))) "s%s") cmd (or description ""))))))

(defn parse-error! [& msg]
  (throw (ex-info (str/join " " msg) {:type ::parse-error})))

(defn handle-flag [{:keys [flagspecs] :as cmdspec} flag cli-args args flags]
  (reduce
   (fn [[cli-args args flags] f]
     (if-let [{:keys [argcnt value] :as flagspec} (get flagspecs f)]
       (cond
         (or (nil? argcnt)
             (= 0 argcnt))
         [cli-args args (assoc flags (:key flagspec) (if (contains? flagspec :value) value true))]
         (= 1 argcnt)
         [(next cli-args) args (assoc flags (:key flagspec) (if (contains? flagspec :value) value (first cli-args)))]
         :else
         [(drop argcnt cli-args) args (assoc flags (:key flagspec) (if (contains? flagspec :value) value (take argcnt cli-args)))])
       (parse-error! "Unknown flag: " f)))
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

(defn parse-flagspec [flagspec flagopts]
  (let [argcnt (count  (re-seq #"[A-Z][A-Z_-]*[A-Z]" flagspec))
        flagopts (if (string? flagopts) {:description flagopts} flagopts)]
    (for [[_ _ dash no flag] (re-seq #"([, ]|^)(--?)(\[no-\])?([\w-]+)" flagspec)
          negative? (if no [true false] [false])]
      (merge {:flag (str dash (if negative? "no-") flag)
              :key (keyword flag)
              :short? (= dash "-")
              :argcnt argcnt}
             (when (= 0 argcnt)
               {:value (not negative?)})
             flagopts))))

(defn parse-flagspecs [raw-flagspecs]
  (into {"--help" {:key :help :value true}}
        (map (juxt :flag identity))
        (mapcat
         (fn [[flagspec flagopts]]
           (parse-flagspec flagspec flagopts))
         (if (vector? raw-flagspecs) (partition 2 raw-flagspecs) raw-flagspecs))))

(defn dispatch
  ([cmdspec]
   (dispatch cmdspec *command-line-args*))
  ([cmdspec cli-args]
   (let [[pos-args flags] (split-flags (assoc cmdspec :flagspecs (parse-flagspecs (:flags cmdspec))) cli-args)]
     (dispatch cmdspec pos-args flags)))
  ([{:keys [commands flags name] :as cmdspec} pos-args opts]
   (let [[cmd & pos-args] pos-args
         opts (update opts ::command (fnil conj []) cmd)
         program-name (or (:name cmdspec) "cli")
         command-map (if (vector? commands) (apply hash-map commands) commands)
         command-vec (if (vector? commands) commands (into [] cat commands))
         {command :command
          subcommands :commands} (get command-map cmd)]
     (cond
       (or (nil? cmd)
           (and (nil? command)
                (nil? commands))
           (= "help" cmd)
           (:help opts))
       (print-help cmdspec opts)

       :else
       (if subcommands
         (dispatch {:commands subcommands :flags flags :name (str program-name " " cmd)} pos-args opts)
         (command pos-args opts))))))
