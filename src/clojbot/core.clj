(ns clojbot.core
  (:require [clojure.tools.logging :as log]
            [clojbot.utils         :as u]
            [clojbot.commands      :as cmd]
            [clojbot.botcore       :as core]
            [clojure.core.async    :as as])
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
  "I don't do a whole lot."
  [& args]
  (let [bot (core/create-bot [freenode kreynet quakenet])]
    (println "done")))
