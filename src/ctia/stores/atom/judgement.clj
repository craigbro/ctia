(ns ctia.stores.atom.judgement
  (:require [ctia.lib.time :as time]
            [ctia.schemas.common :as c]
            [ctia.schemas.judgement
             :refer [NewJudgement StoredJudgement realize-judgement]]
            [ctia.schemas.relationships :as rel]
            [ctia.schemas.verdict :refer [Verdict]]
            [ctia.stores.atom.common :as mc]
            [schema.core :as s]))

(mc/def-create-handler handle-create-judgement
  StoredJudgement
  NewJudgement
  (mc/make-swap-fn realize-judgement)
  (mc/random-id "judgement"))

(mc/def-read-handler handle-read-judgement StoredJudgement)

(mc/def-delete-handler handle-delete-judgement StoredJudgement)

(mc/def-list-handler handle-list-judgements StoredJudgement)

(defn judgement-expired? [judgement now]
  (if-let [expires (get-in judgement [:valid_time :end_time])]
    (time/after? now expires)
    false))

(defn higest-priority [& judgements]
  ;; pre-sort for deterministic tie breaking
  (let [[judgement-1 judgement-2 :as judgements]
        (sort-by (comp :start_time :valid_time) judgements)]
    (cond
      (some nil? judgements)
      (first (remove nil? judgements))

      (not= (:priority judgement-1) (:priority judgement-2))
      (last (sort-by :priority judgements))

      :else (loop [[d-num & rest-d-nums] (sort (keys c/disposition-map))]
              (cond
                (nil? d-num) nil
                (= d-num (:disposition judgement-1)) judgement-1
                (= d-num (:disposition judgement-2)) judgement-2
                :else (recur rest-d-nums))))))

(s/defn make-verdict :- Verdict
  [judgement :- StoredJudgement]
  {:type "verdict"
   :disposition (:disposition judgement)
   :judgement_id (:id judgement)
   :disposition_name (get c/disposition-map (:disposition judgement))})

(s/defn handle-calculate-verdict :- (s/maybe Verdict)
  [state :- (s/atom {s/Str StoredJudgement})
   observable :- c/Observable]
  (if-let [judgement
           (let [now (time/now)]
             (loop [[judgement & more-judgements] (vals @state)
                    result nil]
               (cond
                 (nil? judgement)
                 result

                 (not= observable (:observable judgement))
                 (recur more-judgements result)

                 (judgement-expired? judgement now)
                 (recur more-judgements result)

                 :else
                 (recur more-judgements (higest-priority judgement result)))))]
    (make-verdict judgement)))

(s/defn handle-add-indicator-to-judgement :- (s/maybe rel/RelatedIndicator)
  [state :- (s/atom {s/Str StoredJudgement})
   judgement-id :- s/Str
   indicator-rel :- rel/RelatedIndicator]
  ;; Possible concurrency issue, maybe state should be a ref?
  (when (contains? @state judgement-id)
    (swap! state update-in [judgement-id :indicators] conj indicator-rel)
    indicator-rel))
