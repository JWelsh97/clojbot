(defproject clojbot "0.1.0-SNAPSHOT"
  :main clojbot.core
  :description "Clojure bot for IRC."
  :url "https://github.com/m1dnight/clojbot"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"                                      ]
                 [org.slf4j/slf4j-log4j12 "1.7.9"                                  ]
                 [log4j/log4j "1.2.17" :exclusions [javax.mail/mail
                                                    javax.jms/jms
                                                    com.sun.jmdk/jmxtools
                                                    com.sun.jmx/jmxri             ]]
                 [org.clojure/tools.logging "0.3.1"                                ]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"                  ]
                 [org.clojure/java.jdbc "0.3.6"                                    ]
                 [postgresql "9.1-901.jdbc4"                                       ]
                 ;; Youtube Search
                 [com.google.api-client/google-api-client "1.19.1"                 ]
                 [com.google.apis/google-api-services-youtube "v3-rev104-1.18.0-rc"]
                 [com.google.http-client/google-http-client-jackson "1.20.0"       ]
                 [clj-http "1.1.1"                                                 ]
                 [com.cemerick/url "0.1.1"                                         ]
                 [org.clojure/data.json "0.2.6"                                    ]
                 [clj-time "0.9.0"                                                 ]]
  :plugins [[codox "0.8.11"]]
  :java-source-paths ["java"]
  :resource-paths ["conf"]
  :aot [:all])
