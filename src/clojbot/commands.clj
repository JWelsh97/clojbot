(ns clojbot.commands
  (:require [clojure.tools.logging :as log]
            [clojbot.utils         :as u]
            [clojure.string        :as str]
            [clojbot.botcore       :as core]))


(defn register
  "Sets up the nickname and user information on the server."
  [bot user]
  (dosync
   (core/write-message bot (str "NICK " (:nick user)))
   (core/write-message bot (str "USER " (:nick user) " 0 * :" (:name user)))))


(defn join
  "Joins a given channel on a given server."
  [bot channel]
  (core/write-message bot (str/join " " (list "JOIN" channel))))


(defn send-message
  "Sends a simple message to a channel or user."
  [bot channel message]
  (core/write-message bot (str/join " " (list "PRIVMSG" channel message))))

