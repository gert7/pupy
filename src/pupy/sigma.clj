(ns pupy.sigma
  (:require [clojure.string :as string]
            [pupy.payload :as pl]))

(def sigma-rules (atom {}))

(defn decode-sigma-rule
  [st]
  (let [number (re-find #"rule (\d+):" (string/lower-case st))
        rule (or
              (re-find #"Rule \d+: (.*)" st)
              (re-find #"rule \d+: (.*)" st))]
    {(number 1) st}))

(defn get-messages
  []
  (pl/discord-get "/channels/872139935258378280/messages" {"limit" 100}))

(defn format-messages
  [msg-list]
  (map #(decode-sigma-rule (%1 "content")) msg-list))

(defn retrieve-sigma-rules!
  []
  (reset! sigma-rules
          (reduce conj {} (format-messages (get-messages)))))

(defn fetch-sigma-rule!
  [key]
  (or
   (@sigma-rules key)
   (do (retrieve-sigma-rules!)
       (@sigma-rules key))))
