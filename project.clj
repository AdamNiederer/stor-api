(defproject stor-api "0.0.1-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]

                 [org.clojure/core.match "0.3.0-alpha5"]
                 [org.clojure/core.async "0.4.474"]

                 [org.clojure/java.jdbc "0.7.5"]
                 [org.postgresql/postgresql "42.2.0"]
                 [org.xerial/sqlite-jdbc "3.21.0.1"]

                 [honeysql "0.9.1"]
                 [nilenso/honeysql-postgres "0.2.3"]

                 [clj-time "0.14.2"]
                 [com.taoensso/nippy "2.14.0"]

                 [io.pedestal/pedestal.service "0.5.3"]

                 ;; Remove this line and uncomment one of the next lines to
                 ;; use Immutant or Tomcat instead of Jetty:
                 [io.pedestal/pedestal.jetty "0.5.3"]
                 ;; [io.pedestal/pedestal.immutant "0.5.3"]
                 ;; [io.pedestal/pedestal.tomcat "0.5.3"]

                 ;; [ch.qos.logback/logback-classic "1.1.8" :exclusions [org.slf4j/slf4j-api]]
                 ;; [org.slf4j/jul-to-slf4j "1.7.22"]
                 ;; [org.slf4j/jcl-over-slf4j "1.7.22"]
                 ;; [org.slf4j/log4j-over-slf4j "1.7.22"]
                 ]
  :min-lein-version "2.0.0"
  :resource-paths ["config", "resources"]
  ;; If you use HTTP/2 or ALPN, use the java-agent to pull in the correct alpn-boot dependency
  ;:java-agents [[org.mortbay.jetty.alpn/jetty-alpn-agent "2.0.5"]]
  :profiles {:dev {:aliases {"run-dev" ["trampoline" "run" "-m" "stor-api.server/run-dev"]}
                   :dependencies [[io.pedestal/pedestal.service-tools "0.5.3"]]}
             :plugins [[cider/cider-nrepl "0.16.0"]]
             :uberjar {:aot :all}}

  :main ^{:skip-aot true} stor-api.server)
