(ns clojbot.commands
  (:require [clojure.tools.logging :as log]
            [clojbot.utils         :as u]
            [clojure.string        :as str]
            [clojbot.botcore       :as core]))


(defn send-ping
  "Sends a ping message to the connected server."
  [bot]
  (dosync (core/write-message bot "PING 1234")))


(defn join
  "Joins a given channel list."
  [bot channels]
  (doall (map #(core/write-message bot (str/join " " (list "JOIN" %))) channels)))


(defn send-message
  "Sends a simple message to a channel or user."
  [bot channel message]
  (core/write-message bot (str/join " " (list "PRIVMSG" channel (str ":" message)))))


(defn nick
  "Changes the nick of the bot. Not certain this will succeed.
  Nick could be taken or invalid.
  When a nick fails we get code 433, on success we get "
  ;;; TODO Additional checks are needed here. Perhaps wait for a reply.
  [bot new-nick]
  (core/write-message bot (str/join " " (list "NICK" new-nick))))
