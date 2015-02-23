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
(def kreynet {:name "verne.freenode.net" :port 6667})
(def user    {:name "Clojure Bot" :nick "clojbot" :alternates ["clojbot_" "clojbot__"]})


;; Test module. Will reply to *everything* in a PM.
(def testmodule {:name    :reply-module
                 :type    :PRIVMSG
                 :handler (fn [bot message]
                            (cmd/send-message bot (:channel message) "im replying on a message"))})
(defn -main
  "I don't do a whole lot."
  [& args]
  (let [bot (core/init-bot kreynet user)]
    (cmd/register bot user)
    (cmd/join bot "#clojbot")
    (Thread/sleep 10000)
    (cmd/nick bot "m1dnight___")
    ;;(core/connect-module bot testmodule)
    (Thread/sleep 5000)))
