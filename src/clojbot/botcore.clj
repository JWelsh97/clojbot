(ns clojbot.botcore
  (:import  [java.net              Socket                                      ]
            [java.io               PrintWriter InputStreamReader BufferedReader]
            [java.util.concurrent  LinkedBlockingQueue TimeUnit                ]
            [java.lang             InterruptedException                        ]
            )
  (:require [clojbot.utils         :as u  ]
            [clojure.core.async    :as as ]
            [clojure.tools.logging :as log]
            [clojure.string        :as str]
            [clojure.edn           :as edn]
            [clojure.java.io       :as  io]
            [clojbot.db            :as  db]))


(declare write-message)
(declare register-user)
(declare change-nick)
(declare handle-message)
(declare heartbeat)
(declare join-channel)
(declare join-channels)
(declare add-module)

;;;;;;;;;;;;;;;;;;;;;;;;
;; Macros for Modules ;;
;;;;;;;;;;;;;;;;;;;;;;;;


(defmacro defcommand
  [trigger handler]
  `{:kind :command :command ~trigger :handler ~handler})


(defmacro defhook
  [type handler]
  `{:kind :hook :hook ~type :handler ~handler})


(defmacro defstorage
  [tablename & fields]
  `{:kind    :storage
    :handler (fn []
               (db/create-table ~tablename ~@fields))})


(defmacro defmodule
  [modulename & cmds]
  `(defn ~'load-module [srvrs#]
     (doseq [cmd# [~@cmds]]
       (let [knd#  (:kind cmd#)
             body# (:handler cmd#)]

         (if (= :storage knd#)
           (body#)
           (add-module srvrs# cmd#))))))


(defmacro store
  [table mapvalues]
  `(if (db/table-exists? ~table)
    (db/insert-into-table ~table ~mapvalues)
    (throw (ex-info "Table does not exist!"))))


(defmacro query
  [query]
  `(db/query-table ~query))



;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helper Abstractions ;;
;;;;;;;;;;;;;;;;;;;;;;;;;


(defn- exiting?
  "Returns true if the bot is shutting down. "
  [srv]
  (:exiting @srv))


(defn- connected?
  "Returns true if the current server insantance is connected with
  sockets to the server."
  [srv]
  (and (not (exiting? srv)) (:connected @srv)))


(defn- disconnected?
  "Returns false if the current server insantance is connected with
  sockets to the server."
  [srv]
  (not (connected? srv)))


(defn- registered?
  "Returns true if the current server insantance is registered on the
  irc network."
  [srv]
  (:registered @srv))


(defn- human-name
  "Gets the human readable name from the server instance."
  [srv]
  (:humanname (:info @srv)))


(defn- hookmap
  "Gets the mapping of message types to functions."
  [srv]
  (:hook @srv))


(defn- commandmap
  "Gets the mapping from commands to handler."
  [srv]
  (:command @srv))


(defn- parse-command
  "Takes a raw input message and checks if it can pick out a command.
  A command is always formatted as '~<trigger> <args>' where args can
  be empty."
  [msg]
  (try
    (when-let [regex (re-matches #"~(\w+)\s?(.+)?" (:message msg))]
      {:trigger (keyword (nth regex 1))
       :args    (nth regex 2)})
    (catch NullPointerException e nil)))


(defn- apply-handlers-of-kind-to
  "Takes a server, a keyword and arguments. For each handler that is
  associated with the keyword in the command map, the handler will be
  applied to the given arguments. In case of an exception an error message is printed to stdout."
  [srv kind & args]
  (let [handlermap (commandmap srv)
        handlers   (kind handlermap)]
    (doseq [handler handlers]
      (log/debug "Applying handler for command" kind)
      (try
        (apply handler args)
        (catch Exception e
          (log/error "Command handler " handler " threw an exception: " (.getMessage e) "\n" (.getStacktrace e)))))))

(defn- apply-hooks-of-kind-to
  "Given a kind and arguments, this function will take out all the
  handlers in the hooks map that are bound to the keyword. It will
  then apply each handler to the given arguments. In case of an
  exception the handler an error message is printed to stdout."
  [srv kind & args]
  (let [hooks    (hookmap srv)
        handlers (kind hooks)]
    (doseq [handler handlers]
      (try
        (apply handler args)
        (catch Exception e
          (log/error "Hook " handler " threw an exception: " (.getMessage e)))))))


(defn- loop-in-thread-while-pred
  "Takes a server instance, a function and a predicate. Will loop
  infinitly in a seperate thread until the predicate returns
  false. Returns the thread reference."
  [srv body pred label]
  (let [thread (Thread.
                (fn []
                  (while (pred srv)
                    (body))
                  (log/info label " loop exiting!")))]
    (.start thread)
    thread))


(defn- loop-in-future-while-pred
  "Takes a server instance, a function and a predicate. Will loop
  infinitly in a future until the predicate returns false. Returns the
  future reference."
  [srv body pred label]
  (let [future (future
                 ((fn []
                    (while (pred srv)
                      (body))
                    (log/error "Future " label  " exiting!"))))]
    future))


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
  [srv]
  (let [msg (read-in (:socket @srv))]
    (when msg
      (handle-message msg srv))))


(defn- process-outgoing
  "Processes a message from the outbound channel and writes it to the socket."
  [srv]
  (when-let [msg (u/read-with-timeout (:out-chan @srv) 5000)]
    (log/info "OUT -" (format "%15s" (human-name srv)) " - " msg)
    (write-out (:socket @srv) msg)))


;;;;;;;;;;;;;;;;;;;;;;
;; Message Protocol ;;
;;;;;;;;;;;;;;;;;;;;;;


(defn- handle-change-nick
  "Actions to take when a nickchange has been announced for the current instance."
  [srv nickname]
  (log/debug "### ::" "Changed nick to " nickname)
  (dosync
   (alter srv #(update-in % [:info :nick] (fn [_] nickname)))
   (log/info "Nickname changed to " nickname)))


(defn- handle-ping
  "Actions to take when the server sends us a ping message."
  [srv ping]
  (write-message srv (str "PONG " (re-find #":.*" ping))))


(defn- handle-001
  "Actions to take when the server sends the 001 command. 001 means
  that we have been registered on the server."
  [srv]
  (log/info "### ::" "Registered for " (-> @srv :info :humanname))
  (dosync
   (alter srv #(assoc % :registered true)))
  ;; Join the channels
  (join-channels srv))


(defn- handle-error
  "Actions to take when the server sends an ERROR command. Probably
  means disconnecting."
  [srv]
  (log/info "### ::" "Server is closing link. Exiting bot.")
  (dosync
   (alter srv
          #(assoc %
             :registered false
             :connected  false))))

(defn- handle-pong
  "Actions to take when the server sends us a pong message. Pong is a
  reply to our ping."
  [srv msg]
  (let [pong-id (re-find #":.*" (:original msg))]
    (as/>!! (:hb-chan @srv) (:original msg))))


(defn- handle-nick-taken
  "Actions to take when the server lets us know that our nickname has already
  been taken."
  [srv]
  ;; Swap the nicklist in the bot by shifting it one position.
  (dosync
   (alter
    srv #(update-in % [:info :altnicks] u/shift-left)))
  (let [next (first (-> @srv :info :altnicks))]
    (change-nick srv next)))


(defn- handle-dispatch
  "Parses a message and checks if it is a command (e.g., ~youtube
  movietitle). If it is a command the proper handlers are executed.
  Finally, all hooks are executed as well according to the type of
  this message. (e.g., privmsg)."
  [srv msg]
  (let [ ;; Determine the hook type. (e.g., PRIVMSG, PONG,..)
        irc-command (keyword (:command msg))
        command     (parse-command msg)]
    ;; If it is a syntactical valid command, find the handler if there is one.
    (when-let [{t :trigger a :args} command]
      (apply-handlers-of-kind-to srv t srv a msg))
    ;; Handle all the registered hooks for this command.
    (apply-hooks-of-kind-to srv irc-command srv msg)))


(defn- handle-message
  "Function that dispatches over the type of message we receive."
  [msg srv]
  ;; Ignored messages.
  (when-not (contains? #{"372"} (:command msg))
     (log/info " IN -" (format "%15s" (human-name srv)) " - " (:original msg)))
  (cond
   (= "NICK" (:command msg))
   (handle-change-nick srv (:message msg))
   (re-find #"^PING" (:original msg))
   (handle-ping srv (:original msg))
   (= "001" (:command msg))
   (handle-001 srv)
   (re-find #"^ERROR :Closing Link:" (:original msg))
   (handle-error srv)
   (= "PONG"  (:command msg))
   (handle-pong srv msg)
   (= "433" (:command msg))
   (handle-nick-taken srv)
   :else
   (future (handle-dispatch srv msg))))


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
          in       (BufferedReader.
                    (InputStreamReader.
                     (.getInputStream socket)))
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
  "Expects a server setup map and creates a ref srv for this
  server. Server setup map contains the keys
  {:ip :port :channels :name :nick :altnicks}"
  [serverinfo]
  (ref {:name        (:humanname serverinfo)
        :info        serverinfo
        :connected   false
        :registered  false
        :exiting     false ;; will be set to true if the bot is exiting.
        :socket      nil   ;; Sockets to communicate with the server.
        :in-chan     (as/chan) ;; Channel that has incoming messages from the server.
        :out-chan    (as/chan) ;; Channel that has outgoing messages to the server.
        :hb-chan     (as/chan) ;; Heartbeat channel.
        :command    {}        ;; Mapping of functions to triggers. Multiple
        :hook       {}})) ;; Mapping of irc commands (e.g., PRIVMSG) to a function.



(defn- connect-server
  "Takes a server, attaches a socket to it and starts the read-in and
  -out loops."
  [srv]
  (when-let [socket (create-socket srv)]
    ;; Store the socket in the server srv.
    (dosync
     (alter srv
            #(assoc %
               :socket socket
               :connected true)))
    ;; After socket is in place and connected is set to true, start loops.
    (let [out-loop  (loop-in-thread-while-pred
                     srv
                     (fn []
                       (process-outgoing srv))
                     connected?
                     :out-loop)
          in-loop  (loop-in-thread-while-pred
                    srv
                    (fn []
                      (process-incoming srv))
                    connected?
                    :in-loop)]
      (dosync
       (alter srv
              #(assoc %
                 :out-loop out-loop
                 :in-loop  in-loop)))
      srv)))


(defn- register-server
  "Registers the bot on the network."
  [srv]
  (register-user srv)
  srv)


(defn- join-channels
  "Joins the channels in the srv."
  [srv]
  (doall
   (map #(join-channel srv %)
        (-> @srv :info :channels)))
  srv)


(defn- monitor-server
  "Attaches a loop the the connection. Sends heartbeats on periodic
  intervals. Reconnects the server if a reply from the server does not
  arrive after certain time."
  [srv]
  (let [heartbeat (loop-in-thread-while-pred
                   srv
                   (fn []
                     (heartbeat srv))
                   #(not (exiting? %))
                   :heartbeat)]
    (dosync
     (alter srv
            #(assoc % :monitor heartbeat)))
    srv))


(defn- reconnect-server
  "Creates a new socket for this server instance, re-registers on the
  network and re-joins channels."
  [srv]
  (log/error "### ::" "Reconnecting " (:humanname (:info @srv)))
  ;; First clean up current connections, if any.
  (when (:socket @srv)
    (-> @srv :socket :cleanup))
  ;; Reset status.
  (dosync
   (alter srv
          #(assoc %
             :socket     nil
             :connected  false
             :registered false)))
  (connect-server srv)
  ;; Only register when connected.
  (when (connected? srv)
    (register-server srv)
    (join-channels srv)))


;;;;;;;;;;;;;;;;;;;;;;;
;; Internal Commands ;;
;;;;;;;;;;;;;;;;;;;;;;;


(defn- change-nick
  "Requests a nick change from the server."
  [srv nick]
  (write-message srv (str "NICK " nick)))


(defn- register-user
  "Register user on the network."
  [srv]
  (let [nick (-> @srv :info :nick)
        name (-> @srv :info :name)]
    (write-message srv (str "NICK " nick))
    (write-message srv (str "USER " nick " 0 * :" name))))


(defn- join-channel
  "Joins a given channel list."
  [srv channel]
  (write-message srv (str "JOIN " channel)))


(defn- heartbeat
  "Send ping messages to the server in periodic intervals and wait for
  their reply. If no reply has been sent assume connection is dead and
  attempt reconnect until succes."
  [srv]
  ;; Send the ping message only if we are registered.
  (when (registered? srv)
    (write-message srv (str "PING clojbot"))
    ;; Await for the reply on the heartbeat channel.
    ;; Only reconnect if we are not connected or registered.
    (if-let [reply (u/read-with-timeout (:hb-chan @srv) 60000)]
      ;; If a pong is received, wait for a proper amount of time.
      (Thread/sleep 60000)
      ;; If no pong is received, try a reconnect and keep trying until it
      ;; succeeds.
      (when (not (exiting? srv))
        (do (reconnect-server srv)
            (while (disconnected? srv)
              (Thread/sleep 1000)
              (reconnect-server srv)))))))


(defn add-handler
  " Adds a command or hook to the bot. This function should only be
  used by the module dispatcher!"
  [srv module]
  (let [handler  (:handler module)
        kind     (:kind module) ;; :command or a :hook
        ;; Each module is either a hook or a command.
        ;; A command has  a :command defined (e.g., "youtube")
        ;; and a hook has a hook defined (e.g., :PRIVMSG).
        ;; So the two below are mutually exclusive!
        command  (:command module)
        hook     (:hook module)
        submap   (keyword (if command command hook))]
    ;; The kind: :command or :hook
    ;; Create new channel for this module.
    ;; Insert the handler in the proper sublist in the bot.
    (dosync
     (alter srv
            (fn [srv]
              (update-in srv
                         [kind submap]
                         conj handler))))))


;;;;;;;;;
;; API ;;
;;;;;;;;;


(defn add-module
  " Takes the bot and adds a commandmap to it. This method should only
  be used by the macros! This method can not be made private. Hence,
  its in the API."
  [server-instances command]
  ;; Log it.
  (log/debug "Adding "
             (if (:command command)
               (str "command " (:command command))
               (str "hook on " (:hook command))))
  (doall
   (map #(add-handler % command) server-instances)))


(defn load-module
  "Takes the bot and the string representation of a module. Loads the
  module and attaches it to the bot."
  [srvrs name]
  (let [fullns (symbol (str "clojbot.modules." name))
        modfn  (symbol (str "clojbot.modules." name "/load-module"))]
    (require fullns :reload)
    ((resolve modfn) srvrs)))


(defn write-message
  "Puts a message in the channel of the bot. This will be then picked up by the
  loop in socket-write-loop. Expects *RAW* messages! Use abstractions provided
  in commands.clj."
  [srv message]
  (let [chan (:out-chan  @srv)]
    (as/>!! chan message)))


(defn create-bots
  "Takes a list of server infos and applies setup-server to each of
  them, resulting in a list of instances."
  [serverinfos]
  (doall
   (map setup-server serverinfos)))


(defn connect-bots
  "Takes a bot and connects to the irc network, registers the user,
  monitors the server and joins all wanted channels."
  [serverinstances]
  (doall
   (map #((comp monitor-server register-server connect-server) %)
        serverinstances)))


(defn init-bot
  "Reads the configuration file from disk and creates an instance for
  each of the servers in the configuration. This results in a list of
  servers (which are refs)."
  []
  (let [server-config (edn/read-string (slurp  (io/resource "servers.edn")))
        bot-config    (edn/read-string (slurp  (io/resource "bot.edn")))
        instances     (create-bots server-config)]
    (connect-bots instances)
    (doseq [module (:modules bot-config)]
      (load-module instances module))))
