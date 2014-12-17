(ns hivewing-images.git-hooks
  (:require [hivewing-core.hive-image-notification :as hin]
            [taoensso.timbre :as logger])
  (:gen-class))

(defn -main
  "This is called when you receive a POST via gitolite.
  It should queue up a message in SQS to update things internally
  based on this new information"
  [& argv]

  (let [[hook hive-uuid] argv]
    (case hook
      "post-receive" (hin/hive-images-notification-send-images-update-message hive-uuid)
      "default" (logger/error "This is comfusing. This is not a valid hook" hook)
      )))
