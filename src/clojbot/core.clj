(ns clojbot.core
  (:require [clojure.tools.logging :as log]
            [clojbot.utils         :as u]
            [clojbot.commands      :as cmd]
            [clojbot.botcore       :as core]
            [clojure.core.async    :as as]
            [clojure.edn           :as edn]
            [clojbot.db            :as db]
            [clojbot.youtube       :as yt]
            [cemerick.url          :as url]
            [clj-http.client       :as client])
  (:gen-class))

;;;;;;;;;;;;;;;;;;;;;;;
;; Temporary Modules ;;
;;;;;;;;;;;;;;;;;;;;;;;


(def ^:dynamic header {
                       "User-Agent"  "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:24.0) Gecko/20100101 Firefox/24.0"
                       "Accept" "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
                       "Accept-Encoding" "gzip, deflate"
                       "Accept-Language" "en-US,en;q=0.5"
                       "Connection" "keep-alive"})

(defn search
  "Gets the first search result form google, given a search term in
  query."
  [query]
  (let [url "http://www.google.com/search?q="
        charset "UTF-8"
        useragent "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:24.0) Gecko/20100101 Firefox/24.0"
        search (str url (url/url-encode query))]
    ;; Get the source of the searchquery result.
    (let [resp    (client/get search {:headers header})
          matches (re-find #"<h3.*?><a href=\"(.+?)\" .*?>(.*?)<\/a>" (:body resp))]
      (when matches
        (let [furl  (nth matches 1)
              fname (nth matches 2)]
          {:url furl :title fname})))))


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


(def googlesearch {:kind :command
                   :command "google"
                   :handler (fn [srv args msg]
                              (log/debug "Searching Google for " args)
                              (let [results (search args)]
                                (if results
                                  (cmd/send-message srv (:channel msg) (str (:title results) " :: " (:url results)))
                                  (cmd/send-message srv (:channel msg) "No results found!"))))})


(def echo {:kind    :command
           :command "echo"
           :handler (fn [srv args msg]
                      (cmd/send-message srv (:channel msg) args))})


(def privhook {:kind :hook
               :hook :PRIVMSG
               :handler (fn [srv message]
                          (log/debug "Got a PRIVMSG:" (:message message)))})
:q


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
    (core/add-module bots googlesearch)
    (core/add-module bots echo)
    (core/add-module bots privhook)))
