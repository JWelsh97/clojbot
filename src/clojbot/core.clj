(ns clojbot.core
  (:require [clojbot.botcore       :as core  ]
            [clojbot.utils         :as u     ]
            [clojure.tools.logging :as log   ] 
            [clj-http.client       :as client]
            [clojure.data.json     :as json  ]
            [clojure.core.reducers :as r     ]
            [clj-time.format       :as f     ]
            [clj-time.core         :as t     ]
            [clj-time.local        :as l     ])
  (:gen-class))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Entrypoint for the Bot ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;; TODO

;;; - A bot is still an instance connected to a single server. Abstract away in
;;;   such a way that the user seems to handle a single bot and not multiple
;;;   bots.
;;; - Create seperat file for YouTube module!
;;; - Create Karma Module (to make sure DB architecture is on point)
;;; - Use regex to filter server to attach to.
;;; - Make write message accept any number of strings as final params.
;;; - Cleanup youtube.clj?
;;; - Bots/servers should be singular!
;;; - Find a better regex for URLS!



(defn -main
  [& args]
  (let [bot (core/init-bot)]
    (println bot)))
