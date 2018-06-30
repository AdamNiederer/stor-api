(ns stor-api.data
  (:require [clojure.core.match :refer [match]])
  (:require [clojure.set :as set]))

(defn prret [x] (prn x) x)

(defmacro defmemoized [name & rest]
  `(def ~name (memoize (fn ~@rest))))

(defmacro when->> [form & rest]
  `(when ~form (->> ~form ~@rest)))

(defmacro when-> [form & rest]
  `(when ~form (-> ~form ~@rest)))

(defn one [obj]
  [:i64 [:foreign-key obj :non-null]])

(defn one? [obj]
  [:i64 [:foreign-key obj] :nullable])

(defmemoized foreign-keys
  [spec]
  (filter identity (map #(match % [:foreign-key v] v _ nil)
                        (mapcat #(get % 1) (:fields spec)))))

(defmemoized find-spec
  [specs name]
  (some #(when (= name (:table-name %)) %) specs))

(defmemoized find-children
  [specs self]
  (set/union #{self} (set (mapcat #(find-children specs %)
                                  (filter #(= (:table-name self) (:polymorph %)) specs)))))

(defmemoized find-parents
  [specs self]
  (set/union #{self} (when->> (:polymorph self)
                       (find-spec specs)
                       (find-parents specs))))

(defmemoized find-heirarchy
  [specs self]
  (set/union (find-parents specs self) (find-children specs self)))

(defmemoized find-fields
  [specs self]
  (apply set/union (map :fields (find-heirarchy specs self))))

(defmemoized foreign-keys
  [spec]
  (filter identity (map #(match % [:foreign-key v] v _ nil)
                        (mapcat #(get % 1) (:fields spec)))))

(def specs (atom #{}))

(defn inject-spec
  [name spec]
  (-> spec
      (assoc :table-name (str name))
      (assoc :type (hash (str name)))
      (assoc-in [:fields :id] (if-let [parent (:polymorph spec)]
                                [:i64 :primary-key [:polymorph parent]]
                                [:id]))
      (assoc-in [:fields :type] [:i32 :non-null])))

(defmacro defspec
  ([name]
   `(defspec ~name {}))
  ([name spec]
   `(def ~name
      ;; Use unbound var for filename by parsing string repr
      (let [s# ~(inject-spec name spec)]
        (swap! specs conj s#)
        s#))))

(defspec inode)

(defspec directory
  {:polymorph "inode"
   :fields {:parent (one? "directory")
            :name [:str :non-null]
            :created [:timestamp :non-null]}})

(defspec file
  {:polymorph "inode"
   :fields {:parent (one? "directory")
            :name [:str :non-null]
            :created [:timestamp :non-null]}})
