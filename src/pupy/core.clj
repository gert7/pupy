(ns pupy.core
  (:require
   [pupy.heartbeat :as hbeat]
   [pupy.payload :as pload :refer [send-message]]
   [pupy.blackjack :as blackjack]
   [pupy.sigma :as sigma]
   [aleph.http :as http]
   [manifold.deferred :as d]
   [manifold.stream :as s]
   [clojure.math.numeric-tower :as math]
   [byte-streams :as bs]
   [clojure.data.json :as json]
   [clojure.java.io :as io]
   [clojure.data.csv :as csv]
   [clojure.string :as string]
   [tupelo.core :refer [spy]]
   ; [opennlp.pprint :as pprint]
   [opennlp.nlp :as nlp]
   [opennlp.treebank :as treebank]
   [clojure.core.async :as a
    :refer [>! <! >!! <!! go go-loop chan buffer
            close! thread alt! alt!! alts! alts!! timeout]])
  (:gen-class))

(require '[clojure.tools.namespace.repl :refer [refresh]])

(def discord-websocket-uri
  "wss://gateway.discord.gg/?v=9&encoding=json")

(def get-sentences (nlp/make-sentence-detector "models/en-sent.bin"))
(def tokenize (nlp/make-tokenizer "models/en-token.bin"))
; (def detokenize (nlp/make-detokenizer "models/english-detokenizer.xml"))
(def pos-tag (nlp/make-pos-tagger "models/en-pos-perceptron.bin"))
(def name-find (nlp/make-name-finder "models/en-ner-person.bin"))
(def date-find (nlp/make-name-finder "models/en-ner-time.bin"))
(def chunker (treebank/make-treebank-chunker "models/en-chunker.bin"))

(defn opcode-10
  [client]
  (pload/read-pl-direct client))

(defn take-hello
  [client]
  (hbeat/read-heartbeat-interval (opcode-10 client)))

(defn pupy-eval
  [cont _]
  (try
    (eval (read-string cont))
    (catch Exception e (str "Caught exception: " (.getMessage e)))))

(defn eval-format
  [result]
  (if (nil? result)
    "nil"
    result))

(defn format-reminder
  [cont dup]
  (go
    (let [inlet (string/split cont #" ")]
      (<! (timeout 500))
      (pload/send-message
       (dup :cid)
       (str "Ok, I'll remind "
            (string/join " "
                         (map #(cond
                                 (= % "you") "me"
                                 (= % "me") "you"
                                 (= % "myself") "you"
                                 (= % "yourself") "myself"
                                 :else %) inlet)))))))

(defn pupy-bark
  [_ _]
  (if (> (rand) 0.95) ":b:ark!" "Bark!"))

(defn pupy-chunk
  [cont _]
  (if (< (count cont) 1)
    "String too short!"
    (pr-str (chunker (pos-tag (tokenize cont))))))

(defn pupy-name
  [cont _]
  (pr-str (name-find (tokenize cont))))

(defn pupy-date
  [cont _]
  (pr-str (date-find (tokenize cont))))

(defn pupy-reboot
  [_ dup]
  (send-message (dup :cid) "Closing!")
  (a/close! (dup :closing-channel)))

(defn pupy-block-forever
  [cont dup]
  (send-message (dup :cid) "Blocking...")
  (<!! (chan)))

(defn pupy-sample-channeled
  [cont dup ch]
  (>!! ch "what is up dramalert nation")
  (>!! ch cont))

(defn pupy-sigma-rule
  [cont dup]
  (sigma/fetch-sigma-rule! cont))

(def commands
  {:title "Main"
   :commands {"bark" {:f pupy-bark
                      :text true
                      :desc "Bark or 5% chance of ultra rare secret bark."
                      :exact true}
              "chunk" {:f pupy-chunk
                       :text true
                       :desc "Splits parts of a sentence up into
                   grammatical components."}
              "names" {:f pupy-name
                       :text true
                       :desc "Tries to find people's names in noun phrase position
                   a sentence."}
              "times" {:f pupy-date
                       :text true
                       :desc "Tells you about breakfast."}
              "reboot" {:f pupy-reboot
                        :text false
                        :desc "Shuts down pupy and reboots him after 3 seconds."}
              "remind" {:f format-reminder
                        :text false
                        :desc "Intelligently reminds you something at some point
                     in time."}
              "eval" {:f pupy-eval
                      :text true
                      :desc "Evaluates Clojure."
                      :enabled false}
              "block" {:f pupy-block-forever
                       :text false
                       :desc "Blocks forever."
                       :enabled false}
              "chantest" {:f pupy-sample-channeled
                          :channeled true
                          :desc "Test channeled functions"
                          :enabled false}
              "bj" {:enter-mode blackjack/innermode
                    :desc "BJ mode."}
              "rule" {:f pupy-sigma-rule
                      :text true
                      :desc "Prints the sigma rule"
                      :enabled true}}})

(def command-not-found
  "Command not found!")

(declare manage-discord-loop)

; TODO get this to work
(defn channeled-command-discord
  "Execute commands that send their outbound messages to a
   core.async channel, without caring about the platform."
  [f cont dup]
  (let [ch (chan (a/dropping-buffer 16))]
    (f cont dup ch)
    (a/close! ch)
    (loop [msg (<!! ch)]
      (if msg
        (do (pload/send-message (dup :cid) msg)
            (recur (<!! ch)))
        nil))))

(defn call-command
  [cont dup mode]
  (println "call-command")
  (println mode)
  (let [contsplit (string/split cont #" ")
        cmdname (contsplit 1)
        command (get-in mode [:commands cmdname])
        command-rest (string/join " " (subvec contsplit 2))]
    (if (or
         (nil? command)
         (= (command :enabled) false)
         (and (command :exact) (> (count contsplit) 2)))
      ; command not found
      (send-message (dup :cid) (pr-str (keys (mode :commands))))
      (cond
        (command :enter-mode)
        ; manage-discord-loop returns a go channel, so
        ; we can't nest these reliably without explicitly
        ; waiting for it
        (<!! (manage-discord-loop
              (dup :down)
              (dup :up)
              nil
              (conj
               (command :enter-mode)
               {:rest command-rest
                :cid (dup :cid)})))
        (command :channeled)
        (channeled-command-discord (command :f) command-rest dup)
        (command :text)
        (send-message (dup :cid) ((command :f) command-rest dup))
        :else ((command :f)
               command-rest
               dup)))))

(defn color
  [r g b]
  (+ b (bit-shift-left g 8) (bit-shift-left r 16)))

(defn format-command
  [s cmd]
  (let [cmdname (first cmd)
        cmdmap (last cmd)]
    (str s
         "**" cmdname "** "
         (or (cmdmap :desc) "") "\n")))


(defn format-commands
  [command-list]
  (reduce format-command ""
          (filter
           #(not= ((val %) :enabled) false)
           (command-list :commands))))

(defn show-help
  [command-list cid]
  (pload/send-message-custom
   cid
   {"embed" {"title" (str "Mode: " (command-list :title))
             "color" (color 0 160 128)
             "description" (format-commands command-list)}}))

(defn handle-message
  [data dup command-list]
  (let [cont (data "content")
        cid (data "channel_id")]
    (println "handle-message")
    (cond
      (= cont "pupy help")
      (show-help command-list cid)

      (string/starts-with? cont "pupy ")
      (call-command cont dup command-list))))

(defn handle-input
  [payload dupe command-list]
  (let [t (or (payload "t") "nil")
        msg (get-in payload ["d" "content"])
        dup (conj dupe {:cid (get-in payload ["d" "channel_id"])})]
    (cond
      (= (payload "op") 1)
      (hbeat/put-heartbeat (dup :up))

      (and (= t "MESSAGE_CREATE") (string/starts-with? msg "pupy "))
      (handle-message (payload "d") dup command-list)

      :else
      (println (str "Unknown gateway type " t)))))

(defn manage-discord-loop
  [down up timeout-time command-list]
  (println "Managing discord loop: ")
  (println command-list)
  (let [closing-channel (chan)
        state (atom {})
        init-fn (command-list :init)
        dup {:down down
             :up up
             :closing-channel closing-channel
             :state state}]
    (if timeout-time
      (hbeat/heartbeat-loop up closing-channel timeout-time)
      nil)
    (if init-fn
      (init-fn (command-list :rest) dup (command-list :cid)) nil)
    (go-loop []
      (alt!
        down ([jspl]
              (if (nil? jspl)
                (do
                  (println "Socket closed!")
                  (a/close! closing-channel))
              ; else
                (let [payload (pload/read-pl jspl)]
                  (handle-input payload
                                dup
                                command-list)
                  (recur))))
        closing-channel ([] (println (str "Channel closed.")))))))

(defn ident
  [presence]
  (conj (hbeat/identify pload/discord-bot-token
                        (hbeat/intent-code [:guilds :guild-messages])
                        (hbeat/presence "online")) {"presence" presence}))

(defn establish-discord
  [down up]
  (pload/write-pl up (ident (hbeat/presence "online"))))

(defn start-ws-client
  [uri]
  (let [client
        @(http/websocket-client uri)
        client-chan (chan)
        hbeat-interval (take-hello client)]
    (s/connect client client-chan)
    (pload/reset-sequence-number!)
    (sigma/retrieve-sigma-rules!)
    (let [dchan
          (manage-discord-loop client-chan client hbeat-interval commands)]
      (establish-discord client-chan client)
      dchan)))

(def global-kill-channel (chan))

(defn start-discord
  []
  (go-loop [dchan (start-ws-client discord-websocket-uri)]
    (alt!
      dchan ([] (<! (timeout 3000))
                (recur (start-ws-client discord-websocket-uri)))
      global-kill-channel ([] (println "Program finished.")))))

(defn handler [req]
  {:status 200
   :headers {"content-type" "text/plain"}
   :body "Hello!"})

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (start-discord))

(start-discord)
