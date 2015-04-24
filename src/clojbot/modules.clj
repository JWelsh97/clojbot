;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; (ns clojbot.modues)                                                       ;;
;;                                                                           ;;
;;                                                                           ;;
;;                                                                           ;;
;; (defmodule                                                                ;;
;;   ;;; Create a hook that hooks into PRIVMSG or any other type of message. ;;
;;   (defhook :PRIVMSG                                                       ;;
;;     (fn [message sender channel]                                          ;;
;;       (println "handle hook on privmsg")))                                ;;
;;                                                                           ;;
;;   (defaction "~echo"                                                      ;;
;;     (fn [args sender channel]                                             ;;
;;       (println "echoing args"))))                                         ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;