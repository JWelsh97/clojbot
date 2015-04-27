(ns clojbot.modules.google
  (:require [clojure.tools.logging :as log]
            [clojbot.commands      :as cmd]
            [cemerick.url          :as url]
            [clj-http.client       :as client]))	


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
        (let [furl  (url/url-decode (nth matches 1))
              fname (nth matches 2)]
          {:url furl :title fname})))))

(def googlesearch {:kind    :command
                   :command "google"
                   :handler (fn [srv args msg]
                              (log/debug "Searching Google for " args)
                              (let [results (search args)]
                                (if results
                                  (cmd/send-message srv (:channel msg) (str (:title results) " :: " (:url results)))
                                  (cmd/send-message srv (:channel msg) "No results found!"))))})

