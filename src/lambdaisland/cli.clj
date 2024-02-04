(ns lambdaisland.cli
  (:require [clojure.string :as str]))

(defn print-help [prefix commands]
  (println "Usage:" prefix "[COMMAND] [COMMAND_ARGS...]")
  (println)
  (doseq [[cmd {:keys [description]}] (partition 2 commands)]
    (println (format "  %-15s%s" cmd (or description "")))))

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

(defn parse-flagspecs [flags]
  (into {"--help" {:key :help :value true}}
        (map (juxt :flag identity))
        (mapcat
         (fn [[flagspec flagopts]]
           (parse-flagspec flagspec flagopts))
         (if (vector? flags) (partition 2 flags) flags))))

(defn dispatch
  ([cmdspec]
   (dispatch cmdspec *command-line-args*))
  ([cmdspec cli-args]
   (let [[pos-args flags] (split-flags (assoc cmdspec :flagspecs (parse-flagspecs (:flags cmdspec))) cli-args)]
     (dispatch cmdspec pos-args flags)))
  ([{:keys [commands flags name] :as cmdspec} pos-args opts]
   (let [[cmd & pos-args] pos-args
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
       (print-help program-name command-vec)

       :else
       (if subcommands
         (dispatch {:commands subcommands :flags flags :name (str program-name " " cmd)} pos-args opts)
         (command pos-args opts))))))

(with-out-str
  (dispatch
   {:commands ["run" {:command (fn [args flags]
                                 (print "RUN" args flags))}]}
   ["run"]))
;; => "RUN nil nil"

(with-out-str
  (dispatch
   {:commands ["run" {:command (fn [args flags]
                                 (print "RUN" args flags))}]}
   ["run" "hello"]))
;; => "RUN (hello) nil"

(with-out-str
  (dispatch
   {:commands ["run" {:command (fn [args flags]
                                 (print "RUN" args flags))
                      }]}
   ["help"]))
;; => "Usage: cli [COMMAND] [COMMAND_ARGS...]\n\n  run            \n"

(with-out-str
  (dispatch
   {:commands ["run" {:command (fn [args flags]
                                 (print "RUN" args flags))
                      :description "Do something"}]}
   ["help"]))
;; => "Usage: cli [COMMAND] [COMMAND_ARGS...]\n\n  run            Do something\n"

(with-out-str
  (dispatch
   {:commands {"run" {:command (fn [args flags]
                                 (print "RUN" args flags))
                      :description "Do something"}}}
   ["help"]))

(defn show-args [cmd]
  (fn [args flags]
    (print (str/upper-case cmd) args flags)))

(println
 (dispatch
  {:commands {"run" {:command (show-args "run")
                     :description "Do something"}
              "widget" {:description "Work with widgets"
                        :commands
                        ["ls" {:description "List widgets"
                               :command (show-args "widget ls")}
                         "add" {:description "Add widget"
                                :command (show-args "widget add")}]}}}
  ["widget" "ls" "x" "--recursive" "--help"]))

(println
 (dispatch
  {:commands {"run" {:command (show-args "run")
                     :description "Do something"}
              "widget" {:description "Work with widgets"
                        :commands
                        ["ls" {:description "List widgets"
                               :command (show-args "widget ls")}
                         "add" {:description "Add widget"
                                :command (show-args "widget add")}]}}
   }
  ["widget" "ls" "x" "--recursive" "--help"]))

(dispatch
 {:commands {"run" {:command (show-args "run")
                    :description "Do something"}
             "widget" {:description "Work with widgets"
                       :commands
                       ["ls" {:description "List widgets"
                              :command (show-args "widget ls")}
                        "add" {:description "Add widget"
                               :command (show-args "widget add")}]}}
  :flags ["-i,--input FILE" {:desc "Specify input file"}
          "--output FILE" "Specify output file"]}
 ["widget" "ls" "--input" "INPUT" "--output" "OUTPUT"])


(parse-flagspec
 "-i,--input FILE" {:desc "Specify input file"})

(parse-flagspecs
 ["-i,--input FILE" {:desc "Specify input file"}
  "--output FILE" "Specify output file"
  "--[no-]foo" ""])

(let [cmdspec
      {:commands {"run" {:command (show-args "run")
                         :description "Do something"}
                  "widget" {:description "Work with widgets"
                            :commands
                            ["ls" {:description "List widgets"
                                   :command (show-args "widget ls")}
                             "add" {:description "Add widget"
                                    :command (show-args "widget add")}]}}
       :flags ["-i,--input FILE" {:desc "Specify input file"
                                  :key "XXX"}
               "--output FILE" "Specify output file"]}]
  (split-flags
   (assoc cmdspec :flagspecs (parse-flagspecs (:flags cmdspec)))
   ["widget" "ls" "--help" "--input" "INPUT" "--output" "OUTPUT"]))

(dispatch
 {:commands {"run" {:command (show-args "run")
                    :description "Do something"}
             "widget" {:description "Work with widgets"
                       :commands
                       ["ls" {:description "List widgets"
                              :command (show-args "widget ls")
                              :help "List widgets in the order they exist."}
                        "add" {:description "Add widget"
                               :command (show-args "widget add")}]}}
  :flags ["-i,--input FILE" {:desc "Specify input file"}
          "--output FILE" "Specify output file"]}
 ["widget" "ls" "--help"])
