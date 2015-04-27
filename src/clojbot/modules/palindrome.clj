(ns clojbot.modules.palindrome
  (:require [clojbot.commands :as cmd ]
            [clojbot.botcore  :as core]
            [clojure.string   :as str ]))


(defn is-palindrome?
  [word]
  (and (< 5  (count word))
       (= word (apply str (reverse word)))))

(core/defmodule
  :palindrome
  (core/defhook
    :PRIVMSG
    (fn [srv msg]
      (let [msgtext (clojure.string/replace (:message msg) #"[^a-zA-Z0-9]" "")]
        (when  ((comp is-palindrome? str/lower-case) msgtext)
          (cmd/send-message srv (:channel msg) "Palindrome!"))))))
