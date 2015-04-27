(ns clojbot.modules.torrentleech
  (:require[clj-http.client       :as client]
           [clojure.tools.logging :as log   ]           
           [clojure.edn           :as edn   ]
           [clojbot.commands      :as cmd   ]
           [clojbot.botcore       :as core  ]
           [clojbot.utils         :as u     ]))

;;;;;;;;;;;;;;;;;;;;;;;
;; TORRENTLEECH URLS ;;
;;;;;;;;;;;;;;;;;;;;;;;

(def ^:dynamic header {
                       "User-Agent"  "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:24.0) Gecko/20100101 Firefox/24.0"
                       "Accept" "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
                       "Accept-Encoding" "gzip, deflate"
                       "Accept-Language" "en-US,en;q=0.5"
                       "Connection" "keep-alive"})
(def ^:dynamic loginurl "http://torrentleech.org/user/account/login/")
(def ^:dynamic profileurl "http://torrentleech.org/profile/%s/#profileTab")
(def ^:dynamic homepage "http://torrentleech.org/torrents/browse/")

(def ^:dynamic replyformat "Ratio: %s Up: %s Down: %s")

(def ^:dynamic cookies nil)

;;;;;;;;;;;;;;;;;;;;;;
;; HELPER FUNCTIONS ;;
;;;;;;;;;;;;;;;;;;;;;;


(defn- valid-profile?
  "Checks if the given page source constitues a valid profile."
  [pagesource]
  (not (re-find #".*Profile.*does\snot\sexist.*" pagesource)))


(defn- logged-in?
  "Requests the home page of torrentleech.org to see if the
  credentials are valid."
  []
  (let [source (client/get "http://torrentleech.org/torrents/browse/")]
    (.contains (:body source) "/user/account/logout")))


(defn- do-login
  "Logs in. Assumes that the calls are wrapped in a binding for
  cookiejars (clj-http.core/*cookie-store*)."
  [username password]
      (client/post loginurl {:form-params  {"username" username "password" password}}))


(defn- get-userinfo
  "Scrapes the userinfo from the torrentleech profile page.
   Assumes a binding of cookiejar(clj-http.core/*cookie-store*)."
  [username]
  (log/info "Getting userinfo for " username)
  (let [source (:body (client/get (format profileurl (str username))
                                  {:throw-exceptions false}))]
    (if (valid-profile? source)
      (let [ratio (nth  (re-find #"<b>Ratio:</b>\s(.*?)<" source) 1)
            up    (nth  (re-find #"Up:</b></span>\s(.*?)<" source) 1)
            down  (nth  (re-find #"Down:</b></span>\s(.*?)<" source) 1)]
        {:ratio ratio :up up :down down})
      {:error "Invalid user!"})))


;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Module Implementation ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;


(core/defmodule
  :echomodule
  (core/defcommand
    "u"
    (fn [srv args msg]
      (let [credentials (edn/read-string (slurp "conf/torrentleech.edn"))]
        (binding  [clj-http.core/*cookie-store* (clj-http.cookies/cookie-store)]
          (do-login (:username credentials) (:password credentials))
          (if (logged-in?)
            (when-let [info (get-userinfo (if args args (u/sender-nick msg)))]
              (if (:error info)
                (cmd/send-message srv (:channel msg) (:error info))
                (cmd/send-message srv (:channel msg) (format replyformat (:ratio info) (:up info) (:down info)))))
            (cmd/send-message srv (:channel msg) "Euhm, I can't seem to log in :<")))))))
