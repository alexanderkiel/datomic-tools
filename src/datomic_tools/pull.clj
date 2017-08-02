(ns datomic-tools.pull
  (:require [clojure.spec :as s]
            [datomic.api :as d]
            [datomic-spec.core :as ds]))

(s/fdef append-db-id :args (s/cat :pattern ::ds/pattern))

(defn- append-db-id
  "Appends the attribute :db/id to patterns which contain attributes from audit
  namespace."
  [pattern]
  (let [pattern' (into [] (remove #(and (keyword? %) (= "audit" (namespace %)))) pattern)]
    (if (not= (count pattern') (count pattern))
      (conj pattern' :db/id)
      pattern)))

(s/fdef transform-attr-spec :args (s/cat :attr-spec ::ds/attr-spec))

(declare transform-pattern)

(defn- transform-attr-spec
  [attr-spec]
  (if (map? attr-spec)
    (into {} (map (fn [[k v]] [k (transform-pattern v)])) attr-spec)
    attr-spec))

(s/fdef transform-pattern :args (s/cat :pattern ::ds/pattern))

(defn transform-pattern
  "Transforms a pattern to support things like audit data fetching."
  [pattern]
  (append-db-id (mapv transform-attr-spec pattern)))

(defn find-id-attr [db attrs]
  (when (seq attrs)
    (d/q '[:find ?a . :in $ [?a ...] :where [?a :db/unique :db.unique/identity]]
         db attrs)))

(s/fdef assoc-created-time
  :args (s/cat :db ::ds/db :entity (s/keys :req [:db/id])))

(defn assoc-created-time
  "Associates the created time to entity."
  [db entity]
  (if-let [id-attr (find-id-attr db (remove #{:db/id} (keys entity)))]
    (let [q '[:find (pull ?tx [:db/txInstant]) .
              :in $ ?e ?a
              :where [?e ?a _ ?tx]]
          {:keys [db/txInstant]} (d/q q db (:db/id entity) id-attr)]
      (assoc entity :audit/created-time txInstant))
    entity))

(declare fetch-audit-data)

(defn fetch-audit-data*
  [db pattern entity-or-entities]
  (if (sequential? entity-or-entities)
    (mapv #(fetch-audit-data db pattern %) entity-or-entities)
    (fetch-audit-data db pattern entity-or-entities)))

(s/fdef fetch-audit-data
  :args (s/cat :db ::ds/db :pattern ::ds/pattern :entity map?))

(defn fetch-audit-data
  "Fetches audit data specified in pattern and puts it in entity."
  [db pattern entity]
  (reduce
    (fn [entity pattern]
      (if (map? pattern)
        (reduce-kv
          (fn [entity k pattern]
            (if (k entity)
              (update entity k #(fetch-audit-data* db pattern %))
              entity))
          entity
          pattern)
        entity))
    (-> (cond->> entity
                 (some #{:audit/created-time} pattern)
                 (assoc-created-time db))
        (dissoc :db/id))
    pattern))

(s/fdef pull
  :args (s/cat :db ::ds/db :pattern ::ds/pattern :eid ::ds/entity-identifier))

(defn pull [db pattern eid]
  (some->> (d/pull db (transform-pattern pattern) eid)
           (fetch-audit-data db pattern)))
