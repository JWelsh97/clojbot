(ns clojbot.commands
  (:require [clojure.tools.logging :as log]
            [clojbot.utils :as u]
            [clojure.string :as str]))


(defn register
  "Sets up the nickname and user information on the server."
  [bot user]
  (dosync
   (let [socket @(:socket @bot)]
     (u/write-out socket (str "NICK " (:nick user)))
     (u/write-out socket (str "USER " (:nick user) " 0 * :" (:name user)))
     ;; Change the user in the atom.
     (println "altering")
     (alter bot (fn [bot] (assoc bot :user user))))))


(defn join
  "Joins a given channel on a given server."
  [bot channel]
  (dosync
   (u/write-out @(:socket @bot) (str/join " " (list "JOIN" channel)))))
