(ns pupy.payload
  (:require [clojure.data.json :as json]
            [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [aleph.http :as http]
            [manifold.stream :as s]
            [tupelo.core :refer [spy]]
            [byte-streams :as bs]
            [pupy.urlencoded :as ue]
            [clojure.string :as string]))

(def discord-rest-uri
  "https://discord.com/api/v9")

(def discord-bot-token
  "MzU2ODc2NTIxODI4NTE1ODQx.WbbdKQ.oO4PQeOoqE00yFEFRpzy9_0-evU")

(def http-headers
  {"User-Agent" "DiscordBot www.pupy.bark, v1"
   "Content-Type" "application/json"
   "Authorization" (str "Bot " discord-bot-token)})

(def sequence-number (atom nil))

(defn timestamp-csv
  "Adds timestamp column to a single csv line"
  [valp]
  (conj valp (str (java.util.Date.))))

(defn skim-packet
  "Takes a map and leaves only the keys that are in a Discord Gateway packet."
  [raw-payload]
  (select-keys raw-payload ["t" "s" "op" "d"]))

(defn logup
  [tag payload]
  (with-open [r (io/writer "out.csv" :append true)]
    (csv/write-csv r
                   [(timestamp-csv
                     (conj (vals (skim-packet payload)) tag))])))

(defn read-sequence-number!
  [payload]
  (reset! sequence-number
          (or (payload "s") @sequence-number)))

(defn read-pl
  [jspl]
  (let [payload (json/read-str jspl)]
    (read-sequence-number! payload)
    (logup "in" payload)
    payload))

(defn read-pl-direct
  [client]
  (read-pl @(s/take! client)))

(defn write-pl-inner
  [payload seqn?]
  (let [seqn @sequence-number
        seqline (if seqn? {"d" seqn} nil)]
    (json/write-str (spy (conj payload seqline)))))

(defn write-pl
  "Sends payload to client. If seqn? is true, will overwrite
   the value of d with the current sequence number."
  ([client payload seqn?]
   (let [json-out (write-pl-inner payload seqn?)]
     (logup "out" payload)
     @(s/put! client json-out)))

  ([client payload]
   (write-pl client payload false)))

(defn reset-sequence-number!
  []
  (reset! sequence-number nil))

(defn channel-string
  [channel]
  (str discord-rest-uri
       (str "/channels/" channel "/messages")))

(defn discord-get
  [uri fields]
  (json/read-str
   (byte-streams/convert
    (@(http/get
       (str discord-rest-uri uri (ue/format-urlencoded fields))
       {:headers http-headers}) :body) String)))

(defn discord-post
  [uri fields]
  (json/read-str
   (byte-streams/convert
    (@(http/post
       (str discord-rest-uri uri)
       {:headers http-headers
        :body (json/write-str fields)}) :body) String)))

(defn send-message-custom
  [channel fields]
  @(http/post
    (channel-string channel)
    {:headers http-headers
     :body (json/write-str
            (conj {"tts" false} fields))}))

(defn send-message
  [channel message]
  (send-message-custom channel {"content" message}))

(defn read-message
  [channel msg]
  (byte-streams/convert (@(http/get
                           (spy (str (channel-string channel) "/" msg))
                           {:headers http-headers}) :body) String))
