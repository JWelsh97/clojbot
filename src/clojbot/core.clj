(ns clojbot.core
  (:require [clojure.tools.logging :as log   ]
            [clojbot.utils         :as u     ]
            [clojbot.commands      :as cmd   ]
            [clojbot.botcore       :as core  ]
            [clojure.core.async    :as as    ]
            [clojure.edn           :as edn   ]
            [clojbot.db            :as db    ]
            [cemerick.url          :as url   ]
            [clj-http.client       :as client]
            [clojure.pprint                  ])
  (:gen-class))

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
;;; - Bots/servers should be singular!


(defn -main
  [& args]
  (let [bot (core/init-bot)]))
