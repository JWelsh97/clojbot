(ns clojbot.core
  (:require [clojure.tools.logging :as log]
            [clojbot.utils         :as u]
            [clojbot.commands      :as cmd]
            [clojbot.botcore       :as core]
            [clojure.core.async    :as as]
            [clojure.edn           :as edn]
            [clojbot.db            :as db]
            [clojbot.youtube       :as yt])
  (:gen-class))

;;;;;;;;;;;;;;;;;;;;;;;
;; Temporary Modules ;;
;;;;;;;;;;;;;;;;;;;;;;;


;; (def youtubesearch {:name :youtube-search
;;                     :type :PRIVMSG
;;                     :handler (fn [srv message]
;;                                ;; Iff there is a query do a search, fetch it
;;                                ;; using a regex.
;;                                (when-let [query  (nth
;;                                                   (re-matches #".*?!youtube\s(.+)"
;;                                                               (:original message))
;;                                                   1)]
;;                                  (let [{r :result e :error} (yt/search query)]
;;                                    ;; Send the first result, properly formatted,
;;                                    ;; back to the channel.  If there are no
;;                                    ;; results, say so.
;;                                    (if e
;;                                      ;; If there was an exceptoin, print it out.
;;                                      (cmd/send-message srv
;;                                                        (:channel message)
;;                                                        (str "An error occured: " e))
;;                                      ;; If there was no exception, check if
;;                                      ;; there is an empty list.  If not, take
;;                                      ;; the first result.
;;                                      (cmd/send-message srv
;;                                                        (:channel message)
;;                                                        (if (empty? r)
;;                                                          "No search results!"
;;                                                          (let [hit   (first r)
;;                                                                title (:title hit)
;;                                                                url   (:url hit)]
;;                                                            (str title " :: " url))))))))})

(def youtubesearch {:kind    :command
                    :command "youtube"
                    :handler (fn [srv args msg]
                               (log/debug "Executing youtube handler: " args)
                               (let [{r :result e :error} (yt/search args)]
                                 ;; Send the first result, properly formatted,
                                 ;; back to the channel.  If there are no
                                 ;; results, say so.
                                 (if e
                                   ;; If there was an exceptoin, print it out.
                                   (cmd/send-message srv
                                                     (:channel msg)
                                                     (str "An error occured: " e))
                                   ;; If there was no exception, check if
                                   ;; there is an empty list.  If not, take
                                   ;; the first result.
                                   (cmd/send-message srv
                                                     (:channel msg)
                                                     (if (empty? r)
                                                       "No search results!"
                                                       (let [hit   (first r)
                                                             title (:title hit)
                                                             url   (:url hit)]
                                                         (str title " :: " url)))))))})

(def echo {:kind    :command
           :command "echo"
           :handler (fn [srv args msg]
                      (cmd/send-message srv (:channel msg) args))})

(def privhook {:kind :hook
               :hook :PRIVMSG
               :handler (fn [srv message]
                          (log/debug "Got a PRIVMSG:" (:message message)))})


;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Entrypoint for the Bot ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;; TODO

;;; - A bot is still an instance connected to a single server. Abstract away in
;;;   such a way that the user seems to handle a single bot and not multiple
;;;   bots.
;;; - Create seperate file for YouTube module!
;;; - Create Karma Module (to make sure DB architecture is on point)
;;; - Use regex to filter server to attach to.
;;; - Make write message accept any number of strings as final params.
;;; - Cleanup youtube.clj?



(defn -main
  [& args]
  (let [bots (core/init-bot)]
    (core/add-module bots youtubesearch)
    (core/add-module bots echo)
    (core/add-module bots privhook)))
