(ns stor-api.serde
  (:require [clojure.string :as string]
            [taoensso.nippy :as nippy]
            [clj-time.core :as time]
            [clj-time.coerce :as timecast]
            [clj-time.format :as timefmt]
            [clojure.java.jdbc :as jdbc]
            [honeysql.format :as sqlfmt]))

(defn ser-el [spec val] val)

(defn de
  [spec json-map]
  json-map)

(defn ser
  [spec json-map]
  (into {} (map (fn [[k v]] [k (ser-el (get-in [:fields k] spec) v)])
                json-map)))
