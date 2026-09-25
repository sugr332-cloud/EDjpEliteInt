# v2-P8 Trade / Outfitting Search Specification（交易候補・艤装検索仕様）

**Status:** Draft（2026-09-25 決定事項を反映）
**正本との関係:** `docs/ELITEINTEL_INTEGRATION_PLAN.md` §R.6（LLM 一本化）・§R.7 v2-P8・§R.12（DB 非汚染）の下位仕様。矛盾する場合は計画書を優先する。
**前提:** アプリの AI は LM Studio + Gemma 4 E4B（§R.6）。数値の信頼性を LLM の推論能力に依存させない。

## 0. 決定事項（2026-09-25）

| 論点 | 決定 |
|---|---|
| 交易データの鮮度 | **既存の 10 時間に合わせる**（既存 `TradeRouteSearchCriteria` の `max_price_age = 36000` 秒）。新規 Tool も 10 時間で統一する |
| 「近い」の定義 | **既存方式に統一**: 現在地星系からの光年距離の昇順。恒星からの距離（Ls）はトレードプロファイルの `maxLsFromArrival` による足切り（上限）として使い、並び順には使わない |
| ミッション検索 | **v2-P8 から外す**（§8、非目標） |
| 上位 3 候補 | **新規 Tool だけが 3 件を返す**。既存 `find_commodity`（1 件選択 + 自動航路設定）は変更しない |
| Tool の種類と ID（2026-09-25、P8-0 後） | 新規 2 Tool は **Query（`IntelQuery` / `@RegisterQuery`、`BaseQueryAnalyzer.process` でデータと指示を LLM に渡し、LLM が回答する）** として実装する。Command（`IntelCommand`、定型文を自分で読み上げる）にはしない。ID は既存 Query の命名に合わせ `query_nearest_outfitting` / `query_trade_candidates` とする（旧仮称 `find_outfitting` / `find_trade_candidates`） |

## 1. 目的

EliteIntel の LLM Tool として次を追加する。

1. **交易候補検索**（新規 `query_trade_candidates`）: 利益の高い交易候補を上位 3 件返す
2. **艤装（モジュール）販売ステーション検索**（新規 `query_nearest_outfitting`）: 指定モジュールを売っている最寄りのステーションを返す
3. 検索結果から目的地への航路設定へつなぐ（既存の航路設定機能を再利用、§7）

## 2. 責務の分担

### 2.1 LLM の責務

- ユーザー要求の解釈、Tool の選択、Tool 引数の生成
- Tool 結果の日本語での説明（Tool が返した候補・順位・数値をそのまま説明する）

### 2.2 LLM に行わせないこと

- 距離・利益・利益率・効率の計算、候補の順位付けや順位の変更
- 市場データの鮮度の判断・補正、Tool 結果に無い数値の推測
- 鮮度条件を外れたデータの採用

### 2.3 Tool（決定的コード）の責務

- 名前の照合（辞書照合。照合できなければ `UNKNOWN` として検索しない、§R.12 ルール 2）
- 外部データ取得、鮮度フィルタ、距離・利益の計算、順位付け、上位 N 件の選定
- 構造化結果の返却（数値はすべて Tool が計算した値）

これは既存 `FindCommodityCommand` / `CommodityTradeSearch` と同じ方式である。

## 3. 「近い」の定義（既存方式）

既存の商品検索（`SpanshCommoditySearch`）の定義をそのまま使う。

1. **並び順:** 現在地星系（`PlayerSession.getPrimaryStarName()`）からの光年距離の昇順（Spansh の距離ソート）
2. **足切り:** 恒星からステーションまでの距離（Ls）は、トレードプロファイルの `maxLsFromArrival` を上限とする。並び順には使わない
3. **パッドサイズ・惑星港・キャリア・許可制星系:** トレードプロファイルの既存設定（`requiresLargePad` / `allowPlanetary` / `allowFleetCarriers` / `allowPermit` 等）に従う
4. **検索半径:** 指定が無ければ既存 `CommodityTradeSearch.defaultRange()`（FSD 最大ジャンプ距離 × 2、不明時 1000 ly）

**ジャンプ数は使わない。** 航路を計算しないと求まらず、既存検索はどれも光年距離のみを返すため。

## 4. データの鮮度

- 新規の交易候補検索は、市場データの更新から **10 時間以内** のものだけを対象にする（既存交易ルート検索と同じ `max_price_age = 36000`）。
- 10 時間以内のデータで 3 件に満たない場合、Tool は取れた件数だけを返し、状態 `insufficient_fresh_data` を付ける。LLM は「10 時間以内の市場データでは N 件しか見つかりませんでした」と伝える。古いデータへの拡張はしない（仕様変更として別途扱う）。
- 既存 `find_commodity` の鮮度扱い（除外せず、7 日超に注記）は変更しない。
- 艤装データの鮮度は **除外せず、更新時刻（`outfitting_updated_at`）を必ず返す**（暫定。艤装は価格ほど頻繁に変わらないため）。7 日超は既存 `find_commodity` と同様に注記する。

## 5. 交易候補検索 `query_trade_candidates`（新規）

### 5.1 用途

「一番儲かる交易を 3 つ」「近くて儲かる交易先」「効率のいい交易」など、**最適候補を求める要求**。

特定の商品名を指定した「○○を買える／売れる場所」は既存 `find_commodity` / `sell_commodity` のまま（変更しない）。複数ホップの交易ルートは既存 `calculate_trade_route` のまま。

### 5.2 Tool 内の処理

```text
入力（LLM が生成）: 検索半径(任意)、優先(profit | nearest、任意)
  ↓
トレードプロファイル取得（貨物容量・資金・Ls 上限・パッド等）
  ↓
市場データ取得（10 時間以内）
  ↓
候補 = 1 ホップ（購入ステーション → 売却ステーション）
  ↓
各候補について計算:
  単位利益 = 売値 − 買値
  積載量   = min(貨物容量, 資金 ÷ 買値, 供給量, 需要量)
  1 回の総利益 = 単位利益 × 積載量
  距離（現在地 → 購入、購入 → 売却、ly）
  ↓
並び順:
  profit（既定）: 1 回の総利益の降順 → 同値は現在地からの距離の昇順
  nearest       : 現在地から購入ステーションまでの距離の昇順
  ↓
同じ区間（購入ステーション・売却ステーションの組。星系名 + ステーション名で識別）は、1 回の総利益が最大の商品 1 件だけを残す（2026-09-25 追記: 3 件が同じ区間の商品違いで埋まらないようにするため）
  ↓
上位 3 件 + 状態（ok | insufficient_fresh_data | no_result | profile_unavailable | location_unknown）
```

- 「最高効率」「一番儲かる」は **1 回の総利益（profit）** として扱う（暫定）。1 時間あたり利益は航行時間の推定が必要で、既存実装に無いため使わない。
- **P8-0 の結果:** Spansh には 1 ホップ候補を複数件返す API が無い。`/api/trade/route`（`max_hops=1`、`max_price_age=36000`）は最適 1 件のみを返す（実機確認済み）。候補 3 件の取得方式は **案 2 に決定（2026-09-25）**。理由: `/api/trade/route` は非同期ジョブで 1 件あたり十数秒（P8-0 実測で約 17 秒）かかり、起点を増やす案 1 は音声応答として遅すぎるため。
  - 案 1: 現在地周辺の複数ステーションを起点に `/api/trade/route`（`max_hops=1`）を呼び、得られた 1 ホップ取引を総利益順に並べて上位 3 件を選ぶ。
  - **案 2（採用）**: 周辺ステーションの市場データ（10 時間以内）を Spansh ステーション検索（同期 API）で取得し、§5.2 の計算を Tool 内で行って上位 3 件を選ぶ。
    - 取得データ量が大きいため、検索半径・取得ステーション数（ページサイズ）・市場データの鮮度条件の指定方法は、P8-2 の PLAN CHECK で実機計測（応答時間・応答サイズ）に基づいて確定する。目安: 応答全体で 10 秒以内。
    - 購入・売却の組み合わせ計算は取得したステーション間だけで行う（取得範囲外のステーションは候補にしない）。

### 5.3 返却値（候補ごと）

```text
rank
commodity（商品名、canonical 英語名）
buy_system / buy_station / buy_price / supply / buy_station_distance_ls / buy_market_updated_at
sell_system / sell_station / sell_price / demand / sell_station_distance_ls / sell_market_updated_at
unit_profit / units / trip_profit
distance_from_current_ly / route_distance_ly
```

- **自動で航路設定しない。** ユーザーが候補を選んでから §7 で設定する（既存 `find_commodity` との違い）。

## 6. 艤装販売ステーション検索 `query_nearest_outfitting`（新規）

### 6.1 用途

「5A FSD を売っているところ」「一番近い 5A FSD の販売ステーション」など。

- 現在ステーションの艤装確認は既存 `query_local_outfitting`（`AnalyzeLocalOutfittingQuery`、EDSM の `OutfittingDto`）のまま。**拡張せず、別 Tool として新設する。**
- 商品（commodity）とモジュール（module）は別 Tool。`find_commodity` にモジュールを混在させない（§R.7 v2-P8）。

### 6.2 モジュール名の照合

- LLM が抽出したモジュール指定（例: 「5A FSD」「5A フレームシフトドライブ」）は、決定的コードで **カテゴリ・クラス・レーティング**（または `ed_symbol`）へ照合する。照合できなければ `UNKNOWN` として検索しない（§R.12 ルール 2）。
- **照合先（正本）は Spansh の canonical モジュール名**（`GET https://spansh.co.uk/api/stations/field_values/modules` が返す英語名、P8-0 時点で 152 種）＋ クラス（数値）＋ レーティング（英字）とする。
  - 実行時に Spansh から取得せず、**スナップショットをリソースファイルとして同梱**する（取得日を記録する）。辞書は決定的でなければならないため。
  - 日本語・通称（「FSD」「フレームシフトドライブ」「5A」等）から canonical 名・クラス・レーティングへの対応表を新規に作る。`various-meta-data/modules.csv` は日本語列が無くアプリからも参照されていないため使わない。
  - `ed_symbol` は Spansh の検索条件に使えない（P8-0 で実機確認: 条件が無視され全件ヒット）ため、照合のキーにしない。

### 6.3 Tool 内の処理

```text
照合済みモジュール（category, class, rating / ed_symbol）
  ↓
Spansh ステーション検索（モジュールで絞り込み、§3 の並び順・足切り）
  ↓
最寄り 1 件（候補が無ければ no_result）
```

- **P8-0 で実機確認済み:** `POST https://spansh.co.uk/api/stations/search/save` に `filters.modules = [{"name": ["Frame Shift Drive"], "class": [5], "rating": ["A"]}]`、`reference_system`、`sort: [{"distance": {"direction": "asc"}}]` を渡し、`/api/stations/search/recall/<ref>` で 5A FSD を持つステーションが距離順に返ることを確認した。結果には `modules` と `outfitting_updated_at` が含まれる（既存 `TradeStationSearchResultDto` で受け取れる）。
- フリートキャリアも結果に含まれる。トレードプロファイルの `allowFleetCarriers` が false のときは除外する（§3）。パッドサイズ・Ls 上限も §3 のとおり適用する。
- 既存 `query_local_outfitting` のエイリアス（"outfitting", "available modules", "buy modules" 等）と衝突しないよう、新 Query のエイリアスは「売っている場所・最寄り・どこで買える」を必ず含む言い回しにする。現在ステーションの艤装確認は引き続き `query_local_outfitting`。

### 6.4 返却値

```text
module（name / class / rating）
system / station / station_type
distance_from_current_ly / station_distance_ls
price（取得できる場合）
outfitting_updated_at
```

- 自動で航路設定しない（§7）。

## 7. 航路設定との接続

- 航路設定は **既存機能を再利用する**（`RoutePlotter`。既存 `find_commodity` は検索後に自動で使っている。交易ルートには `NavigateToTradeStopCommand` がある）。
- 新規 Tool（§5、§6）は目的地（system / station）を構造化して返すだけで、航路は設定しない。「そこへの航路を設定して」は、検索 Tool とは別のコマンドとして LLM が続けて呼ぶ。
- 目的地を選ぶコマンドの具体設計（直前の検索結果の保持方法を含む）は P8-3 で行う。

## 8. 非目標

- **ミッション掲示板からの候補検索**: 掲示板の内容は外部 API（Spansh / EDSM）にも Journal にも出ない（Journal の `Missions` は受注済みミッションのみ）。データが無いため作らない。受注済みミッションの目的地案内（`NavigateToMissionTargetCommand` 等）は既存のまま。
- ジャンプ数による並べ替え、1 時間あたり利益
- 既存コマンド（`find_commodity`、`sell_commodity`、`calculate_trade_route`、`query_local_outfitting`）の挙動変更
- INARA のデータ・画面の複製

## 9. 実装台帳（v2-P8）

§R.13 の統制手順に従う。P8-1 以降の変更許可ファイルと TEST GATE は、P8-0 の結果を踏まえて PLAN CHANGE REQUEST で本表に確定させてから着手する。

| ID | 内容 | 変更許可ファイル | TEST GATE | 状態 |
|---|---|---|---|---|
| P8-0 | READ-ONLY 調査: (1) Spansh ステーション検索のモジュール条件、(2) 1 ホップ交易候補を複数件取得する方法、(3) モジュール名照合辞書の元データ、(4) 新規 Tool の登録方法 | なし | なし | **DONE**（2026-09-25。結果は §5.2・§6.2・§6.3・§0 に反映） |
| P8-1 | `query_nearest_outfitting`（§6）。Query として実装。モジュール照合辞書（canonical 名スナップショット + 日本語/通称対応表）、Spansh ステーション検索（`filters.modules`、距離順、プロファイルの Ls 上限・パッド・キャリア設定）、LLM への指示とデータ（`BaseQueryAnalyzer.process`）、EN/JA エイリアス | 新規: `app/src/main/java/elite/intel/ai/brain/actions/handlers/queries/NearestOutfittingQuery.java`、`app/src/main/java/elite/intel/gameapi/search/spansh/outfitting/` 配下（検索クライアント・照合辞書）、`app/src/main/resources/outfitting/` 配下（canonical 名スナップショット、日本語/通称対応表）、対応するテスト（`app/src/test/java/elite/intel/gameapi/search/spansh/outfitting/`、`app/src/test/java/elite/intel/ai/brain/actions/handlers/queries/NearestOutfittingQueryTest.java`）。既存: `app/src/main/resources/i18n/ai_action_aliases.properties`、`ai_action_aliases_ja.properties`（`query_nearest_outfitting` の 1 キーのみ追加）、`app/src/test/resources/i18n-parity-baseline.txt`（他 8 言語の当該キーの `MISSING` 行の追加のみ）。Query の説明文に i18n キー（`query.query_nearest_outfitting.*`）が必要な場合は、そのバンドルの EN/JA と baseline のみ。**`TradeStationSearchCriteria` など既存の検索クラスは変更しない**（新規クラスで組み立てる）。PLAN CHANGE REQUEST で追加（2026-09-25 承認）: `app/src/test/java/elite/intel/ai/brain/actions/handlers/queries/QueryRegistryTest.java`（`EXPECTED_QUERY_COUNT` 40→41 のみ）、`app/src/test/java/elite/intel/ai/brain/AiActionMapGeneratorTest.java`（`SNAPSHOT_BUILTIN_IDS` に `query_nearest_outfitting` を 1 件追加のみ）。新 Query 追加時に意識的に更新させる番兵テストのため。これ以外が必要なら PLAN CHANGE REQUEST | `./gradlew --no-daemon :app:test --tests 'elite.intel.gameapi.search.spansh.outfitting.*' --tests '*NearestOutfittingQueryTest' --tests '*BundleKeyParityTest' --tests '*BundleQuotingTest' --tests '*AiActionLocalizationsTest' --tests '*AliasPhraseTest' --tests '*AliasVocabularyTest'`、および全体 `./gradlew --no-daemon :app:test` で既知の失敗 10 件（§10）以外が無いこと | **DONE**（2026-09-25、main `8f68369`。現在地不明時は `location_unknown` を返す） |
| P8-2 | `query_trade_candidates`（§5）。Query として実装。候補取得は §5.2 案 2。PLAN CHECK（2026-09-25）の実測: Spansh ステーション検索（`services: Market`、半径 30 ly、size 20、`filters.updated_at` 過去 10 時間）で約 6.7 秒・約 895 KB・19 件中 16 件が市場データ 10 時間以内。`filters.updated_at` はステーション全体の更新日時で市場の更新日時ではないため、Tool 側で `market_updated_at` を 10 時間以内に厳密に絞る（二重チェック）。既定半径 30 ly（最大 50 ly）、size 20。トレードプロファイルが null または貨物容量 0 の場合は検索せず状態 `profile_unavailable`（貨物容量を仮定しない）。資金上限は `getStartingCapital()`（0 以下なら上限なし） | 新規: `app/src/main/java/elite/intel/ai/brain/actions/handlers/queries/TradeCandidatesQuery.java`、`app/src/main/java/elite/intel/gameapi/search/spansh/tradecandidates/`（`TradeCandidatesSearchClient`、`TradeCandidatesSearchCriteria`、`TradeCandidateCalculator`）、対応テスト（`app/src/test/java/elite/intel/gameapi/search/spansh/tradecandidates/`、`TradeCandidatesQueryTest`）。既存: `ai_action_aliases.properties` / `ai_action_aliases_ja.properties`（`query_trade_candidates` の 1 キーのみ）、`i18n-parity-baseline.txt`（他 8 言語の MISSING 行のみ）、`QueryRegistryTest.java`（41→42 のみ）、`AiActionMapGeneratorTest.java`（スナップショット 1 件追加のみ）。既存の検索クラス・コマンドは変更しない | `./gradlew --no-daemon :app:test --tests 'elite.intel.gameapi.search.spansh.tradecandidates.*' --tests '*TradeCandidatesQueryTest' --tests '*BundleKeyParityTest' --tests '*BundleQuotingTest' --tests '*AiActionLocalizationsTest' --tests '*AliasPhraseTest' --tests '*AliasVocabularyTest'`、および全体テストで既知の失敗 10 件（§10）以外が無いこと | **DONE**（2026-09-25、main `34e6093`） |
| P8-2a | 修正（2026-09-25 実機確認で判明）: `TradeCandidatesQuery` の `radius` の型が `"integer"` で、`ActionParameterSpec.VALID_TYPES`（string / number / boolean）に無い。`ToolCallValidator` は無効な型を含む Tool 定義一式を不正とみなすため、`query_trade_candidates` が候補に入ったターンは全 Tool 呼び出しが `INVALID_TOOL_CALL` になる。`radius` を `"number"` にし、Tool 側で整数へ丸める。再発防止として、登録済みの全 Command / Query の `parameters()` が `ActionParameterSpec.validate()` を通ることを検証するテストを追加する | `app/src/main/java/elite/intel/ai/brain/actions/handlers/queries/TradeCandidatesQuery.java`、`app/src/test/java/elite/intel/ai/brain/actions/handlers/queries/TradeCandidatesQueryTest.java`、新規 `app/src/test/java/elite/intel/ai/brain/actions/AllActionParameterSpecsValidTest.java` | `./gradlew --no-daemon :app:test --tests '*TradeCandidatesQueryTest' --tests '*AllActionParameterSpecsValidTest'`、および全体テストで既知の失敗 10 件（§10）以外が無いこと。実機で「利益が出る交易候補を教えて」が INVALID_TOOL_CALL にならないこと | **DONE**（2026-09-25、main `b0de0dd`。実機再確認待ち） |
| P8-2b | 診断ログの追加（2026-09-26）: `query_trade_candidates` の検索条件と、結果の件数（Spansh ステーション数・市場 10 時間以内・区間数・候補数）・状態・所要時間を INFO で 2 行出力。計算内容・LLM への受け渡しは変更なし | `TradeCandidatesQuery.java`、`TradeCandidateCalculator.java`、対応テスト | TEST GATE + 全体テスト | **DONE**（main `b0e3016`） |
| P8-2c | 数値表記の修正（2026-09-26 実機確認で判明）: Gemma が `speak` の本文で数字を漢数字に書き換え（例: 5103950 → 五百十万三千九百五十）、距離も丸められていない（12.611572376690 ly）。`query_nearest_outfitting` / `query_trade_candidates` の DataDto に表示用文字列を追加する: 価格・利益・数量は 3 桁区切りの整数（`5,103,950`）、光年は小数 2 桁（`12.61`）、光秒は整数（`473`）。LLM への指示に「数値は表示用文字列をそのまま算用数字で使い、漢数字・かなに書き換えない」「光秒は恒星からステーションまでの距離であり時間ではない」を追加。元の数値フィールドは残す | `NearestOutfittingQuery.java`、`TradeCandidatesQuery.java`、それぞれの既存テスト | `./gradlew --no-daemon :app:test --tests '*NearestOutfittingQueryTest' --tests '*TradeCandidatesQueryTest'` と全体テスト（既知の 10 件以外の失敗 0 件）。実機で数字が算用数字・3 桁区切りで出ること | **DONE**（2026-09-26、main `33cb1c6`。実機再確認待ち） |
| P8-2d | 不具合修正（2026-09-26 実機確認で判明）: (1) Spansh の日時（`2026-09-25 12:00:00+00` 形式）を `Instant.parse` で読めず、全市場が 10 時間超とみなされて交易候補が常に 0 件になる。艤装検索の経過時間も同じ。既存 `SpanshCommoditySearch.parseInstant` と同じ書式（`yyyy-MM-dd HH:mm:ss[.fraction][XXX][XX][X]`、ISO も可）で読む共通クラスを新設して両方で使う（既存クラスは変更しない）。(2) P8-2c で追加した「漢数字・かなに書き換えない」指示は、全 Query 共通の「Spell out numerals」（TTS が区切り記号を誤読しないための既存設計）と矛盾するため削除する。丸めた表示用文字列（`*Display`）を数値の出典として使う指示は残す。画面表示の算用数字化は P2-J17（計画書 §R.14）で行う | 新規 `app/src/main/java/elite/intel/gameapi/search/spansh/SpanshTimestamps.java` とそのテスト、`NearestOutfittingQuery.java`、`TradeCandidatesQuery.java`、`TradeCandidateCalculator.java`、それぞれの既存テスト | 新規テスト（`2026-09-25 12:00:00+00`、`+00:00`、小数秒付き、ISO `Z`、不正値 → null）と `NearestOutfittingQueryTest` / `TradeCandidatesQueryTest` / `TradeCandidateCalculatorTest`、全体テスト（既知の 10 件以外の失敗 0 件）。実機で Trade candidates result の freshStations が 0 でないこと | **DONE**（2026-09-26、main `6e44b46`。実機再確認待ち） |
| P8-2e | ログ設定の修正（2026-09-26）: `log4j2.xml` の `elite.intel`=error により P8-2b の INFO ログが抑止されていたため、`TradeCandidatesQuery` / `NearestOutfittingQuery` を info で出力 | `app/src/main/resources/log4j2.xml` | 全体テスト | **DONE**（main `f198d21`。実機で出力を確認済み） |
| P8-4 | `query_trade_candidates` の拡張（2026-09-26 実機確認より）。実機ログ: Synuefe MR-C c29-24 から 50 ly・大型パッド・軌道のみ・10 時間以内で `stations=1, freshStations=1, pairs=0` となり、2 ステーション未満で区間を作れなかった（処理は正常）。(1) 省略可能な文字列パラメータ `referenceSystem` を追加し、指定時はその星系を検索の基準（Spansh の `reference_system`）にする。省略時は現在地。現在地も指定も無い場合のみ `location_unknown`。DataDto に基準星系（`searchedFromSystem`）と、それが指定か現在地か（`referenceSource`: specified / current）を入れ、距離はこの基準からの距離である旨を指示に書く。(2) 市場データ 10 時間以内のステーションが 2 未満のときは状態 `too_few_stations` と、その数（`freshStationCount`）を返し、「条件に合うステーションが N 件しかないため候補を作れない」ことをユーザーの言語で伝えるよう指示する（指定星系が Spansh で見つからない場合もステーション 0 件としてこの状態になる）。(3) EN/JA エイリアスの既存キー `query_trade_candidates` に、基準星系を指定する言い回しを追加する（例: `trade candidates near {referenceSystem:X}`、`{referenceSystem:X} 周辺の交易候補`）。計算内容・並び順は変更しない | `TradeCandidatesQuery.java`、`TradeCandidateCalculator.java`、`TradeCandidatesSearchCriteria.java`、それぞれの既存テスト、`ai_action_aliases.properties` / `ai_action_aliases_ja.properties`（`query_trade_candidates` の行のみ） | `./gradlew --no-daemon :app:test --tests '*TradeCandidatesQueryTest' --tests 'elite.intel.gameapi.search.spansh.tradecandidates.*' --tests '*AllActionParameterSpecsValidTest' --tests '*BundleKeyParityTest' --tests '*AiActionLocalizationsTest' --tests '*AliasPhraseTest' --tests '*AliasVocabularyTest' --tests '*AliasPhraseCollisionTest'` と全体テスト（既知の 10 件以外の失敗 0 件）。実機で「Sol 周辺の交易候補」が Sol 基準で検索されること | 未着手 |
| P8-3 | 検索結果の候補を選んで航路設定するコマンド（§7） | P8-1/P8-2 後に確定 | 確定時に記載 | 未着手 |

## 10. 既知のテスト失敗（2026-09-25 時点、main）

v2-P8 と無関係に main で失敗している 10 件。回帰判定ではこの 10 件を既知として扱い、これ以外の失敗が無いことを確認する。修正は別途（v2-P8 の範囲外）。

- `FighterAttackTargetPhrasingTest` > JA orders the attack in full words（1 件）
- `JukeboxPlayerTest`（7 件）
- `TagScannerTest`（2 件）
