(defproject qasac "0.1.5-SNAPSHOT"
  :description "Stacks and Queues Language for Programming in Nature"
  :url "http://pin.synaesmedia.net/"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/core.async "0.2.385"] ]
  :main ^:skip-aot qasac.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
