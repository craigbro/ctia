(ns ctia.http.routes.pagination-test
  (:refer-clojure :exclude [get])
  (:require
   [clojure.test :refer [deftest is testing use-fixtures join-fixtures]]
   [schema-generators.generators :as g]
   [ctia.test-helpers.core :refer [delete get post put] :as helpers]
   [ctia.test-helpers.fake-whoami-service :as whoami-helpers]
   [ctia.test-helpers.store :refer [deftest-for-each-store]]
   [ctia.test-helpers.auth :refer [all-capabilities]]
   [ctia.schemas.judgement :refer [NewJudgement]]))

(use-fixtures :once (join-fixtures [helpers/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(defn limit-test [route headers]

  (testing (str route " with a limit")
    (let [limit 10
          {limited-status :status
           limited-res :parsed-body
           limited-headers :headers}
          (get route :query-params {:limit limit} :headers headers)

          {full-status :status
           full-res :parsed-body
           full-headers :headers}

          (get route :headers headers)]

      (is (= 200 full-status limited-status))
      (is (= limit (count limited-res)))
      (is (deep= (take limit full-res)
                 limited-res))
      (is (= (clojure.core/get limited-headers "X-Total-Hits")
             (clojure.core/get full-headers "X-Total-Hits"))))))

(defn offset-test [route headers]
  (testing (str route " with limit and offset")
    (let [limit 10
          offset 10
          {limited-status :status
           limited-res :parsed-body
           limited-headers :headers}

          (get route
               :query-params {:limit limit
                              :offset offset}
               :headers headers)

          {full-status :status
           full-res :parsed-body
           full-headers :headers}

          (get route :headers headers)]

      (is (= 200 full-status))
      (is (= 200 limited-status))
      (is (= limit (count limited-res)))
      (is (deep= (->> full-res
                      (drop offset)
                      (take limit))
                 limited-res))

      (is (= (clojure.core/get limited-headers "X-Total-Hits")
             (clojure.core/get full-headers "X-Total-Hits")))

      (is (deep=
           {:X-Total-Hits "30"
            :X-Previous "limit=10&offset=0"
            :X-Next "limit=10&offset=20"}

           (-> limited-headers
               clojure.walk/keywordize-keys
               (select-keys [:X-Total-Hits :X-Previous :X-Next])))))))

(defn sort-test [route headers sort-fields]
  (testing (str route " with sort")

    (let [results (map (fn [field]
                         (->> (get route
                                   :headers headers
                                   :query-params {:sort_by (name field)
                                                  :sort_order "desc"})
                              :parsed-body
                              (map field))) sort-fields)]

      (doall (map #(is (apply >= %)) results)))))

(defn edge-cases-test [route headers]
  (testing (str route " with invalid offset")
    (let [res (->> (get route
                        :headers headers
                        :query-params {:sort_by "id"
                                       :sort_order "asc"
                                       :limit 10
                                       :offset 31})
                   :parsed-body)]
      (is (deep= [] res))))


  (testing (str route " with invalid limit")
    (let [status (->> (get route
                           :headers headers
                           :query-params {:sort_by "id"
                                          :sort_order "asc"
                                          :limit 100000000
                                          :offset 31})
                      :status)]
      (is (= 200 status)))))

(defn pagination-test [route headers sort-fields]
  (limit-test route headers)
  (offset-test route headers)
  (sort-test route headers sort-fields)
  (edge-cases-test route headers))
