(ns stor-api.orm
  (:require [clojure.string :as string]
            [clojure.core.match :refer [match]]
            [honeysql.core :as sql]
            [honeysql.format :as sqlfmt]
            [honeysql-postgres.format :as pgfmt]
            [stor-api.data :as data]
            [clj-time.core :as time]
            [clj-time.coerce :as timecast]
            [clj-time.format :as timefmt]
            [taoensso.nippy :as nippy]
            [clojure.java.jdbc :as db]))

(def tables (atom #{}))
(def db-spec {:connection-uri "jdbc:postgresql:stor"})

(defn prret [x] (println x) x)

(extend-protocol sqlfmt/ToSql
  org.joda.time.DateTime
  (to-sql [v] (sqlfmt/to-sql (timecast/to-sql-time v))))

(defn space
  [& strs]
  (string/join " " strs))

;; TRUSTED FUNCTIONS: DO NOT RUN ON UNTRUSTED INPUT

(defn select-type
  [type]
  (case type
    :id "BIGSERIAL PRIMARY KEY"
    :id-fk "BIGINT PRIMARY KEY"
    :str "TEXT"
    :i32 "INTEGER"
    :i64 "BIGINT"
    :f32 "REAL"
    :f64 "DOUBLE PRECISION"
    :bignum "DECIMAL"
    :timestamp "TIMESTAMP WITHOUT TIME ZONE"
    :bin "BYTEA"
    :json "JSONB"))

(defn parse-attr
  [attr]
  (match attr
         [:foreign-key table-name & _] (format "REFERENCES %s(id)" table-name)
         [:polymorph table-name & _] (format "NOT NULL REFERENCES %s(id) ON DELETE CASCADE ON UPDATE CASCADE"
                                             table-name)
         :delete-cascade "ON DELETE CASCADE"
         :update-cascade "ON UPDATE CASCADE"
         :primary-key "PRIMARY KEY"
         :nullable "NULL"
         :non-null "NOT NULL"
         :unique "UNIQUE"))

(defn parse-field
  [field]
  (space (select-type (first field))
         (apply space (map parse-attr (rest field)))))

(defn gen-field
  [[key spec]]
  (format "%s %s" (name key) (parse-field spec)))

(defn gen-create
  [spec]
  (format "CREATE TABLE %s ( %s );"
          (:table-name spec)
          (string/join ", " (mapv gen-field (:fields spec)))))

(defn create-table!
  [spec]
  (when (not (contains? @tables spec))
    (swap! tables conj spec) ;; MT-unfriendly
    (when (:polymorph spec)
      (create-table! (data/find-spec @data/specs (:polymorph spec))))
    (doseq [parent (data/foreign-keys spec)]
      (create-table! (data/find-spec @data/specs parent)))
    (db/execute! db-spec (gen-create spec))))

(defn create-tables!
  [specs]
  (doseq [spec specs]
    (create-table! spec)))

(defn gen-drop
  [spec]
  (format "DROP TABLE %s;" (:table-name spec)))

(defn drop-table!
  [spec]
  (when (contains? @tables spec)
    (swap! tables disj spec) ;; MT-unfriendly
    (db/execute! db-spec (gen-drop spec))))

(defn drop-tables!
  [specs]
  (doseq [spec specs]
    (drop-table! spec)))

(defn list-tables
  []
  (map #(data/find-spec @data/specs (:table_name %))
       (db/query db-spec ["SELECT table_name FROM information_schema.tables WHERE table_schema='public'"])))

;; END TRUSTED FUNCTIONS

(defn gen-join
  [spec]
  (println spec)
  (println (disj (data/find-heirarchy @data/specs spec) spec))
  (vec (mapcat (fn [{name :table-name parent :polymorph}]
                 (let [build-key #(keyword (str % ".id"))]
                   (when (and name parent) ;; TODO: Join to parents
                     [(keyword name) [:= (build-key name) (build-key parent)]])))
               (disj (data/find-heirarchy @data/specs spec) spec))))

(defn get-id
  ([spec id]
   (get-id spec id {}))
  ([spec id opts]
   (db/query db-spec
             (->> {:select [(keyword (str (:table-name spec) ".*"))]
                   :from (keyword (:table-name spec))
                   :left-join (gen-join spec)
                   :where [:= :id id]}
                  (merge opts)
                  (sql/format)))))

(defn get-all
  ([spec]
   (get-all spec {}))
  ([spec opts]
   (println (-> {:select [(keyword (str (:table-name spec) ".*"))]
                  :from [(keyword (:table-name spec))]
                  :left-join (gen-join spec)}
                 (merge opts)
                 (sql/format)))
   (db/query db-spec
             (-> {:select [(keyword (str (:table-name spec) ".*"))]
                  :from [(keyword (:table-name spec))]
                  :left-join (gen-join spec)}
                 (merge opts)
                 (sql/format)))))

(defn update!
  [spec obj]
  (assert (:id obj))
  (db/execute! db-spec
               (sql/format {:update (keyword (:table-name spec))
                            :set (dissoc obj :id :type)
                            :where [:= :id (:id obj)]})))

(defn map-first
  [f coll]
  (cons (f (first coll)) (rest coll)))

(defn mapv-first
  [f coll]
  (vec (cons (f (first coll)) (rest coll))))

(defn insert!
  ([spec obj]
   (db/with-db-connection [conn db-spec]
     (db/with-db-transaction [tx conn]
       (insert! spec obj (:type spec) tx))))
  ([spec obj type tx]
   (let [parent (if-let [parent (:polymorph spec)]
                  (insert! (data/find-spec @data/specs parent) obj type tx) {})]
     (first (db/query tx
                      (->> {:insert-into (keyword (:table-name spec))
                            :values (as-> obj x
                                      (dissoc x :id :type)
                                      (assoc x :type type)
                                      (into parent x)
                                      (filter #(contains? (:fields spec) (% 0)) x)
                                      (into {} x)
                                      (vector x))
                            :returning [:*]}
                           (sql/format)
                           (mapv-first #(string/replace % "() VALUES ()" "DEFAULT VALUES"))))))))
