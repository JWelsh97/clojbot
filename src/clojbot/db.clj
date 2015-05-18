(ns clojbot.db
  (:require [clojure.java.jdbc :as sql]
            [clojure.edn       :as edn]
            [clj-time.core     :as   t]
            [clj-time.coerce   :as   c]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Configuring postgres:                                                           ;;
;; ---------------------                                                           ;;
;; sudo apt-get update                                                             ;;
;; sudo -i -u postgres                                                             ;;
;; sudo apt-get install postgresql postgresql-contrib                              ;;
;; createuser --interactive                                                        ;;
;; --> Create a user with the username that you will run the bot as (christophe)   ;;
;; createdb clojbot                                                                ;;
;; psql -d clojbot (as user that runs Clojbot to manage db)                        ;;
;; sudo -i -u postgres                                                             ;;
;; psql -U postgres template1 -c "alter user christophe with password 'pAssword!'" ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;



;;;==============================================================================
;;; Inner functions
;;;==============================================================================

(defn read-db-config
  "Reads in the configuration file from the conf/db.edn file. Returns
  a map that can be used with sql."
  []
  (let [inf (edn/read-string (slurp "conf/db.edn"))]
    {:classname "org.postgresql.Driver" ; must be in classpath
     :subprotocol "postgresql"
     :subname (str "//" (:dbhost inf) ":" (:dbport inf) "/" (:dbname inf))
     :user (:dbuser inf)
     :password (:dbpass inf)
     }))


(defn table-exists?
  "Queries if a table exists. `table` is supposed to be a keyword."
  [table]
  (let [tblname (name table)]
    (:exists
     (first
      (sql/query
       (read-db-config)
       [(format "SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE  table_name = '%s')" tblname)])))))


(defn create-table
  "Creates table with the given name as keyword and a list of fields
  for the table."
  [name & fields]
  (let [db (read-db-config)
        ddl (apply sql/create-table-ddl name fields)]
    (when-not (table-exists? name)
      (sql/db-do-commands
       db
       (apply sql/create-table-ddl name fields)))))



;;;==============================================================================
;;; API Helpers
;;;==============================================================================

(defn insert-into-table
  "Function that is used by macros to insert data into a table."
  [table datamap]
  (sql/insert! (read-db-config) table datamap))


(defn query-table
  [query]
  (sql/query (read-db-config) [query]))
