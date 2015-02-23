(ns clojbot.utils
  (:require [clojure.tools.logging :as log]))

(defn destruct-raw-message
  "Destructs a message into a map."
  [message]
  (let [map 
        (zipmap
         [:original :sender :command :channel :message]
         (re-matches #"^(?:[:](\S+) )?(\S+)(?: (?!:)(.+?))?(?: [:](.+))?$" message))]
    ;; If the nickname is found, put it in the map.
    (if (re-matches #"\S+!.*" (:sender map))
      (assoc map :nickname ((re-matches #"(.+)!.*" (:sender map)) 1))
      map)))
