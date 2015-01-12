(ns clojbot.utils
  (:require [clojure.tools.logging :as log]))

(defn destruct-raw-message
  "Destructs a message into a map."
  [message]
  (zipmap
       [:original :sender :command :channel :message]
       (re-matches #"^(?:[:](\S+) )?(\S+)(?: (?!:)(.+?))?(?: [:](.+))?$" message)))
