(defproject org.clojars.akiel/datomic-tools "0.2-SNAPSHOT"
  :description "Tools to work with Datomic."
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojars.akiel/datomic-spec "0.1-alpha2"]
                 [org.clojure/clojure "1.9.0-alpha15"]]

  :profiles {:dev {:dependencies [[com.datomic/datomic-free "0.9.5407"]]}})
