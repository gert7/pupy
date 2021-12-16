(ns pupy.urlencoded
  (:require [clojure.string :as string]))

(defn kv
  [keyval]
  (str (key keyval) "=" (val keyval)))

(defn join
  [kvs]
  (string/join "&" (map kv kvs)))

(defn format-urlencoded
  [fields]
  (if
   (empty? fields) ""
   (str "?" (join fields))))
