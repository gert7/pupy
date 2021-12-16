(defproject pupy "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/math.numeric-tower "0.0.4"]
                 [byte-streams "0.2.4"]
                 [tupelo "21.06.03b"]
                 [aleph "0.4.6"]
                 [org.clojure/core.async "1.3.618"]
                 [org.clojure/tools.namespace "1.1.0"]
                 [org.clojure/data.csv "1.0.0"]
                 [org.clojure/data.json "2.3.1"]
                 [clojure-opennlp "0.5.0"]]
  :main ^:skip-aot pupy.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})
