(ns lambdaisland.cli
  (:require [clojure.string :as str]))

(defn print-help [prefix commands]
  (println "Usage:" prefix "[COMMAND] [COMMAND_ARGS...]")
  (println)
  (doseq [[cmd {:keys [description]}] (partition 2 commands)]
    (println (format "  %-15s%s" cmd (or description "")))))

(defn split-flags [cli-args]
  (reduce (fn [[args flags] arg]
            (cond
              (= "--" arg)
              (reduced [(into args (drop (inc  (+ (count args) (count flags))) cli-args)) flags])
              (= \- (first arg))
              [args (conj flags arg)]
              :else
              [(conj args arg) flags]))
          [[] []]
          cli-args))

(defn parse-flags [{:keys [flags] :as cmdspec} cli-flags]
  (reduce (fn [opts f]
            (cond
              (str/starts-with? f "--no-")
              (assoc opts (keyword (subs f 5)) false)
              (str/starts-with? f "--")
              (assoc opts (keyword (subs f 2)) true)
              (str/starts-with? f "-")
              (reduce (fn [o l]
                        (assoc o (keyword (str l)) true))
                      opts
                      (subs f 1))))
          {}
          cli-flags))

(defn dispatch
  ([cmdspec]
   (dispatch cmdspec *command-line-args*))
  ([cmdspec cli-args]
   (let [[pos-args flags] (split-flags cli-args)
         opts (parse-flags cmdspec flags)]
     (dispatch cmdspec pos-args opts)))
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
