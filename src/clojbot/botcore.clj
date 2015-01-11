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
  "Dispatches a message received from the IRC server to the proper
  channels (i.e., modules). The message is sent to all modules that are
  registered under a certain message. E.g., :JOIN, or :PRIVMSG"
  [msg botstate]
  (let [cmd (keyword (:command msg))]
    (doall
     (map #(as/>!! % msg)
          (cmd (:in-channels @botstate))))))


(defn- socket-read-loop
  "Loops on the socket and reads when a line is available from the IRC server."
  [botstate]
  (while (nil? (:exit @botstate))
    ;; Read in a message from the connection. Dispatch.
    (let [msg (read-in (:socket @botstate))]
      ;; If it is our own message drop it.
      (log/info "IN  :: " (:original msg))
      (cond
       ;; Server said we are disconnecting.
       (re-find #"^ERROR :Closing Link:" (:original msg))
       (dosync (alter botstate  merge {:exit true}))
       ;; Server sent a ping, reply with a ping.
       (re-find #"^PING" (:original msg))
       (write-out (:socket @botstate) (str "PONG "  (re-find #":.*" (:original msg))))
       :else
       (future (dispatch-message msg botstate)))))
  (log/info "socket-read-loop done"))


;;; TODO Make this function timeout.
(defn- socket-write-loop
  "As long as :exit in the botstate is nil it will read from the out-channel.
  Messages are stored here in raw format to send to the IRC server."
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
  "Creates a bot and returns a ref containg all needed data."
  [server user]
  (let [socket   (create-socket server)
        botstate (ref
                  {:socket      socket
                   :user        user
                   :out-channel (as/chan)
                   :in-channels {:PRIVMSG []}
                   })
        ;; Run the read and write socket each in their own thread.
        in-loop  (Thread. #(socket-read-loop botstate))
        out-loop (Thread. #(socket-write-loop botstate))]
    (.start in-loop)
    (.start out-loop)
    botstate))


(defn destroy-bot
  "Kill the bot by swapping the :exit value to true."
  [bot]
  (dosync
   (alter bot (fn [b] (assoc b :exit true)))))


(defn write-message
  "Puts a message in the channel of the bot. This will be then picked up by the
  loop in socket-write-loop. Expects *RAW* messages! Use abstractions provided
  in commands.clj."
  [botstate message]
  (let [chan (:out-channel  @botstate)]
    (as/>!! chan message)))


(defn connect-module
  "Attaches a module to a running bot by adding the channel to the bot's state.
   Runs the module function in a loop and keeps feeding it messages."
  [botstate handler]
  (let [type     (:type handler)
        modfn    (:handler handler)
        mod-chan (as/chan)] ;; Create new channel for this module.
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
          (modfn botstate msg))))))
