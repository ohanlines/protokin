(ns protokin.system.config
  (:require
    [clojure.edn :as edn]
    [clojure.java.io :as io]))

(defn load-config
  "Load config EDN from a file path. Defaults to CONFIG_PATH or ./config.edn.
  Returns an empty map if the file does not exist."
  ([] (load-config (or (System/getenv "CONFIG_PATH") "config.edn")))
  ([path]
   (let [file (io/file path)]
     (if (.exists file)
       (edn/read-string (slurp file))
       {}))))
