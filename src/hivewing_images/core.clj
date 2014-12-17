(ns hivewing-images.core
  (:require
            [hivewing-core.hive-image-notification :as hin]
            [hivewing-core.hive-image :as hi]
            [taoensso.timbre :as logger]
            [hivewing-core.hive :as hive]
            [hivewing-core.configuration :as config]
            [hivewing-core.worker :as worker]
            [hivewing-core.worker-config :as wc]
            [hivewing-core.beekeeper :as bk]
            [hivewing-core.public-keys :as pk]
            [amazonica.aws.sqs :as sqs]
            )
  (:gen-class))

(comment
  image-branch (:image_branch (hive/hive-get hive-uuid))
  (packaged-hive-image-url hive-uuid "master")
  (update-worker-image-refs hive-uuid "master")
  (def hive-uuid "dfc5d2c4-7972-11e4-8732-b334ee7e2863")
  (hi/hive-images-send-images-update-message hive-uuid)
  )

(defn packaged-hive-image-url
  [hive-uuid branch-name]
  ; Resolve branch name to a ref
  (logger/info "Looking up hive " hive-uuid " @ branch " branch-name)
  (let [resolved-ref   (hi/hive-image-resolve-ref hive-uuid branch-name)]
    (logger/info "Resolved branch to " resolved-ref)
    ; Package if needed this ref for deploy on S3
    ; return the URL for the packaged image
    (try
    (if (hi/hive-image-packaged? hive-uuid resolved-ref)
      (do
        (logger/info "Packaged alrady.")
        (hi/hive-image-package-url hive-uuid resolved-ref))
      (do
        (logger/info "Packaging now...")
        (hi/hive-image-package-image hive-uuid resolved-ref)))
    (catch Exception e (println "Error: " (.getMessage e))))))

(defn update-worker-image-refs
  "You need to get the image ref from the repository and match it against
  the branch name"
  [hive-uuid branch-name]
  (logger/info "Updating the worker image references")
  (let [package-url (packaged-hive-image-url hive-uuid branch-name)]
    (logger/info "Updating hive " hive-uuid " @ branch " branch-name " with " package-url)
    (hive/hive-update-hive-image-url hive-uuid package-url)))

(defn hive-update-processing
  "Process the updates that need to occur when a hive was updated.
  This is mainly, re-adding the gitolite repo config file
  and then testing the image with the latest hive-image-target
  If the hive is not found, delete the config from the gitolite system"
  [hive-uuid]
  (let [hive (hive/hive-get hive-uuid)]
    (if hive
      (do
        (hi/hive-image-write-access-config-file hive-uuid)
        (update-worker-image-refs hive-uuid (:image_branch hive)))
      (hi/hive-image-delete-access-config-file hive-uuid))
  ))
(defn beekeeper-update-processing
  " Sets the public keys for a user. This allows them to be found by the various
   repository config files. If there is no beekeeper, it will find no
  public keys and will then delete all the keys (and add none). "
  [bk-uuid]
  (let [public-keys (map :key (pk/public-keys-for-beekeeper bk-uuid))]
    (logger/info "Updating " (count public-keys) " keys for user " bk-uuid)
    (hi/hive-image-set-beekeeper-public-keys bk-uuid public-keys)))

(defn image-update-processing
  "The image was updated (pushed) and we should look and see if we need
   to update the workers with new image URLs"
  [hive-uuid]
  (let [hive (hive/hive-get hive-uuid)]
    (if hive
      ; Update it!
      (update-worker-image-refs hive-uuid (:image_branch hive))
      ; Delete it if we don't have the record in the system.
      (hi/hive-image-delete-access-config-file hive-uuid))))

(defn worker-update-processing
  "This tries to update the individual worker with the correct .hive-image"
  [worker-uuid]
  (let [worker  (worker/worker-get worker-uuid)
        hive-uuid (:hive_uuid worker)
        image-branch (:image_branch (hive/hive-get hive-uuid))
        image-url (packaged-hive-image-url hive-uuid image-branch)]
    (wc/worker-config-set-hive-image worker-uuid image-url)))

(defn process-incoming-message
  "The messages are received by the system and processed here"
  [msg]
  (doseq [msg-key (keys msg)]
    (let [data (get msg msg-key)]
      (logger/info "Processing " msg-key " : " data)
      (try
        (case msg-key
          :hive-update (hive-update-processing data)
          :beekeeper-update (beekeeper-update-processing data)
          :image-update (image-update-processing data)
          :worker-update (worker-update-processing data))
        (catch Exception ex
          (logger/error (str "Error: " (.getMessage ex))))))))

(defn -main
  "Start up the subscribe loop and try to process any incoming messages"
  [& args]
  (println "Starting hivewing-images process")
  (let [incoming-queue (hin/hive-images-notification-sqs-queue)]
    (logger/info "Incoming queue: " incoming-queue)
    (while true
      (try
        (let [msgs (:messages (sqs/receive-message config/sqs-aws-credentials
                                       :queue-url incoming-queue
                                       :wait-time-seconds 1
                                       :max-number-of-messages 10
                                       :delete false))]
          (if (empty? msgs)
            (Thread/sleep 500)
            (do
              (logger/info "Received " (count msgs) " messages")
              (logger/info "received " msgs)

              (hi/with-gitolite
                (doseq [packed-msg msgs]
                  ; Unpack it - it's just prn-str for now.
                  (let [msg (read-string (:body packed-msg))]
                    ; Process
                    (process-incoming-message msg)
                    ; Delete
                    (sqs/delete-message config/sqs-aws-credentials incoming-queue (:receipt-handle packed-msg))))))))
        (catch Exception e (logger/error "Exception: " (.getMessage e)))))))
