(ns clojbot.core
  (:require [clojure.tools.logging :as log]
            [clojbot.utils         :as u]
            [clojbot.commands      :as cmd]
            [clojbot.botcore       :as core]
            [clojure.core.async    :as as]
            [clojure.edn           :as edn])
  (:gen-class))


(defn -main
  [& args]
  (let [servers (edn/read-string (slurp "conf/servers.edn"))]
    (let [bot (core/connect-bots (core/create-bots servers))]
      (println "done"))))
