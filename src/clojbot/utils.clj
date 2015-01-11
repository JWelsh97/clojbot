(ns clojbot.utils
  (:require [clojure.tools.logging :as log]))

(defn destruct-raw-message
  "Destructs a message into an array:
  [<original message> <sender> <type>  <channel> <message>]"
  [message]
  (zipmap
       [:original :sender :command :channel :message]
       (re-matches #"^(?:[:](\S+) )?(\S+)(?: (?!:)(.+?))?(?: [:](.+))?$" message)))
