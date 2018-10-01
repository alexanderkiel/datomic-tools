(defproject org.clojars.akiel/datomic-tools "0.4"
  :description "Tools to work with Datomic"
  :url "https://github.com/alexanderkiel/datomic-tools"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :min-lein-version "2.0.0"
  :pedantic? :abort

  :dependencies [[org.clojars.akiel/datomic-spec "0.2"]
                 [org.clojure/clojure "1.9.0"]
                 [org.clojure/core.specs.alpha "0.2.44"]]

  :profiles {:dev {:dependencies [[com.datomic/datomic-free "0.9.5697"]]}})
