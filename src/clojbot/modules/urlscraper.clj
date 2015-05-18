(ns clojbot.modules.urlscraper
  (:require [clojbot.commands :as  cmd]
            [clojbot.botcore  :as core]
            [clj-time.coerce  :as    c]
            [clj-time.core    :as    t]
            [clj-time.format  :as    f]))

(def url-regex #"(https?|ftp):\/\/[^\s/$.?#].[^\s]*")
(def shout "Old! Original by %s (%s).")
(def timeformat (f/formatter "dd/MM/yyy HH:mm"))

;;;;;;;;;;;;;
;; Helpers ;;
;;;;;;;;;;;;;

(defn get-urls-from-line
  "Takes a line of text and returns all the urls present in the line."
  [line]
  (map first  (re-seq url-regex line)))

(defn repost?
  "Returns true or false. Just checks if there is an entry in the
  database that matches the given url."
  [url channel]
  (first
   (core/query
    (format "select * from urlstorage where url = '%s' and channel = '%s'" url channel))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Module Implementation ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;

(core/defmodule
  :urlscraper
  (core/defstorage
    :urlstorage [:url :text] [:time :timestamp] [:sender :text] [:channel :text])
  (core/defhook
    :PRIVMSG
    (fn [srv msg]
      (let [urls   (get-urls-from-line (:message msg))
            chan   (:channel msg)  
            sender (:nickname msg "unknown")]
        ;; For each url, check if it is old or not.
        ;; If it's old, shout, otherwise insert into db.
        (doseq [url urls]
          (if-let [res (repost? url chan)]
            (let [sender     (:sender res)
                  time       (c/from-sql-time (:time res))
                  timestring (f/unparse  timeformat time)]
              (cmd/send-message srv chan (format shout sender timestring)))
            (core/store :urlstorage {:url url :time (c/to-timestamp (t/now)) :sender sender})))))))
