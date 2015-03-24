(ns clojbot.botcore
  (:import  [java.net              Socket                                      ]
            [java.io               PrintWriter InputStreamReader BufferedReader]
            [java.util.concurrent  LinkedBlockingQueue TimeUnit                ]
            [java.lang             InterruptedException                        ])
  (:require [clojbot.utils         :as u  ]
            [clojure.core.async    :as as ]
            [clojure.tools.logging :as log]
            [clojure.string        :as str]))


(declare write-message)
(declare register-user)
(declare change-nick)
(declare reply-ping)
(declare handle-message)
(declare heartbeat)
(declare join-channel)


;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helper Abstractions ;;
;;;;;;;;;;;;;;;;;;;;;;;;;


(defn- connected?
  "Returns true if the current server insantance is connected with
  sockets to the server."
  [srv-instance]
  (:connected @srv-instance))


(defn- disconnected?
  "Returns false if the current server insantance is connected with
  sockets to the server."
  [srv-instance]
  (not (connected? srv-instance)))


(defn- registered?
  "Returns true if the current server insantance is registered on the
  irc network."
  [srv-instance]
  (:registered @srv-instance))


(defn- exiting?
  "Returns true if the bot is shutting down. "
  [srv-instance]
  (:exiting @srv-instance))


(defn- human-name
  "Gets the human readable name from the server instance."
  [srv-instance]
  (:humanname (:info @srv-instance)))


(defn- loop-in-thread-while-pred
  "Takes a server instance, a function and a predicate. Will loop
  infinitly in a seperate thread until the predicate returns
  false. Returns the thread reference."
  [srv-instance body pred]
  (let [thread (Thread.
                #(while (pred srv-instance)
                   (body)))]
    (.start thread)
    thread))


;;;;;;;;;;;;;;;;;;;;;;;;;
;; Raw Socket Commands ;;
;;;;;;;;;;;;;;;;;;;;;;;;;


(defn- write-out
  "Writes a raw line to the socket."
  [socket msg]
  (doto (:out socket)
    (.println (str msg "\r"))
    (.flush)))


(defn- read-in
  "Reads a line from the socket and parses it into a message
  map. Returns nil if there has been an exception reading from the
  socket or a timeout."
  [socket]
  (try
    (let [line (.readLine (:in socket))]
      (u/destruct-raw-message line))
    (catch Exception e
      nil)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Socket Communication Functions ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn- process-incoming
  "Reads a message from the incoming socket and dispatches it."
  [srv-instance]
  (let [msg (read-in (:socket @srv-instance))]
    (when msg
      (log/info " IN -" (format "%15s" (human-name srv-instance)) " - " (:original msg))
      (handle-message msg srv-instance))))


(defn- process-outgoing
  "Processes a message from the outbound channel and writes it to the socket."
  [srv-instance]
  (when-let [msg (u/read-with-timeout (:out-chan @srv-instance) 5000)]
    (log/info "OUT -" (format "%15s" (human-name srv-instance)) " - " msg)
    (write-out (:socket @srv-instance) msg)))


;;;;;;;;;;;;;;;;;;;;;;
;; Message Protocol ;;
;;;;;;;;;;;;;;;;;;;;;;


(defn- handle-change-nick
  "Actions to take when a nickchange has been announced for the current instance."
  [srv-instance nickname]
  (log/debug "### ::" "Changed nick to " nickname)
  (dosync
   (alter srv-instance #(update-in % [:info :nick] (fn [_] nickname)))
   (log/info "Nickname changed to " nickname)))


(defn- handle-ping
  "Actions to take when the server sends us a ping message."
  [srv-instance ping]
  (write-message srv-instance (str "PONG " (re-find #":.*" ping))))


(defn- handle-001
  "Actions to take when the server sends the 001 command. 001 means
  that we have been registered on the server."
  [srv-instance]
  (log/info "### ::" "Registered for " (-> @srv-instance :humanname :info))
  (dosync
   (alter srv-instance #(assoc % :registered true))))


(defn- handle-error
  "Actions to take when the server sends an ERROR command. Probably
  means disconnecting."
  [srv-instance]
  (log/info "### ::" "Server is closing link. Exiting bot.")
  (dosync
   (alter srv-instance
          #(assoc %
             :registered false
             :connected  false))))

(defn- handle-pong
  "Actions to take when the server sends us a pong message. Pong is a
  reply to our ping."
  [srv-instance msg]
  (let [pong-id (re-find #":.*" (:original msg))]
    (as/>!! (:hb-chan @srv-instance) (:original msg))))


;;; Message dispatcher. Used by read-in loop.
(defn- handle-message
  "Function that dispatches over the type of message we receive."
  [msg srv-instance]
  (cond
   (= "372" (:command msg))
   nil
   (= "NICK" (:command msg))
   (handle-change-nick srv-instance (:message msg))
   (re-find #"^PING" (:original msg))
   (handle-ping srv-instance (:original msg))
   (= "001" (:command msg))
   (handle-001 srv-instance)
   (re-find #"^ERROR :Closing Link:" (:original msg))
   (handle-error srv-instance)
   (= "PONG"  (:command msg))
   (handle-pong srv-instance msg)))


;;;;;;;;;;;;;;;;;;;;;
;; Setup Functions ;;
;;;;;;;;;;;;;;;;;;;;;


(defn- create-socket
  "Creates a socket for the given ip and port. Returns nil if
  impossible."
  [server]
  (try
    (let [ip       (get-in @server [:info :ip])
          port     (get-in @server [:info :port])
          socket   (doto (Socket. ip port)
                     (.setSoTimeout 5000))
          in       (BufferedReader. (InputStreamReader. (.getInputStream socket)))
          out      (PrintWriter. (.getOutputStream socket))
          clean-fn #(do
                      (.close socket)
                      (.close in)
                      (.close out))]
      {:in in :out out :cleanup clean-fn})
    (catch java.net.SocketException e
      nil)
    (catch java.net.UnknownHostException e
      nil)))


(defn- setup-server
  "Expects a server setup map and creates a ref srv-instance for this
  server. Server setup map contains the keys
  {:ip :port :channels :name :nick :altnicks}"
  [serverinfo]
  (ref {:info       serverinfo
        :connected  false
        :registered false
        :exiting    false
        :socket     nil
        :in-chan    (as/chan)
        :out-chan   (as/chan)
        :hb-chan    (as/chan)
        }))


(defn- connect-server
  "Takes a server, attaches a socket to it and starts the read-in and
  -out loops."
  [srv-instance]
  (when-let [socket (create-socket srv-instance)]
    ;; Store the socket in the server srv-instance.
    (dosync
     (alter srv-instance
            #(assoc %
               :socket socket
               :connected true)))
    ;; After socket is in place and connected is set to true, start loops.
    (let [out-loop  (loop-in-thread-while-pred
                     srv-instance
                     (fn []
                       (process-outgoing srv-instance))
                     connected?)
          in-loop  (loop-in-thread-while-pred
                    srv-instance
                    (fn []
                      (process-incoming srv-instance))
                    connected?)]
      (dosync
       (alter srv-instance
              #(assoc %
                 :out-loop out-loop
                 :in-loop  in-loop)))
      srv-instance)))


(defn- register-server
  "Registers the bot on the network."
  [srv-instance]
  (register-user srv-instance)
  srv-instance)


(defn- join-channels
  "Joins the channels in the srv-instance."
  [srv-instance]
  (doall (map #(join-channel srv-instance %) (-> @srv-instance :channels :info))))


(defn- monitor-server
  "Attaches a loop the the connection. Sends heartbeats on periodic
  intervals. Reconnects the server if a reply from the server does not
  arrive after certain time."
  [srv-instance]
  (let [heartbeat (loop-in-thread-while-pred
                   srv-instance
                   (fn []
                     (heartbeat srv-instance))
                   #(not (exiting? %)))]
    (dosync
     (alter srv-instance
            #(assoc % :monitor heartbeat)))))


(defn- reconnect-server
  "Creates a new socket for this server instance, re-registers on the
  network and re-joins channels."
  [srv-instance]
  (log/error "### ::" "Reconnecting " (:humanname (:info @srv-instance)))
  ;; First clean up current connections, if any.
  (when (:socket @srv-instance)
    (-> @srv-insance :cleanup :socket))
  ;; Reset status.
  (dosync
   (alter srv-instance
          #(assoc %
             :socket     nil
             :connected  false
             :registered false)))
  (connect-server srv-instance)
  ;; Only register when connected.
  (when (connected? srv-instance)
    (register-server srv-instance)
    (join-channels srv-instance)))


;;;;;;;;;;;;;;;;;;;;;;;
;; Internal Commands ;;
;;;;;;;;;;;;;;;;;;;;;;;


(defn- register-user
  "Register user on the network."
  [srv-instance]
  (let [nick (-> @srv-instance :info :nick)
        name (-> @srv-instance :info :name)]
    (write-message srv-instance (str "NICK " nick))
    (write-message srv-instance (str "USER " nick " 0 * :" name))))

(defn join-channel
  "Joins a given channel list."
  [srv-instance channel]
  (write-message srv-instance (str "JOIN " channel)))


(defn- heartbeat
  "Send ping messages to the server in periodic intervals and wait for
  their reply. If no reply has been sent assume connection is dead and
  attempt reconnect until succes."
  [srv-instance]
  ;; Send the ping message.
  (write-message srv-instance (str "PING clojbot"))
  ;; Await for the reply on the heartbeat channel.
  ;; Only reconnect if we are not connected or registered.
  (if-let [reply (u/read-with-timeout (:hb-chan @srv-instance) 60000)]
    (Thread/sleep 60000)
    (do (reconnect-server srv-instance)
        (while (disconnected? srv-instance)
          (Thread/sleep 1000)
          (reconnect-server srv-instance)))))


;;;;;;;;;
;; API ;;
;;;;;;;;;


(defn write-message
  "Puts a message in the channel of the bot. This will be then picked up by the
  loop in socket-write-loop. Expects *RAW* messages! Use abstractions provided
  in commands.clj."
  [srv-instance message]
  (let [chan (:out-chan  @srv-instance)]
    (as/>!! chan message)))


(defn create-bots
  [serverinfos]
  
  (let [srv-instances (doall (map setup-server    serverinfos))
        _             (doall (map connect-server  srv-instances))
        _             (doall (map register-server srv-instances))
        _             (doall (map monitor-server  srv-instances))
        _             (doall (map join-channels   srv-instances))]
    nil))
