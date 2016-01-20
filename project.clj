(defproject org.clojars.akiel/ring-hap "0.5-SNAPSHOT"
  :description "Ring Middleware for Hypermedia Application Protocol."
  :url "https://github.com/alexanderkiel/ring-hap"

  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [ring/ring-core "1.3.2" :exclusions [clj-time
                                                      commons-codec
                                                      commons-fileupload
                                                      commons-io
                                                      crypto-equality
                                                      crypto-random
                                                      org.clojure/tools.reader]]
                 [org.clojars.akiel/transit-schema "0.4"
                  :exclusions [org.clojure/clojurescript
                               com.cognitect/transit-cljs]]]

  :profiles {:test {:dependencies [[juxt/iota "0.2.0"]]}})
