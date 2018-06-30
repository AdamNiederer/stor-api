(ns stor-api.service
  (:require [clojure.pprint :refer [pprint]]
            [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.interceptor.helpers :as interceptor]
            [ring.util.response :as ring-resp]
            [cheshire.core :as json]
            [stor-api.orm :as orm]
            [stor-api.data :as models]
            [stor-api.serde :as serde]))

(def ^:const api-interceptors [(body-params/body-params)
                               http/json-body
                               (interceptor/on-response ring-resp/response)])

(defn api-get [{{spec-name :model} :path-params :as req}]
  (let [model (models/find-spec @models/specs spec-name)]
    (vec (map #(serde/ser model %) (orm/get-all model {:limit 100})))))
(defn api-post [{{spec-name :model} :path-params json :json-params :as req}]
  (let [model (models/find-spec @models/specs spec-name)]
    (orm/insert! model (serde/de model json))))
(defn api-get-id [req] [])
(defn api-post-id [req] [])
(defn api-put-id [req] [])
(defn api-delete-id [req] [])
(defn api-get-rel [req] [])

(def routes #{["/api/:model" :get (conj api-interceptors api-get) :route-name :model-get]
              ["/api/:model" :post (conj api-interceptors api-post) :route-name :model-post]
              ["/api/:model/:id" :get (conj api-interceptors api-get-id) :route-name :model-get-id]
              ["/api/:model/:id" :post (conj api-interceptors api-post-id) :route-name :model-post-id]
              ["/api/:model/:id" :put (conj api-interceptors api-put-id) :route-name :model-put-id]
              ["/api/:model/:id" :delete (conj api-interceptors api-delete-id) :route-name :model-delete-id]
              ["/api/:model/:id/:rel" :get (conj api-interceptors api-get-rel) :route-name :model-get-rel]})

;; Consumed by stor-api.server/create-server
;; See http/default-interceptors for additional options you can configure
(def service {:env :prod
              ::http/routes routes
              ::http/resource-path "/public"
              ::http/type :jetty
              ::http/port 8080
              ::http/container-options {:h2c? true
                                        :h2? false
                                        :ssl? false}})
