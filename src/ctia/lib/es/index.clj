(ns ctia.lib.es.index
  (:require
   [schema.core :as s]
   [clojurewerkz.elastisch.native :as n]
   [clojurewerkz.elastisch.native.index :as idx]
   [ctia.stores.es.mapping :refer [store-mappings]]
   [ctia.events.producers.es.mapping :refer [producer-mappings]]
   [ctia.properties :refer [properties]]))

(s/defschema ESConnState
  {:index s/Str
   :mapping {s/Any s/Any}
   :conn org.elasticsearch.client.transport.TransportClient})

(defn read-store-index-spec []
  "read es store index config properties, returns an option map"
  (get-in @properties [:ctia :store :es]))

(defn read-producer-index-spec []
  "read es producer index config properties, returns an option map"
  (get-in @properties [:ctia :producer :es]))

(s/defn init-store-conn :- ESConnState []
  "initiate an ES store connection returns a map containing transport,
   mapping, and the configured index name"

  (let [props (read-store-index-spec)]
    {:index (:indexname props)
     :mapping store-mappings
     :conn (n/connect [[(:host props) (Integer. (:port props))]]
                      {"cluster.name" (:clustername props)})}))

(s/defn init-producer-conn :- ESConnState []
  "initiate an ES producer connection returns a map containing transport,
   mapping and the configured index name"
  (let [props (read-producer-index-spec)]
    {:index (:indexname props)
     :mapping producer-mappings
     :conn (n/connect [[(:host props) (Integer. (:port props))]]
                      {"cluster.name" (:clustername props)})}))

(defn delete!
  "delete an index, abort if non existant"
  [conn index-name]
  (when (idx/exists? conn index-name)
    (idx/delete conn index-name)))

(defn create!
  "create an index, abort if already exists"
  [conn index-name mappings]
  (when-not (idx/exists? conn index-name)
    (idx/create conn index-name :mappings mappings)))
