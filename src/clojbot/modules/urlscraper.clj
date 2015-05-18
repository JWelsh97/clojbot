(ns clojbot.modules.urlscraper
  (:require [clojbot.commands :as  cmd]
            [clojbot.botcore  :as core]
            [clj-time.coerce  :as    c]
            [clj-time.core    :as    t]
            [clj-time.format  :as    f]))

(def url-regex #"(https?|ftp):\/\/[^\s/$.?#].[^\s]*")

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
  [url]
  (first (core/query (format "select * from urlstorage where url = '%s'" url))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Module Implementation ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;

(core/defmodule
  :urlscraper
  (core/defstorage
    :urlstorage [:url :text] [:time :timestamp] [:sender :text])
  (core/defhook
    :PRIVMSG
    (fn [srv msg]
      (let [urls   (get-urls-from-line (:message msg))
            chan   (:channel msg)  
            sender (:nickname msg "unknown")]
        
        (doseq [url urls]
          (if-let [res (repost? url)]
            (let [sender     (:sender res)
                  time       (c/from-sql-time (:time res))
                  timestring (f/unparse  (f/formatter "dd/MM/yyy HH:mm") time)
                  reply      (format "Old! Original by %s (%s)" sender timestring)]
              (cmd/send-message srv chan reply))
            (core/store :urlstorage {:url url :time (c/to-timestamp (t/now)) :sender sender})))))))
