(ns clojbot.utils
  (:import  [java.io StringWriter         ])
  (:require [clojure.tools.logging :as log]
            [clojure.core.async    :as as ]
            [clojure.pprint        :as pprint]))

(defn destruct-raw-message
  "Destructs a message into a map."
  [message]
  (let [map 
        (zipmap
         [:original :sender :command :channel :message]
         (re-matches #"^(?:[:](\S+) )?(\S+)(?: (?!:)(.+?))?(?: [:](.+))?$" message))]
    map))
    ;; If the nickname is found, put it in the map.
    ;; (if (re-matches #"\S+!.*" (:sender map))
    ;;   (assoc map :nickname ((re-matches #"(.+)!.*" (:sender map)) 1))
    ;;   map)))



(defn sender-nick
  "Parses the actual nickname from a sender sender!sender@foo.bar.com"
  [message]
  (when-let [sender (re-find #"(.+)!.*" (:sender message))]
    (nth sender 1)))


(defn try-times
  "Executes thunk. If an exception is thrown, will retry. At most n retries
  are done. If still some exception is thrown it is bubbled upwards in
  the call chain."
  [n thunk]
  (loop [n n]
    (if-let [result (try
                      [(thunk)]
                      (catch Exception e
                        (when (zero? n)
                          (throw e))))]
      (result 0)
      (recur (dec n)))))



(defn read-with-timeout
  [channel timeout]
  (let [res (as/<!!
             (as/go
               (let [[res src] (as/alts! [channel (as/timeout timeout)])]
                 (if (= channel src)
                   res
                   nil))))]
    ;; If res is nil it means that we waited timeout for a message.
    res))

(defn monitor-ref
  [r]
  (add-watch r :watcher
             (fn [key atom old-state new-state]
               (prn "-- Ref Changed --")
               (prn "old-state" old-state)
               (prn "new-state" new-state))))


(defn shift-left
  [xs]
  (let [len (count xs)
        shift (take len (drop 1 (cycle xs)))]
    shift))


(defn pprint-to-string
  [val]
  (let [writer (StringWriter.)]
    (pprint/pprint val writer)
    (.toString writer)))


(defn my-expander [form depth]
  (cond
   (= 0 depth)
   form
   (not (list? form))
   (macroexpand form)
   :else
   (macroexpand (map macroexpand (my-expander form (dec depth))))))




