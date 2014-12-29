(ns clojbot.core
  (:require [clojure.tools.logging :as log]
            [clojbot.utils :as u]
            [clojbot.commands :as cmd]
            [clojbot.botcore :as core]))

(def kreynet {:name "irssi.be.krey.net" :port 6667})
(def user    {:name "Clojure Bot" :nick "clojbot"})


;;; Channel message: :m1dnight!~m1dnight@109.130.227.3 PRIVMSG #clojbot :message in #clojure
;;; Private message: :m1dnight!~m1dnight@109.130.227.3 PRIVMSG fabiola :private message

(defn -main
  "I don't do a whole lot."
  [& args]
  (let [bot (core/create-bot kreynet)]
    (cmd/register bot user)
    (cmd/join bot "#clojbot")
    ))
