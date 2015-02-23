(ns clojbot.karmamodule
  (:require [clojure.java.jdbc :as sql]))


(def db
    {:classname "org.postgresql.Driver"
     :subprotocol "postgresql"
     :subname "//localhost/christophe"
     :username "christophe"
     :password "christophe"})

(defn create-table
  []
  (sql/db-do-commands
   db
   (sql/create-table-ddl :karma2 [ :nickname :text][:karma :int])))
