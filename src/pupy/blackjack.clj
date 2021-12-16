(ns pupy.blackjack
  (:require [pupy.payload :as pload :refer [send-message]]
            [clojure.core.async :as a
             :refer [>! <! >!! <!! go go-loop chan buffer
                     close! thread alt! alt!! alts! alts!! timeout]]
            [pupy.state :as pupst]))

(def card-suits
  [:hearts :clubs :diamonds :spades])

(def card-ranks
  {:2 2 :3 3 :4 4
   :5 5 :6 6 :7 7
   :8 8 :9 9 :10 10
   :J 11 :Q 12 :K 13 :A 1})

(def card-suits-strings
  {:hearts "Hearts"
   :clubs "Clubs"
   :diamonds "Diamonds"
   :spades "Spades"})

(def card-ranks-strings
  {:2 "2" :3 "3" :4 "4" :5 "5" :6 "6"
  :7 "7" :8 "8" :9 "9" :10 "10" :J "Jack"
   :Q "Queen" :K "King"})

(defn fmt-card
  [card]
  (let [suit (card 0)
        rank (card 1)]
    (str (card-ranks-strings rank) " of " (card-suits-strings suit))))

(def card-deck
  (for [suit card-suits
        rank (keys card-ranks)]
    [suit rank]))

(defn eject
  [_ dup]
  (send-message (dup :cid) "Ejecting from mode!")
  (close! (dup :closing-channel)))

(defn init
  [cont dup cid]
  (send-message
   cid
   (str "Bj mode" cont)))

(defn new-deck
  []
  (a/to-chan! (shuffle card-deck)))

(defn next-card
  [state cid]
  (let [cstate @state
        cur-deck (cstate :cur-deck)
        tried-card (if cur-deck (<!! cur-deck) nil)]
    (if tried-card tried-card
        (<!! (do (send-message cid "Shuffling deck...")
                 ((pupst/set! state :cur-deck (new-deck)) :cur-deck))))))

(defn hitme
  [_ dup]
  (let [card (next-card (dup :state) (dup :cid))]
    (fmt-card card)))

(def innermode
  {:title "Blackjack"
   :init init
   :commands {"eject" {:f eject
            :text false
            :desc "Ejects from this mode."}
   "hitme" {:f hitme
            :text true
            :desc "Hits you."}}})
