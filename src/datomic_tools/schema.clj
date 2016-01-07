(ns datomic-tools.schema
  (:use plumbing.core)
  (:require [datomic.api :as d]
            [schema.core :as s :refer [Keyword Str Any]]))

(defn enum [enum]
  {:db/id (d/tempid :db.part/user)
   :db/ident enum})

(defmacro func [name doc params & code]
  `{:db/id (d/tempid :db.part/user)
    :db/ident (keyword '~name)
    :db/doc ~doc
    :db/fn (d/function '{:lang "clojure" :params ~params
                         :requires [[clojure.core.reducers]]
                         :code (do ~@code)})})

(def Def
  [Any])

(def DefItem
  [(s/one Keyword "attr")
   (s/one Keyword "type")
   s/Any])

(def TxMap
  {:db/id Any
   Any Any})

(def TxData
  [Any])

(defn- assoc-opt [opt]
  (case opt
    :id [:db/unique :db.unique/identity]
    :unique [:db/unique :db.unique/value]
    :index [:db/index true]
    :fulltext [:db/fulltext true]
    :many [:db/cardinality :db.cardinality/many]
    :comp [:db/isComponent true]))

(s/defn ^:private assoc-opts :- TxMap
  [entity-map :- TxMap opts :- [Keyword]]
  (into entity-map (map assoc-opt) opts))

(s/defn ^:private build-attr-map :- TxMap
  [entity-name :- Str def-item :- DefItem]
  (let [[attr type & more] def-item
        [opts doc] (if (string? (last more))
                     [(butlast more) (last more)]
                     [more])]
    (-> {:db/id (d/tempid :db.part/db)
         :db/ident (keyword entity-name (name attr))
         :db/valueType (keyword "db.type" (name type))
         :db/cardinality :db.cardinality/one
         :db.install/_attribute :db.part/db}
        (assoc-opts opts)
        (assoc-when :db/doc doc))))

(defn build-function [entity-name def-item]
  (update-in def-item [:db/ident] #(keyword (str entity-name ".fn") (name %))))

(s/defn ^:private def-item-tx-builder [entity-name :- Str]
  (fn [def-item]
    (cond
      (sequential? def-item)
      (build-attr-map entity-name def-item)

      (:db/fn def-item)
      (build-function entity-name def-item)

      :else def-item)))

(s/defn ^:private build-entity-tx :- TxData
  [tx :- TxData name :- Keyword def :- Def]
  (into tx (map (def-item-tx-builder (clojure.core/name name))) def))

(s/defn build-tx :- TxData [entities :- {Keyword Def}]
  (reduce-kv build-entity-tx [] entities))
