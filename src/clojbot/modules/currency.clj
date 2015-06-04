(ns clojbot.modules.currency
  (:use [clojure.algo.monads])
  (:require[clj-http.client       :as client]
           [clojure.tools.logging :as log   ]
           [clojure.data.json     :as json  ]
           [clojure.edn           :as edn   ]
           [clojbot.commands      :as cmd   ]
           [clojbot.botcore       :as core  ]
           [clojbot.utils         :as u     ]
           [clojure.string        :as str   ]
           [clojure.java.io       :as io    ]))

;;;;;;;;;;;;;;;;;;;;
;; URLS AND STUFF ;;
;;;;;;;;;;;;;;;;;;;;

(def oe-url "http://openexchangerates.org/api/latest.json")

;;;;;;;;;;;;;;;;;;;;;;
;; HELPER FUNCTIONS ;;
;;;;;;;;;;;;;;;;;;;;;;


(defn- get-currency-rates
  [url apikey]
  (try (:body (client/get url {:query-params {:app_id apikey}}))
       (catch Exception e
         (log/error "GET" url "failed!\n" (.getMessage e))
         nil)))

(defn- raw-to-json
  [raw]
  (try (u/keywordize-keys (json/read-str raw))
       (catch Exception e
         (log/error "Failed to parse JSON! Given input:" raw)
         nil)))

(defn- get-currency
  [json currency-code]
  (let [base     (:base json)
        currency ((comp (keyword currency-code) :rates) json)]
    currency))


(defn- convert
  [from to amount]
  (domonad maybe-m
           [apikey     (:apikey
                        (edn/read-string
                         (slurp  (io/resource "openexchange.edn"))))
            currencies (get-currency-rates oe-url apikey)
            parsed     (raw-to-json currencies)
            from-curr  (get-currency parsed from) ;; 1 dolalr == from-curr from
            to-curr    (get-currency parsed to)]
           (* (/ amount from-curr) to-curr)))

(defn- parse-input
  [inputstring]
  (domonad maybe-m
           [prsd (re-find #"([0-9]+(\.[0-9]+)?)\s(\w+)\s(to)\s(\w+)" inputstring)
            value (read-string (nth prsd 1))
            from  (nth prsd 3)
            to    (nth prsd 5)]
           {:value value :from from :to to}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Module Implementation ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;


(core/defmodule                                                                                   
  :currency                                                                                     
  (core/defcommand                                                                                
    "curr"                                                                                        
    (fn [srv args msg]
      (log/debug "Executing handler for currency")
      (log/debug "parsed input:" (parse-input args))
      (if-let [{v :value f :from t :to} (parse-input args)]
        (cmd/send-message srv (:channel msg) (format "%.3g %s" (convert f t v) t))
        (log/debug "Could not parse valid command from ars:" args))))) 

