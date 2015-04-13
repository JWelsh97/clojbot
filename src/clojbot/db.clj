(ns clojbot.db
  (:require [clojure.java.jdbc     :as sql]
            [clojure.edn           :as edn]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Create postgres user                                          ;;
;; --------------------                                          ;;
;;                                                               ;;
;; $ su - postgres                                               ;;
;; $ psql template1                                              ;;
;; template1=# CREATE USER tom WITH PASSWORD 'myPassword';       ;;
;; template1=# CREATE DATABASE clojbot;                          ;;
;; template1=# GRANT ALL PRIVILEGES ON DATABASE clojbot to tom;  ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create-db-config
  []
  (let [inf (edn/read-string (slurp "conf/db.edn"))]
    {:classname "org.postgresql.Driver" ; must be in classpath
     :subprotocol "postgresql"
     :subname (str "//" (:dbhost inf) ":" (:dbport inf) "/" (:dbname inf))
     :user (:dbuser inf)
     :password (:dbuser inf)}))

(defn execute-command
  [query]
  (let [db (create-db-config)]
    (sql/db-do-commands db [query])))

(defn execute-query
  [query]
  (let [db (create-db-config)]
    (sql/query db [query])))
