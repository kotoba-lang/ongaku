(ns ongaku.holdings-test
  (:require [clojure.test :refer [deftest is testing]]
            [ongaku.commission :as commission]
            [ongaku.holdings :as holdings]
            [ongaku.rights :as rights]
            [ongaku.work :as work]))

;; ongakuka の実カタログから採った形（resources/catalog.edn v2）
(def dova-asset
  {:asset/id "dova-12420-10deg"
   :asset/title "10℃"
   :asset/source :source/dova-syndrome
   :license/id :license/dova-syndrome-license
   :license/attribution-required? false
   :policy/render-only? true
   :policy/raw-public-access? false
   :policy/ai-training? false
   :policy/content-id-registration? false})

(def cc-by-asset
  {:asset/id "incompetech-example"
   :asset/title "Example"
   :asset/source :source/incompetech
   :license/id :license/cc-by-4.0
   :license/attribution-required? true
   :policy/render-only? true
   :policy/raw-public-access? false
   :policy/ai-training? false
   :policy/content-id-registration? false})

(def unknown-license-asset
  {:asset/id "mystery" :license/id :license/something-new})

(def no-license-asset
  {:asset/id "no-license"})

;; --- 表そのもの ---------------------------------------------------------------

(deftest every-entry-carries-its-legal-basis
  (testing "根拠・確認日・出典の無いエントリを表に入れない"
    (doseq [[lid h] holdings/license-holdings]
      (is (string? (:basis h)) (str lid " に :basis が無い"))
      (is (seq (:basis h)) (str lid " の :basis が空"))
      (is (re-matches #"\d{4}-\d{2}-\d{2}" (:checked h)) (str lid " の :checked が日付でない"))
      (is (string? (:source-url h)) (str lid " に :source-url が無い")))))

(deftest neither-known-license-can-convey-exclusivity
  (doseq [[lid h] holdings/license-holdings]
    (is (false? (:exclusive-possible? h)) (str lid))
    (is (not-any? :exclusive? (:grantable h)) (str lid " が独占を渡そうとしている"))))

;; --- fail-closed ---------------------------------------------------------------

(deftest unknown-and-missing-licenses-grant-nothing
  (testing "未知のライセンスは「制限が無い」ではなく「何も渡せない」"
    (is (= [] (holdings/grantable-rights unknown-license-asset)))
    (is (= [] (holdings/grantable-rights no-license-asset)))))

(deftest a-use-licence-is-not-something-you-can-pass-on
  (testing "DOVA は自分で使う許諾であって、依頼主へ渡せる権利ではない"
    (is (= [] (holdings/grantable-rights dova-asset)))))

(deftest cc-by-conveys-non-exclusive-sync-only
  (let [g (holdings/grantable-rights cc-by-asset)]
    (is (= 1 (count g)))
    (is (= :sync (:right/kind (first g))))
    (is (false? (:right/exclusive? (first g))))))

;; --- explain ------------------------------------------------------------------

(deftest explain-gives-a-reason-for-every-case
  (doseq [a [dova-asset cc-by-asset unknown-license-asset no-license-asset]]
    (let [e (holdings/explain a)]
      (is (string? (:reason e)) (str (:asset/id a) " に理由が無い"))
      (is (seq (:reason e))))))

;; --- 受注に繋いだときの実際の帰結 ------------------------------------------------

(defn- commission-on [asset grants]
  (commission/commission
   {:id "c-1" :client "株式会社ほげ" :fee 1 :deliverables [:master-audio]
    :grants grants
    :work (work/work {:id (:asset/id asset) :provenance :authored
                      :held-rights (holdings/grantable-rights asset)})}))

(deftest dova-backed-commission-cannot-convey-anything
  (doseq [kind [:master :composition :publishing :sync :mechanical]]
    (let [problems (commission/validate
                    (commission-on dova-asset [(rights/right {:kind kind})]))]
      (is (some #(= :not-held (:problem/type %)) problems)
          (str kind " が DOVA 資産で通ってしまった")))))

(deftest cc-by-backed-commission-allows-non-exclusive-sync-only
  (testing "非独占の同期使用は通る"
    (is (nil? (commission/validate
               (commission-on cc-by-asset [(rights/right {:kind :sync :exclusive? false})])))))
  (testing "独占は通らない（同じ権利が既に全世界に出ている）"
    (is (some #(= :not-held (:problem/type %))
              (commission/validate
               (commission-on cc-by-asset [(rights/right {:kind :sync :exclusive? true})])))))
  (testing "原盤権は通らない"
    (is (some #(= :not-held (:problem/type %))
              (commission/validate
               (commission-on cc-by-asset [(rights/right {:kind :master})]))))))
