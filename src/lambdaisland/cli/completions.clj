(ns lambdaisland.cli.completions
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]))

(defn zsh-completions [cmdspec {:lambdaisland.cli/keys [argv] :as opts}]
  (let [cmdspec (reduce
                 (fn [cmdspec arg]
                   (let [commands ((resolve 'lambdaisland.cli/prepare-cmdpairs) (:commands cmdspec))]
                     (if-let [command-match (get (into {} commands) arg)]
                       (-> cmdspec
                           (dissoc :command :commands :middleware)
                           (merge command-match)
                           (assoc :flagmap (merge (:flagmap ((resolve 'lambdaisland.cli/to-cmdspec) cmdspec))
                                                  (:flagmap ((resolve 'lambdaisland.cli/to-cmdspec) command-match)))))
                       cmdspec)))
                 cmdspec
                 (butlast (next argv)))]
    (doseq [[cmd {:keys [doc]}] ((resolve 'lambdaisland.cli/prepare-cmdpairs) (:commands cmdspec))]
      (println (str cmd (when doc ":") doc)))
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
    (spit (io/file cdir "_licli")
          (slurp (io/resource "lambdaisland/cli/_licli.zsh")))
    ;; we could get a lot fancier here with detecting the block we previously
    ;; added, but this is an ok start
    (spit zshrc-path
          (str zshrc
               "\n"
               (when-not (re-find #"(?m)^fpath=.*\~/.zsh/completions" zshrc)
                 (str "\n# lambdaisland.cli completions\nfpath=(~/.zsh/completions $fpath)\n"))
               (when-not (re-find #"(?m)^autoload -Uz compinit$" zshrc)
                 (str "autoload -Uz compinit\n"))
               (when-not (re-find #"(?m)^compinit$" zshrc)
                 (str "compinit\n"))
               (let [base-name (last (str/split script-name #"/"))]
                 (str "compdef _licli " script-name " */" base-name " " base-name))))))

(defn with-completions [cmdspec]
  (update cmdspec
          :commands
          (fnil into [])
          ["__zsh_completions" {:no-doc true
                                :command (partial zsh-completions cmdspec)}
           "__install_zsh_completions" {:no-doc true
                                        :command #'install-zsh-completions}]))
