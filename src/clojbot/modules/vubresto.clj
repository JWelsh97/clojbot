(ns clojbot.modules.vubresto
  (:require [clojbot.botcore       :as core  ]
            [clojbot.commands      :as cmd   ]
            [clojbot.utils         :as u     ]
            [clojure.tools.logging :as log   ] 
            [clj-http.client       :as client]
            [clojure.data.json     :as json  ]
            [clojure.core.reducers :as r     ]
            [clj-time.format       :as f     ]
            [clj-time.core         :as t     ]
            [clj-time.local        :as l     ]))

;;;;;;;;;;;;;
;; HELPERS ;;
;;;;;;;;;;;;;

(def ^:dynamic resto-url-nl "http://178.62.199.83/vubresto/etterbeek.nl.json")
(def ^:dynamic resto-url-en "http://178.62.199.83/vubresto/etterbeek.en.json")

(defn- get-page
  "Requests a page source and returns error if it failed."
  [url & args]
  (try (:body (apply client/get url args))
       (catch Exception e
         (log/error "GET" url "failed!\n" (.getMessage e))
         {:error "Error making request. Check logs."})))

(defn get-resto-json
  "Requests the json data and returns it parsed into clojure
  datastructures."
  ([]
   (get-resto-json "en"))
  ([lang]
   
   (let [language (cond (= "nl" lang) "nl" :else "en") ;; make sure we dont construct invalid vars
         response (get-page (var-get (ns-resolve 'clojbot.modules.vubresto (symbol (str "resto-url-" language)))))]
     (when (not (:error response))
       (u/keywordize-keys (json/read-str response))))))


(defn find-today
  "Takes the parsed json data and returns the map that holds the
  restaurant data for today."
  [jsn]
  (let [todaystring (f/unparse  (f/formatter "yyy-MM-dd") (l/local-now))]
    ;; Return first because we expect only a single result.
    (first (filter  #(= (:date %) todaystring) jsn))))


(defn menu-to-string
  [menu-array]
   (clojure.string/join "  ~  "  (map #(:dish %) (:menus menu-array))))

;;;;;;;;;;;;;;;;;;;;;;;;
;; MODULE DECLARATION ;;
;;;;;;;;;;;;;;;;;;;;;;;;

(core/defmodule
  :vubresto
  (core/defcommand
    "fret"
    (fn [srv args msg]
      (let [today (find-today (get-resto-json args))]
        (if today
          (cmd/send-message srv (:channel msg) (menu-to-string today))
          (cmd/send-message srv (:channel msg) "Error getting data. Go go gadget debugger!"))))))
