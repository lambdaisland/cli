(ns lambdaisland.cli.completions
  (:require
   [lambdaisland.cli.cmdspec :as cmdspec]
   [clojure.java.io :as io]
   [clojure.string :as str]))

(declare with-completions)

(defn zsh-completions [cmdspec {:lambdaisland.cli/keys [argv] :as opts}]
  (let [cmdspec (reduce
                 (fn [cmdspec arg]
                   (let [commands (cmdspec/prepare-cmdpairs (:commands (with-completions cmdspec)))]
                     (if-let [command-match (get (into {} commands) arg)]
                       (-> cmdspec
                           (dissoc :command :commands :middleware)
                           (merge command-match)
                           (assoc :flagmap (merge (:flagmap (cmdspec/to-cmdspec cmdspec))
                                                  (:flagmap (cmdspec/to-cmdspec command-match)))))
                       cmdspec)))
                 cmdspec
                 (butlast (next argv)))]
    (doseq [[cmd {:keys [doc]}] (cmdspec/prepare-cmdpairs (:commands cmdspec))]
      (println (str cmd (when doc ":") (first (str/split doc #"\R")))))
    (doseq [[flag {:keys [doc]}] (:flagmap cmdspec)]
      (println (str flag ":" (or doc (str/replace flag #"^-+" "")))))))

(defn install-zsh-completions [opts]
  (let [home        (System/getProperty "user.home")
        script-name (or (System/getProperty "babashka.file") (first (:lambdaisland.cli/argv opts)))
        cdir        (io/file home ".zsh/completions")
        zshrc-path  (io/file home ".zshrc")
        zshrc       (if (.exists zshrc-path) (slurp zshrc-path) "")]
    (when-not script-name
      (println "When running via clj/clojure (not babashka), you need to pass the fully qualified of the script as the first argument to __install_zsh_completions")
      (println "e.g. bin/my_script __install_zsh_completions /full/path/to/bin/my_script")
      (System/exit -1))
    (when-not (.exists cdir) (.mkdirs cdir))
    (println "Creating" (str (io/file cdir "_licli")))
    (spit (io/file cdir "_licli")
          (slurp (io/resource "lambdaisland/cli/_licli.zsh")))
    ;; we could get a lot fancier here with detecting the block we previously
    ;; added, but this is an ok start
    (let [stanza (str
                  (when-not (re-find #"(?m)^fpath=.*\~/.zsh/completions" zshrc)
                    (str "\n# lambdaisland.cli completions\nfpath=(~/.zsh/completions $fpath)\n"))
                  (when-not (re-find #"(?m)^autoload -Uz compinit$" zshrc)
                    (str "autoload -Uz compinit\n"))
                  (when-not (re-find #"(?m)^compinit$" zshrc)
                    (str "compinit\n"))
                  (let [base-name (last (str/split script-name #"/"))]
                    (str "compdef _licli " script-name " */" base-name " " base-name)))]
      (println "Updating" (str zshrc-path) ", adding:")
      (println stanza)
      (spit zshrc-path (str zshrc "\n" stanza)))))

(defn install-bash-completions [opts]
  (throw (ex-info {} "Not implemented"))
  )

(defn install-completions
  "Do the necessary setup to get shell completions. Shell will be guessed from
  $SHELL, unless explicitly specified."
  {:flags ["--zsh" "Do zsh setup"
           "--bash" "Do bash setup"]}
  [opts]
  (let [shell (cond
                (:zsh opts) :zsh
                (:bash opts) :bash
                :else (keyword (last (str/split (System/getenv "SHELL") #"/"))))]
    (case shell
      :zsh (install-zsh-completions opts)
      :bash (install-bash-completions opts))))

(defn with-completions [cmdspec]
  (update cmdspec
          :commands
          (fnil into [])
          ["__licli"
           {:no-doc true
            :commands
            ["completions" {:doc "Generate completions based on partially completed arguments

called by shell functions to do the actual completing."
                            :command (partial zsh-completions cmdspec)}
             "install-completions" #'install-completions]}]))
