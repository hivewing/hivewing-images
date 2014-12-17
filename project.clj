(defproject hivewing-images "0.1.0"
  :description "Hivewing Images gitolite manager worker"
  :url "http://images.hivewing.io"
  :dependencies [
                 [org.clojure/clojure "1.6.0"]
                 [environ "1.0.0"]
                 [hivewing-core "0.1.3-SNAPSHOT"]
                ]

  :plugins [[s3-wagon-private "1.1.2"]
            [lein-environ "1.0.0"]]
  :repositories [["hivewing-core" {:url "s3p://clojars.hivewing.io/hivewing-core/releases"
                                   :username "AKIAJCSUM5ZFGI7DW5PA"
                                   :passphrase "UcO9VGAaGMRuJZbgZxCiz0XuHmB1J0uvzt7WIlJK"}]]
  :uberjar-name "hivewing-images-%s.uber.jar"
  :main ^:skip-aot hivewing-images.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
