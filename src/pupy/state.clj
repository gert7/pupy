(ns pupy.state)

(defn set!
  "Sets an element in a map inside an atom."
  [state key value]
  (let [cstate @state]
    (reset! state (conj cstate {key value}))))
