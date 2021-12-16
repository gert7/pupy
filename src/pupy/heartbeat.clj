(ns pupy.heartbeat
  (:require [pupy.payload :as pl]
            [manifold.stream :as s]
            [clojure.core.async :as a
             :refer [>! <! >!! <!! go go-loop chan buffer
                     close! thread alt! alts! alts!! timeout]]))

(defn read-heartbeat-interval
  "Retrieves the heartbeat interval from a payload"
  [op]
  (get-in op ["d" "heartbeat_interval"]))

(def intent-names
  [:guilds
   :guild-members
   :guild-bans
   :guild-emojis
   :guild-integrations
   :guild-webhooks
   :guild-invites
   :guild-voice-status
   :guild-presences
   :guild-messages
   :guild-message-reactions
   :guild-message-typing
   :direct-messages
   :direct-message-reactions
   :direct-message-typing])

(def powers-of-two
  "Infinite sequence of powers of two starting from 2^^0"
  (map #(bit-shift-left 1 %) (range)))

(def intent-map
  (zipmap intent-names powers-of-two))

(defn intent-code
  [arr]
  (reduce + 0
          (vals (select-keys intent-map arr))))

(defn put-heartbeat
  [client]
  (pl/write-pl client {"op" 1} true))

(defn identify
  [token intents status]
  {"op" 2
   "d" {"token" token
        "intents" intents
        "properties" {"$os" "linux"
                      "$browser" "pupy"
                      "$device" "pupy"}}})

(defn presence
  [status]
  {"since" nil
   "activities" []
   "status"
   (#{"online" "dnd" "idle" "invisible" "offline"} status)
   "afk" false})

(defn heartbeat-loop
  [client close time]
  (go-loop [tout (timeout time)]
    (alt!
      tout ([] (put-heartbeat client)
            (recur (timeout time)))
      close (println "heartbeat closed."))))
