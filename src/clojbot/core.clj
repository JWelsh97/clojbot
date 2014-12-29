(ns clojbot.core
  (:import  [java.net Socket]
            [java.io PrintWriter InputStreamReader BufferedReader])
  (:require [clojure.tools.logging :as log]))

(def freenode {:name "irssi.be.krey.net" :port 6667})
(def user {:name "Clojure Bot" :nick "clojbot"})

(declare conn-handler)

(defn connect
  "Sets up a socket to connect to the IRC network defined in the server map.
  Returns a reference to a ref containing an :in and :out stream."
  [server]
  (let [socket (Socket. (:name server) (:port server))
        in     (BufferedReader. (InputStreamReader. (.getInputStream socket)))
        out    (PrintWriter. (.getOutputStream socket))
        conn   (ref {:in in :out out})]

    (.start (Thread. #(conn-handler conn)))
    conn))

(defn write [conn msg]
  (doto (:out @conn)
    (.println (str msg "\r"))
    (.flush)))

(defn conn-handler [conn]
  (while (nil? (:exit @conn))
    ;;; Read in a message from the connection. Dispatch.
    (let [msg (.readLine (:in @conn))]

      (cond 
       (re-find #"^ERROR :Closing Link:" msg)
       (dosync
        (alter conn merge {:exit true}))
       (re-find #"^PING" msg)
       (write conn (str "PONG "  (re-find #":.*" msg)))
       :else
       (println "Received: " msg)))))

(defn login [conn user]
  (write conn (str "NICK " (:nick user)))
  (write conn (str "USER " (:nick user) " 0 * :" (:name user))))

(defn -main
  "I don't do a whole lot."
  [& args]
  (log/info "This is a log info")
  ;(def irc (connect freenode))
  ;(login irc user)
  ;(write irc "JOIN #clojbot")
  ;(write irc "QUIT")
  )
