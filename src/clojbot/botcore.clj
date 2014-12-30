(ns clojbot.botcore
  (:import  [java.net             Socket]
            [java.io              PrintWriter InputStreamReader BufferedReader]
            [java.util.concurrent LinkedBlockingQueue TimeUnit]
            [java.lang            InterruptedException])
  (:require [clojbot.utils         :as u]
            [clojure.core.async    :as as]
            [clojure.tools.logging :as log]))


(defn- write-out 
  "Writes a raw line to the socket."
  [socket msg]
  (log/info "OUT :: " msg)
  (doto (:out socket)
    (.println (str msg "\r"))
    (.flush)))


(defn- read-in
  "Reads a line from the socket and parses it into a message map."
  [socket]
  (u/destruct-raw-message (.readLine (:in socket))))


(defn- dispatch-message
  [msg botstate]
  (let [cmd (keyword (:command msg))]
    (doall (map #(as/>!! % msg) (cmd (:in-channels @botstate))))))


(defn- socket-read-loop
  "Loops on the socket and reads when a line is available."
  [botstate]
  (while (nil? (:exit @botstate))
    ;;; Read in a message from the connection. Dispatch.
    (let [msg (read-in (:socket @botstate))]
      (log/info "IN  :: " (:original msg))
      (cond
       (re-find #"^ERROR :Closing Link:" (:original msg))
       (dosync (alter botstate  merge {:exit true}))
       (re-find #"^PING" (:original msg))
       (write-out (:socket @botstate) (str "PONG "  (re-find #":.*" (:original msg))))
       :else
       (future (dispatch-message msg botstate)))))
  (log/info "socket-read-loop done"))


;;; TODO Make this function timeout.
(defn- socket-write-loop
  "Loops on a queue of messages to write out to the socket."
  [botstate]
  (while (nil? (:exit @botstate))
    (when-let [out-msg (as/<!! (:out-channel  @botstate))]
      (write-out (:socket @botstate) out-msg)))
  (log/info "socket-write-loop done"))


(defn- create-socket
  "Sets up a socket to connect to the IRC network defined in the server map.
  Returns a reference to a ref containing an :in and :out stream."
  [server]
  (let [socket (Socket. (:name server) (:port server))
        in     (BufferedReader. (InputStreamReader. (.getInputStream socket)))
        out    (PrintWriter. (.getOutputStream socket))]
    {:in in :out out}))


(defn init-bot
  [server user]
  (let [socket   (create-socket server)
        botstate (ref {:socket socket :user user :out-channel (as/chan) :in-channels {:PRIVMSG []}})
        in-loop  (Thread. #(socket-read-loop botstate))
        out-loop (Thread. #(socket-write-loop botstate))]
    (.start in-loop)
    (.start out-loop)
    botstate))


(defn destroy-bot
  [bot]
  (dosync
   (alter bot (fn [b] (assoc b :exit true)))))


(defn write-message
  "Writes a message to the server. Expects RAW messages! Use abstractions
  provided in commands.clj."
  [botstate message]
  (let [chan (:out-channel  @botstate)]
    (as/>!! chan message)))


(defn- eat-messages
  [])

(defn connect-module
  [botstate handler]
  (let [type     (:type handler)
        mod-chan (as/chan)]
    ;; Add the module's channel to the proper submap.
    (dosync
     (alter botstate (fn [b]
                       (update-in b
                        [:in-channels type]
                        conj mod-chan))))
    ;; Start of a future that will run this module.
    (future
      (while (nil? (:exit @botstate))
        (when-let [msg (as/<!! mod-chan)]
          ((:handler handler) botstate msg))))))
