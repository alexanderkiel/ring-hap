(defproject org.clojars.akiel/ring-hap "0.2-SNAPSHOT"
  :description "Ring Middleware for Hypermedia Application Protocol."
  :url "https://github.com/alexanderkiel/ring-hap"

  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.7.0"]
                 [ring/ring-core "1.3.2" :exclusions [clj-time
                                                      commons-codec
                                                      commons-fileupload
                                                      commons-io
                                                      crypto-equality
                                                      crypto-random
                                                      org.clojure/tools.reader]]
                 [com.cognitect/transit-clj "0.8.275"
                  :exclusions
                  [com.fasterxml.jackson.datatype/jackson-datatype-json-org
                   com.googlecode.json-simple/json-simple]]
                 [com.fasterxml.jackson.core/jackson-core "2.3.2"]
                 [org.clojars.akiel/transit-schema "0.1"
                  :exclusions [org.clojure/clojurescript]]])
