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


;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helper Abstractions ;;
;;;;;;;;;;;;;;;;;;;;;;;;;


(defn- connected?
  [srv-instance]
  (:connected @srv-instance))


(defn- disconnected?
  [srv-instance]
  (not (connected? srv-instance)))


(defn- registered?
  [srv-instance]
  (:registered @srv-instance))


(defn- exiting?
  [srv-instance]
  (:exiting @srv-instance))


(defn- loop-in-thread-while-alive
  [srv-instance body]
  (let [thread (Thread.
                #(while (connected? srv-instance)
                   (body)))]
    (.start thread)
    thread))


(defn- loop-in-thread-while-pred
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
  [srv-instance]
  (let [msg (read-in (:socket @srv-instance))]
    (when msg
      (log/info " IN ::" (:original msg))
      (handle-message msg srv-instance))))


(defn- process-outgoing
  [srv-instance]
  (when-let [msg (u/read-with-timeout (:out-chan @srv-instance) 5000)]
    (log/info "OUT ::" msg)
    (write-out (:socket @srv-instance) msg)))


;;;;;;;;;;;;;;;;;;;;;;
;; Message Protocol ;;
;;;;;;;;;;;;;;;;;;;;;;


(defn- handle-change-nick
  [srv-instance nickname]
  (log/debug "### ::" "Changed nick to " nickname)
  (dosync
   (alter srv-instance #(update-in % [:info :nick] (fn [_] nickname)))
   (log/info "Nickname changed to " nickname)))


(defn- handle-ping
  [srv-instance ping]
  (write-message srv-instance (str "PONG " (re-find #":.*" ping))))


(defn- handle-001
  [srv-instance]
  (log/info "### ::" "Registered for " (:humanname (:info @srv-instance)))
  (dosync
   (alter srv-instance #(assoc % :registered true))))


(defn- handle-error
  [srv-instance]
  (log/info "### ::" "Server is closing link. Exiting bot.")
  (dosync
   (alter srv-instance
          #(assoc %
             :registered false
             :connected  false))))

(defn- handle-pong
  [srv-instance msg]
  (let [pong-id (re-find #":.*" (:original msg))]
    (as/>!! (:hb-chan @srv-instance) (:original msg))))


;;; Message dispatcher. Used by read-in loop.
(defn- handle-message
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
  "Sets up a socket to connect to the IRC network defined in the server map.
  Returns a reference to a ref containing an :in and :out stream."
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
  [srv-instance]
  (register-user srv-instance)
  srv-instance)


(defn- reconnect-server
  [srv-instance]
  (log/error "### ::" "Reconnecting " (:humanname (:info @srv-instance)))
  ;; First clean up current connections, if any.
  (when (:socket @srv-instance)
    ((:cleanup (:socket @srv-instance))))
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
    (register-server srv-instance)))

(defn- monitor-server
  [srv-instance]
  (let [heartbeat (loop-in-thread-while-pred
                   srv-instance
                   (fn []
                     (heartbeat srv-instance))
                   #(not (exiting? %)))]
    (dosync
     (alter srv-instance
            #(assoc % :monitor heartbeat)))))

;;;;;;;;;;;;;;;;;;;;;;;
;; Internal Commands ;;
;;;;;;;;;;;;;;;;;;;;;;;


(defn- register-user
  [srv-instance]
  (let [nick (get-in @srv-instance [:info :nick])
        name (get-in @srv-instance [:info :name])]
    (write-message srv-instance (str "NICK " nick))
    (write-message srv-instance (str "USER " nick " 0 * :" name))))


(defn- heartbeat
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


(defn create-bot
  [serverinfos]
  (let [srv-instances (doall (map setup-server    serverinfos))
        connected     (doall (map connect-server  srv-instances))
        registered    (doall (map register-server connected))
        _             (doall (map monitor-server  registered))]
    (+ 1 2)))
