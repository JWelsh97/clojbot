(ns clojbot.botcore
  (:import  [java.net Socket]
            [java.io PrintWriter InputStreamReader BufferedReader])
  (:require [clojbot.utils :as u]
            [clojure.tools.logging :as log]))


(defn network-loop
  [socketref]
  (while (nil? (:exit @socketref))
    ;;; Read in a message from the connection. Dispatch.
    (let [msg (u/read-in @socketref)]
      (log/info "IN  :: " (:original msg))
      (cond
       ;; Determine if the connection to the server was shut down.
       (re-find #"^ERROR :Closing Link:" (:original msg))
       (dosync
        (alter socketref merge {:exit true}))
       ;; If this is a ping message.
       (re-find #"^PING" (:original msg))
       (u/write-out @socketref (str "PONG "  (re-find #":.*" msg)))))))


(defn create-socket
  "Sets up a socket to connect to the IRC network defined in the server map.
  Returns a reference to a ref containing an :in and :out stream."
  [server]
  (let [socket (Socket. (:name server) (:port server))
        in     (BufferedReader. (InputStreamReader. (.getInputStream socket)))
        out    (PrintWriter. (.getOutputStream socket))
        conn   (ref {:in in :out out})]
    conn))


(defn create-bot
  [server]
  (let [socket (create-socket server)
        thread (Thread. #(network-loop socket))]
    (.start thread)
    (ref {:socket socket :thread thread :user nil :server server})))
