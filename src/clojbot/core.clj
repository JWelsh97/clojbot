(ns clojbot.core
  (:require [clojure.tools.logging :as log]
            [clojbot.utils         :as u]
            [clojbot.commands      :as cmd]
            [clojbot.botcore       :as core]
            [clojure.core.async    :as as]
            [clojure.edn           :as edn])
  (:gen-class))

;; TODO On a message that comes in of a failed nick, rotate the list of
;; alternates or somethign.

;; Some temporary configuration. Best to abstract this properly.
(def freenode {
               :humanname "freenode"
               :ip        "verne.freenode.net"
               :port      6667
               :channels  ["#clojbot" "#clojbot2"]
               :name      "Clojure Bot"
               :nick      "clojbot__"
               :altnicks  ["clojbot_" "clojbot__"]
               })
(def kreynet {
              :humanname "kreynet"
              :ip        "irssi.be.krey.net"
              :port      6667
              :channels  ["#clojbot" "#clojbot2"]
              :name      "Clojure Bot"
              :nick      "clojbot__"
              :altnicks  ["clojbot_" "clojbot__"]
              })
(def quakenet {
              :humanname "quakenet"
              :ip        "euroserv.fr.quakenet.org"
              :port      6667
              :channels  ["#begijnhof"]
              :name      "yourmother"
              :nick      "yourmother"
              :altnicks  ["clojbot_" "clojbot__"]
              })


(defn -main
  [& args]
  (let [servers (edn/read-string (slurp "conf/servers.edn"))]
    (let [bot (core/connect-bot (core/create-bot servers))]
      (println "done"))))
