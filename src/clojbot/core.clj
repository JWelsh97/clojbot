(ns clojbot.core
  (:require [clojure.tools.logging :as log]
            [clojbot.utils         :as u]
            [clojbot.commands      :as cmd]
            [clojbot.botcore       :as core]))

(def kreynet {:name "irssi.be.krey.net" :port 6667})
(def user    {:name "Clojure Bot" :nick "clojbot" :alternates ["clojbot_" "clojbot__"]})


;;; Channel message: :m1dnight!~m1dnight@109.130.227.3 PRIVMSG #clojbot :message in #clojure
;;; Private message: :m1dnight!~m1dnight@109.130.227.3 PRIVMSG fabiola :private message

(defn -main
  "I don't do a whole lot."
  [& args]
  (let [bot (core/init-bot kreynet user)]
    (cmd/register bot user)
    (cmd/join bot "#clojbot")
    (cmd/send-message bot "#clojbot" ":ey loser")
    (Thread/sleep 10000)
    (cmd/nick bot "fabiola")
    (cmd/nick bot "2fabiola")
    (cmd/nick bot "m1dnight")
    (cmd/nick bot "clojbot")
))
