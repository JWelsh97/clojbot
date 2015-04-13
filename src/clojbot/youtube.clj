(ns clojbot.youtube
  (:require [clojure.pprint]
            [clojure.tools.logging :as log]
            [clojure.edn           :as edn])
  (:import [com.google.api.services.youtube        YouTube]
           [com.google.api.services.youtube.model  ResourceId SearchListResponse SearchResult Thumbnail]
           [com.google.api.client.googleapis.json  GoogleJsonResponseException]
           [com.google.api.client.http HttpRequest HttpRequestInitializer]
           [com.google.api.client.http.javanet     NetHttpTransport]
           [com.google.api.client.json.jackson2    JacksonFactory]))



(defn- urlify
  "Takes a YouTube video ID and returns an URL pointing to it."
  [videoid]
  (str "https://www.youtube.com/watch?v=" videoid))


(defn- parse-result
  "Takes a SearchResult class that has had 'bean' applied to it. Just
  pick out the pieces of information we want."
  [searchres]
  {:title (get-in searchres [:snippet "title"])
   :url    (urlify (get-in searchres [:id "videoId"]))})


(defn- parse-error-message
  "Since the error message  is so convoluted the error message is parsed in
  this function. Returns a simple string representing a human readable
  error message."
  [ex]
  (str (.getCode (.getDetails ex)) " : " (.getMessage (.getDetails ex))))


(defn search
  "Takes a search term and searches YouTube by keyword.  Returns a
  lazy sequence of search results wrapped in a map. {:result seq} in
  case of success, return {:error message} in case of failure.  Each
  search result is a map {:title :url}. Based on the example that can
  be found on
  https://developers.google.com/youtube/v3/code_samples/java#search_by_keyword.
  Requires an API key that is set in `conf/youtube.edn`."
  [searchterm]
  (let [;; Try to slurp the api key. If no key is found (file missing) the
        ;; default is nil.  This will cause unauthenticated searches and might
        ;; fail.
        apikey   (try
                   (:apikey (edn/read-string (slurp "conf/youtube.edn")))
                   (catch java.io.FileNotFoundException e
                     (log/error "No conf/youtube.edn file found! Using unauthenticated mode!")
                     nil))
        ;; Create the object that is needed to make YouTube Data Api requests.
        ;; The last argument (reify) is required. But we do not need to
        ;; initialize anything when the http request is initialize, we make it a
        ;; no-op.
        yt     (.build (.setApplicationName
                        (new com.google.api.services.youtube.YouTube$Builder
                             (new NetHttpTransport)
                             (new JacksonFactory)
                             (reify HttpRequestInitializer
                               (initialize [this request])))
                        "clojbot-video-search"))
        ;; Define the API request.
        search  (doto  (.list (.search yt) "id,snippet")
                  (.setKey apikey)      ; Set API key.
                  (.setQ searchterm)    ; Set searchterm
                  (.setType "video")    ; We want videos.
                  ;;Set the fields we want to fetch (more info:
                  ;;https://developers.google.com/youtube/v3/getting-started#fields)
                  (.setFields "items(id/kind,id/videoId,snippet/title,snippet/thumbnails/default/url)")
                  (.setMaxResults 1)) ;; Limit the results.
        result (try  (.execute search)
                     (catch GoogleJsonResponseException e
                       (log/error "Service error getting results from youtube: " (parse-error-message e))
                       {:error (parse-error-message e)}))]
    ;; Map a bean to generate a map, and then reduce the results using mapify
    ;; over the result to obtain a proper map.
    (if (:error result)
      result
      {:result  (map (comp parse-result bean) (iterator-seq (.iterator (.getItems result))))})))
