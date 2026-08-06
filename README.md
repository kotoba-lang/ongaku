# ongaku

BGM selection and license-policy gating for render pipelines — the craft
library split out of gftdcojp's private `ongakuka` actor (ADR-2607023000:
コードは kotoba-lang、職能は cloud-itonami-isco、商売は gftdcojp).

Catalog-agnostic and channel-agnostic: you bring your own catalog EDN. The
library scores candidates by mood and duration, and structurally refuses any
use the asset's license policy forbids (raw public file exposure, AI training,
Content ID / fingerprint registration).

ISCO-08 2652 covers Musicians, Singers **and Composers**, so this library
gates both directions of the same occupation:

| | namespace | gates |
|---|---|---|
| **使い方（下流）** | `ongaku.compose` / `ongaku.policy` | 既製カタログの選定と、その資産のライセンスが禁じる利用 |
| **渡し方（上流）** | `ongaku.rights` / `ongaku.work` / `ongaku.commission` | 受注で譲渡する権利と、約束した納品物 |

Both halves are pure functions over data you bring — no catalog, no client
roster, no business data lives here (ADR-2607023000: 技芸は kotoba-lang、
商売は -ka repo).

## Rights — 持っていない権利は売れない

```clojure
(require '[ongaku.rights :as rights])

;; 既製カタログ資産について studio が持つのは非独占の使用許諾だけ
(rights/validate-grant [(rights/right {:kind :sync :exclusive? false})]
                       (rights/right {:kind :master :exclusive? true}))
;; => [{:problem/type :not-held
;;      :problem/message "保有していない権利は譲渡できない: master(独占) worldwide" ...}]
```

非独占しか持たない権利の独占譲渡、保有地域の外への譲渡、保有期間を超える
譲渡、既発の独占譲渡と地域・期間が重なる二重譲渡も同じく拒否する。

## Work — 作れない物は約束できない

生成パイプラインの出力は音源と stems だけで、譜面も MIDI も DAW セッションも
**そもそも存在しない**。純 AI 生成の作品に「譜面つき」を売ることは守れない
約束なので、受注時に落とす。

```clojure
(require '[ongaku.work :as work])

(work/producible-kinds :authored)   ;; => #{:master-audio :stems :midi :score :session}
(work/producible-kinds :generated)  ;; => #{:master-audio :stems}
```

`:generated` / `:hybrid` の作品は `:work/model-id` と `:work/disclosure` を
持たなければ納品できない（`ai.gftd.ongakuka.track` の `modelId` と同じ要求）。

## Commission

`ongaku.commission/validate` は上の2つを受注1件に対してまとめて回し、問題の
vector（または `nil`）を返す。**その問題を hard reject にするか人間承認に
回すかは決めない** —— それは職能側の governor の判断
（`cloud-itonami-isco-2652` の `music-practice.governor`）。技芸は事実を返し、
職能が処分を決める。

## Contract

```clojure
(require '[ongaku.compose :as compose])

(compose/compose catalog {:channel-id "demo"
                          :mood :calm
                          :duration/sec 120})
;; => {:ongaku/plan :plan/background-music
;;     :asset/id "..." :asset/path "..." :credit/text "..."
;;     :policy/render-only? true :policy/raw-public-access? false ...}
```

The returned plan carries a **local asset path and credit text, never a public
URL**. Callers mux the file into their render and publish the credit.

## Catalog shape

```clojure
{:ongaku.catalog/version 1
 :ongaku.catalog/assets
 [{:asset/id "..." :asset/title "..." :asset/local-path "..."
   :audio/duration-sec 180.0
   :music/moods #{:calm} :music/channels #{"demo"}
   :credit/text "BGM: ... / ..."
   :policy/render-only? true
   :policy/raw-public-access? false
   :policy/ai-training? false
   :policy/content-id-registration? false}]}
```

The legacy `:ongakuka.catalog/assets` key is also read. Production catalogs
(asset lists, channel rosters) are business data and stay in their private
repos; tests here use a synthetic fixture.

## Occupation

ISCO-08 `2652` (Musicians, Singers and Composers) —
[cloud-itonami-isco-2652](https://github.com/cloud-itonami/cloud-itonami-isco-2652).

## Test

```bash
clojure -M:test
```

## License

Apache-2.0.
