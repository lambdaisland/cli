(ns lambdaisland.cli.completions
  (:require
   [lambdaisland.cli.cmdspec :as cmdspec]
   [clojure.java.io :as io]
   [clojure.string :as str]))

(declare with-completions)

(defn shell-completions [cmdspec {:lambdaisland.cli/keys [argv] :as opts}]
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
      (println (str cmd (when doc (str ":" (first (str/split doc #"\R")))))))
    (doseq [[flag {:keys [doc]}] (:flagmap cmdspec)]
      (println (str flag ":" (or doc (str/replace flag #"^-+" "")))))))

(defn install-zsh-completions [opts]
  (let [home        (System/getProperty "user.home")
        script-name (or (System/getProperty "babashka.file") (first (:lambdaisland.cli/argv opts)))
        cdir        (io/file home ".zsh/completions")
        zshrc-path  (io/file home ".zshrc")
        zshrc       (if (.exists zshrc-path) (slurp zshrc-path) "")]
    (when-not script-name
      (println "When running via clj/clojure (not babashka), you need to pass the fully qualified of the script as the first argument to install-completions")
      (println "e.g. bin/my_script __licli install-completions /full/path/to/bin/my_script")
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
  (let [home        (System/getProperty "user.home")
        script-name (or (System/getProperty "babashka.file") (first (:lambdaisland.cli/argv opts)))
        bashrc-path (io/file home ".bashrc")
        bashrc      (if (.exists bashrc-path) (slurp bashrc-path) "")]
    (when-not script-name
      (println "When running via clj/clojure (not babashka), you need to pass the fully qualified of the script as the first argument to install-completions")
      (println "e.g. bin/my_script __licli install-completions /full/path/to/bin/my_script")
      (System/exit -1))
    (let [base-name (last (str/split script-name #"/"))
          stanza    (str
                     (when-not (re-find #"(?m)^# lambdaisland.cli completions" bashrc)
                       (str
                        "\n# lambdaisland.cli completions\n"
                        "if [ -f " (str (io/file home ".bash_completion.d/_licli.bash")) " ]; then\n"
                        "    source " (str (io/file home ".bash_completion.d/_licli.bash")) "\n"
                        "fi\n"))
                     (str "complete -F _licli " script-name " " "*/" base-name " " base-name "\n"))]
      (let [cdir (io/file home ".bash_completion.d")]
        (when-not (.exists cdir) (.mkdirs cdir))
        (println "Creating" (str (io/file cdir "_licli.bash")))
        (spit (io/file cdir "_licli.bash")
              (slurp (io/resource "lambdaisland/cli/_licli.bash"))))
      (println "Updating" (str bashrc-path) ", adding:")
      (println stanza)
      (spit bashrc-path (str bashrc "\n" stanza)))))

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
                            :command (partial shell-completions cmdspec)}
             "install-completions" #'install-completions]}]))
