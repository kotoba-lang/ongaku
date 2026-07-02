# ongaku

BGM selection and license-policy gating for render pipelines — the craft
library split out of gftdcojp's private `ongakuka` actor (ADR-2607023000:
コードは kotoba-lang、職能は cloud-itonami-isco、商売は gftdcojp).

Catalog-agnostic and channel-agnostic: you bring your own catalog EDN. The
library scores candidates by mood and duration, and structurally refuses any
use the asset's license policy forbids (raw public file exposure, AI training,
Content ID / fingerprint registration).

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
