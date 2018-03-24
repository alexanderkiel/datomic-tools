(ns datomic-tools.schema
  (:require
    [clojure.spec.alpha :as s]
    [datomic.api :as d]
    [datomic-spec.core :as ds]))

(defn- assoc-tempid [m partition]
  (assoc m :db/id (d/tempid partition)))

(defn- make-attr
  "Assocs :db/id and :db.install/_attribute to the attr map."
  [attr]
  (-> (assoc-tempid attr :db.part/db)
      (assoc :db.install/_attribute :db.part/db)))

(defn- make-fn
  "Assocs :db/id to the fn map."
  [fn]
  (assoc-tempid fn :db.part/user))

(defn- make-part
  "Creates tx-data for a partition with ident."
  [ident]
  (-> {:db/ident ident}
      (assoc-tempid :db.part/db)
      (assoc :db.install/_partition :db.part/db)))

(defonce ^:private attr-reg (atom {}))

(defn reg-attr! [k attr]
  (let [defaults {:db/cardinality :db.cardinality/one}]
    (->> (assoc (merge defaults attr) :db/ident k)
         (swap! attr-reg assoc k))))

(def defattr-args
  (s/cat :k keyword? :doc-string (s/? string?)
         :schema (s/keys* :req [:db/valueType])))

(s/fdef defattr
  :args defattr-args)

(defmacro defattr
  "Defines an attribute."
  [& args]
  (let [{:keys [k doc-string schema]} (s/conform defattr-args args)
        schema (cond-> schema doc-string (assoc :db/doc doc-string))]
    `(reg-attr! ~k ~schema)))

(defonce ^:private enum-reg (atom {}))

(defn reg-enum! [part constant]
  (->> (assoc-tempid {:db/ident constant} part)
       (swap! enum-reg assoc constant)))

(def defenum-args
  (s/cat :k keyword? :doc-string (s/? string?)
         :constants (s/+ keyword?)))

(s/fdef defenum
  :args defenum-args)

(defmacro defenum [& args]
  (let [{:keys [k doc-string constants]} (s/conform defenum-args args)
        schema (cond-> {:db/valueType :db.type/ref}
                 doc-string (assoc :db/doc doc-string))]
    `(do
       (reg-attr! ~k ~schema)
       (doseq [c# ~constants]
         (reg-enum! :db.part/user c#)))))

(defonce ^:private fn-reg (atom {}))

(defn reg-fn! [k schema]
  (->> (assoc schema :db/ident k)
       (swap! fn-reg assoc k)))

(def defunc-args
  (s/cat :name symbol :doc-string (s/? string?)
         :params (s/coll-of symbol? :kind vector?)
         :code (s/+ any?)))

(s/fdef defunc
  :args defunc-args)

(defmacro defunc [& args]
  (let [{:keys [name doc-string params code]} (s/conform defunc-args args)
        name (keyword name)
        schema (cond-> {:db/fn `(d/function '{:lang "clojure" :params ~params
                                              :requires [[clojure.core.reducers]]
                                              :code (do ~@code)
                                              })}
                 doc-string (assoc :db/doc doc-string))]
    `(reg-fn! ~name ~schema)))

(defonce ^:private part-reg (atom #{}))

(defn reg-part! [part]
  (swap! part-reg conj part))

(s/fdef defpart
  :args (s/cat :part keyword?))

(defmacro defpart [part]
  `(reg-part! ~part))

(s/fdef schema :args (s/cat) :ret ::ds/tx-data)

(defn schema []
  (-> (mapv make-attr (vals @attr-reg))
      (into (map make-part) @part-reg)
      (into (vals @enum-reg))
      (into (map make-fn) (vals @fn-reg))))
