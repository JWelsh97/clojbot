(ns clojbot.utils
  (:require [clojure.tools.logging :as log]))

(defn destruct-raw-message
  "Destructs a message into an array:
  [<original message> <sender> <type>  <channel> <message>]"
  [message]
  (zipmap
       [:original :sender :command :channel :message]
       (re-matches #"^(?:[:](\S+) )?(\S+)(?: (?!:)(.+?))?(?: [:](.+))?$" message)))


(defn write-out 
  "Writes a raw line to the socket."
  [socket msg]
  (log/info "OUT :: " msg)
  (doto (:out socket)
    (.println (str msg "\r"))
    (.flush)))


(defn read-in
  "Reads a line from the socket and parses it into a message map."
  [socket]
  (destruct-raw-message (.readLine (:in socket))))
