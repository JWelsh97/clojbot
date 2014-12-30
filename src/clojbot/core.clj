(ns clojbot.core
  (:require [clojure.tools.logging :as log]
            [clojbot.utils         :as u]
            [clojbot.commands      :as cmd]
            [clojbot.botcore       :as core]
            [clojure.core.async    :as as])
  (:gen-class))

(def kreynet {:name "irssi.be.krey.net" :port 6667})
(def user    {:name "Clojure Bot" :nick "clojbot" :alternates ["clojbot_" "clojbot__"]})


;;; Channel message: :m1dnight!~m1dnight@109.130.227.3 PRIVMSG #clojbot :message in #clojure
;;; Private message: :m1dnight!~m1dnight@109.130.227.3 PRIVMSG fabiola :private
;;; message


(def replymodule {:name    :reply-module
                  :type    :PRIVMSG
                  :handler (fn [bot message]
                             (cmd/send-message bot (:sender message) ":im replying on a message"))})
(defn -main
  "I don't do a whole lot."
  [& args]
  (let [bot (core/init-bot kreynet user)]
    (cmd/register bot user)
    (cmd/join bot "#clojbot")
    (core/connect-module bot replymodule)
    (Thread/sleep 5000)))
