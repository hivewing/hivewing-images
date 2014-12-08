(ns hivewing-images.git-hooks
  (:require [hivewing-core.hive-image :as core-hive-image]
            [taoensso.timbre :as logger])
  (:gen-class))

(defn -main
  "This is called when you receive a POST via gitolite.
  It should queue up a message in SQS to update things internally
  based on this new information"
  [& argv]
    (println "Hi" argv)

;lein exec -pe "(do \
;  (require 'hivewing-core.hive-image) \
;  (let [hive-uuid \"$GL_REPO\"] \
;  (hivewing-core.hive-image/hive-images-set-update-message hive-uuid)))"
  )
