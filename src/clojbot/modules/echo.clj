(ns clojbot.modules.echo
  (:require [clojbot.commands :as cmd]
            [clojbot.botcore :as c]))


;; (c/module
;;   :echomodule
;;   (c/defcommand 
;;     "echo"
;;     (fn [srv args msg]
;;       (cmd/send-message srv (:channel msg) args)))
;;   (c/defcommand 
;;     "echothis"
;;     (fn [srv args msg]
;;       (cmd/send-message srv (:channel msg)))))

(c/defmodule
  :echomodule
  :foobar)
