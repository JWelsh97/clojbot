(ns clojbot.modules.echo
  (:require [clojbot.commands :as cmd]
            [clojbot.botcore  :as core]))


(core/defmodule
  :echomodule
  (core/defcommand
    "echo"
    (fn [srv args msg]
      (cmd/send-message srv (:channel msg) args))))
