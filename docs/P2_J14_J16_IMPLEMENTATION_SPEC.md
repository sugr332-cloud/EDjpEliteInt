# v2-P2 J-14〜J-16 Implementation Specification

**Status:** Active specification / implementation gate
**Date:** 2026-09-15
**Scope:** `sugr332-cloud/EDjpEliteIntel`
**Parent:** `docs/ELITEINTEL_INTEGRATION_PLAN.md` — v2-P2 / §R.13 / §R.14

この文書は、v2-P2 の J-14〜J-16 を `agy` に実装させるための詳細仕様である。
実装前に必ず read-only 調査を行い、本文の前提と実コード・テストが一致することを確認する。
一致しない場合は実装せず STOP し、本仕様または親計画書を先に更新する。

## 0. 共通実装原則

1. J-14〜J-16 は「翻訳ファイルを埋めるだけ」の作業として扱わない。
2. 実コードが示す入力・照合・フォールバック・安全性を Source of Truth とする。
3. `agy` は仕様にない設計変更を行わない。
4. Phase 外のファイル変更、既存仕様と異なる実装、テスト追加が必要な仕様変更は STOP。
5. J-14 と J-15 は独立コミット／独立検証とし、J-16 は v2-P5 へ移管して現在の v2-P2 では実装しない。
6. 既存の英語動作を日本語化によって壊さないことを必須条件とする。

---

# 1. v2-P2 J-14 — AI Action Alias

## 1.1 結論

J-14 は単純な日本語翻訳作業ではない。

`AiActionAliasTextProvider.resolveText` により alias はキー単位で解決されるため、日本語 alias を設定すると、
そのキーについて日本語 locale では従来の英語 alias が置き換わる。

現在の `Language.JA` は、同梱 `ParakeetSTTImpl` が日本語語彙を持たないため、日本語音声を日本語テキストへ変換できない。
そのため既存実装では JA の入力正規化／alias が英語系へフォールバックする設計になっている。

したがって、J-14 を先に日本語 alias へ全面置換すると、v2-P5 の日本語 STT が導入される前に、
日本語 locale で英語テキストを返す入力経路が壊れる可能性がある。

**J-14 の実装開始条件は、英語 alias を保持するか、日本語 alias と併記するか、または v2-P5 後へ延期するかを確定すること。**

## 1.2 現在確認されている制約

- alias はキー単位で丸ごと解決される。
- 入力の単語分割は `[^...]` ではなく実装上 `\p{L}` / `\p{N}` 系の文字単位ルールに依存する。
- スペースのない日本語フレーズは全体が1語として扱われる。
- その場合、現在の fuzzy alias matching の許容差が1文字程度となり、英語の複数語 alias と同じ前提では扱えない。
- 日本語 alias に不要なスペースを入れると、スペースを含まない STT 出力とは完全一致しない。
- ウェイクワードは `EnglishAiActionAliases`（Javaコード）側に固定されており、J-14 の localization bundle 変更だけでは日本語化できない。
- `{key:X}`、`{state:true}` 等の template placeholder を含む alias 行が存在する。対象は確認時点で45行。

## 1.3 J-14 の実装前 READ-ONLY 調査

`agy` は以下だけを調査し、変更してはならない。

- `AiActionAliasTextProvider.resolveText`
- alias bundle の locale 解決箇所
- alias の tokenizer / matcher
- `InputNormalizerLocalizations`
- `AiActionLocalizations`
- `EnglishAiActionAliases`
- `Language.JA` の入力経路
- 日本語 alias を実際に評価するテスト
- `{...}` placeholder を含む alias の全対象

調査結果として以下を START REPORT に明記する。

1. JA locale で alias がどの bundle から解決されるか
2. 英語 alias を残す方法が現在の構造で存在するか
3. 日本語 alias と英語 alias を同一 command key に併記できるか
4. tokenizer が日本語フレーズをどう分割するか
5. ウェイクワードの解決経路
6. placeholder の保持方法

**この調査で既存実装だけでは安全な併記ができないと判明した場合、実装せず STOP。**

## 1.4 J-14 実装方針の決定ルール

次の優先順位で決める。

### A. 英語 alias を保持したまま日本語 alias を追加できる

これが可能なら採用する。

条件:

- 既存英語 alias が失われない。
- 日本語 alias を追加しても既存 command key の意味が変わらない。
- 同一 phrase が複数 command に所属しない。
- placeholder が完全に保持される。

### B. 併記できない

bundle の仕様上、JA の alias が英語 alias を置換するだけなら、J-14 の全面翻訳を実施しない。

この場合は以下のどちらかを計画変更として選択する。

- v2-P5 の日本語 STT 導入後まで J-14 を延期する。
- alias resolver 自体を変更して英語／日本語 alias の両方を保持できる別 Phase を設計する。

**agy は A/B の判断を独断で行わない。**

## 1.5 J-14 TEST GATE

従来台帳に記載されていた固定文字列・EN/RU中心の4テストだけでは完了判定に使わない。

必須確認:

- `AliasPhraseCollisionTest`
- `AliasRepairSafetyTest`
- 既存 alias normalization / command matching テスト群
- placeholder 45行について `{key:X}`、`{state:true}` 等の構造が保持されること
- JA alias の実値を対象にした command matching
- 英語 alias が JA locale で必要な既存経路から失われていないこと

新規テストが必要なら、**テスト追加自体が仕様変更になる場合は STOP して親計画へ戻す。**
単に既存仕様を機械的に固定するためのテスト追加で、変更範囲が J-14 の許可ファイル内に収まる場合のみ実施可。

## 1.6 J-14 完了条件

- 日本語 alias の設計方式が確定している。
- 英語 alias の既存動作を壊していない。
- alias collision がない。
- fuzzy repair により別 command が誤発火しない。
- 45 placeholder alias の構造が保持されている。
- ウェイクワード仕様を勝手に変更していない。
- 必須テストが PASS。
- DIFF が J-14 の許可範囲だけである。
- 次 Phase を自動開始していない。

---

# 2. v2-P2 J-15 — Commodity 日本語照合

## 2.1 設計修正

J-15 は「新しい独立した決定的辞書システムを作る」のではない。

既存コードには商品について多言語 DB 列が存在し、`FuzzySearch.fuzzyCommodityMatch` が locale に応じた列を使って照合し、
canonical な英語名を返す仕組みが既にある。

確認済みの既存構造:

- `commodity_de` 〜 `commodity_ptbz` 等の言語別列が存在する。
- `FuzzySearch.fuzzyCommodityMatch` が言語別列を利用する。
- canonical English commodity name を返す既存経路がある。
- 日本語はその locale 分岐にまだ存在せず、現在は英語列側で照合される。
- 既存 migration の前例として `01013__italian_i18n.sql` に大量の UPDATE がある。

したがって J-15 は、**既存の多言語 commodity 構造へ日本語を追加する作業**として扱う。

## 2.2 実装内容

原則として以下を行う。

1. commodity 用日本語 DB 列を既存の命名・migration 規約に従って追加する。
2. 日本語 commodity 名をその列へ deterministic に登録する migration を追加する。
3. `FuzzySearch.fuzzyCommodityMatch` の locale 分岐へ JA を追加する。
4. JA 名から canonical English commodity 名へ変換されることを確認する。
5. `FindCommodityCommand` 等の既存呼び出し側は、可能な限り変更しない。
6. LLM に商品名の自由翻訳をさせる設計には変更しない。
7. 辞書／DB に存在しない日本語商品名は `UNKNOWN` 相当として検索・保存へ流さないという R.12 の原則を維持する。

## 2.3 既存素材 J-5 との区別

素材 (`material`) の `name_ja` と commodity の日本語列は用途が異なる。

- material: 既存の material name localization architecture を使用する。
- commodity: 既存の commodity multilingual columns / `FuzzySearch` architecture を使用する。

J-15 のために新しい汎用辞書フレームワークを作らない。

## 2.4 J-15 READ-ONLY 調査

実装前に以下を確認する。

- 最新 schema migration の commodity 列定義
- `CommodityDao` / commodity DTO の locale 列アクセス
- `FuzzySearch.fuzzyCommodityMatch`
- `FindCommodityCommand`
- `AddMiningTargetCommand`
- `01013__italian_i18n.sql` の migration 形式
- schema version / migration numbering
- JA の `Language` enum と locale mapping
- commodity の日本語名一覧をどこから確定するか

既存構造と一致しない場合は実装せず STOP。

## 2.5 J-15 TEST GATE

最低限以下を確認する。

- schema / migration が正常に適用できる。
- 日本語 commodity 名 → canonical English 名の deterministic match。
- 英語 commodity 名 → canonical English 名の既存動作が回帰しない。
- 他言語 commodity match が回帰しない。
- 未登録日本語名が検索・保存へ流れない。
- `FindCommodityCommand` の既存 fixture が PASS。
- `AddMiningTargetCommand` の既存保存経路が PASS。
- `:app:test` は実DBを使用せず、既存のテストDB／in-memory SQLite の前提を維持する。

## 2.6 J-15 完了条件

- JA commodity 列と migration が既存 architecture に沿って追加される。
- 日本語名から canonical English commodity が deterministic に得られる。
- 未知名が LLM の自由翻訳で検索／保存されない。
- 既存 EN / 他言語の照合が壊れていない。
- DB 非汚染ルール §R.12 に違反しない。
- 必須テスト PASS。
- 許可範囲外のファイル変更なし。

---

# 3. v2-P2 J-16 — InputNormalizerLocalizations

## 3.1 方針変更

J-16 は v2-P2 では実装しない。

現行実装では `InputNormalizerLocalizations` が JA に対して `EnglishInputNormalizerRules` を返す。
これは同梱 `ParakeetSTTImpl` が日本語を認識できないという現状制約に対応した設計であり、現時点で追加調査しても実装価値がない。

**J-16 は v2-P5（日本語 STT）へ移管する。**

## 3.2 v2-P5 で再調査する条件

日本語 STT バックエンドが選定され、日本語テキストが実際に `ThoughtDispatcher` へ到達する経路が確立した時点で、改めて read-only 調査する。

確認対象:

- STT の実際の出力形式
- 日本語の tokenization
- `InputNormalizerLocalizations`
- `AiActionLocalizations`
- `PhoneticInputNormalizer`
- alias matching
- wake word
- 日本語 action phrase の安全性

J-16 は v2-P2 の未完了項目として残さず、**v2-P5 の入力基盤作業として管理する。**

---

# 4. `ed_events` の称号・階級 104キー

`ed_events` に含まれる称号・階級等の104キーについては、翻訳技術上の問題ではなく「ゲーム内固有名詞を日本語化するか」という仕様判断が必要である。

Elite Dangerous のゲーム画面自体は英語であり、`Language.isGameLocalized()` の対象外となるゲーム内固有名詞と同じ扱いを検討する。

したがって、これら104キーを無条件に翻訳しない。

実装前に以下を決定する。

- アプリ内の説明文として日本語化するか。
- ゲーム内表示との一致を優先して英語を保持するか。
- TTS 用の `responses` と UI 用の `ed_events` で扱いを分けるか。

仕様決定前は対象キーを翻訳完了扱いにしない。

---

# 5. localization baseline / placeholder ルール

## 5.1 baseline

`app/src/test/resources/i18n-parity-baseline.txt` は、翻訳済みキーを削除することだけを許可する。

- 新しい MISSING 行を追加しない。
- 翻訳対象外を隠すために baseline を増やさない。
- 翻訳済みキーの MISSING 行を残さない。
- baseline 件数を減らすことと、実際の翻訳が正しいことを混同しない。

## 5.2 `_ja.properties` のコメント

4 bundle の冒頭コメントは作業開始時点の「P0のみ」「未翻訳」等を前提にしない。
翻訳が進んだ場合、コメントも現在の実態を反映するよう更新してよい。
ただしコメント変更だけを理由に unrelated file を変更しない。

## 5.3 placeholder

`BundleQuotingTest` は placeholder の完全一致を検証するテストではない。

今後の翻訳 DIFF GATE では、少なくとも対象キーについて、英語 source と JA translation の placeholder 集合を比較する。

例:

```text
English:  "Found {0} items"
Japanese: "{0} 件見つかりました"
```

`{0}` の欠落・追加・番号変更は FAIL。

対象は `{0}`、`{1}` 等の MessageFormat 系 placeholder、および既存 bundle 固有の `{key:X}`、`{state:true}` 等のテンプレートを含む。

テストコードそのものを追加するかどうかは、J-14/J-15 の許可範囲と既存テスト構造を read-only 確認したうえで決定する。新規共通検証機構を作る必要がある場合は別 Phase とする。

---

# 6. agy implementation protocol

## START REPORT 必須項目

agy は書き込み前に次を出力する。

```text
=== START REPORT ===
Phase: v2-P2 J-14 / J-15 / J-16
Plan revision: <plan-file last-modifying commit>
Spec: docs/P2_J14_J16_IMPLEMENTATION_SPEC.md
Mode: READ-ONLY / IMPLEMENTATION

Purpose:
Allowed files:
Forbidden files:
Read-only checks:
Commands to run:
Tests to run:
Expected results:
Known risks:
STOP conditions:

READY FOR APPROVAL: YES/NO
=== END START REPORT ===
```

`READY FOR APPROVAL: YES` を出すまではファイルを書き換えない。

## 実装中

- J-14 と J-15 を混ぜない。
- J-16 は実装しない。
- 自動的に次 Phase へ進まない。
- subagent / background task による変更を勝手に開始しない。
- 計画書・本仕様書を勝手に変更しない。
- 許可ファイル外を変更しない。
- テスト失敗を勝手に無視しない。
- 既存の失敗と今回の回帰を区別するため、必要なら baseline を先に記録する。

## END REPORT 必須項目

```text
=== END REPORT ===
Phase: v2-P2 J-14 / J-15
Result: PASS / PARTIAL / BLOCKED / FAILED
Changed files:
Unchanged / forbidden files checked:
Changes:
Tests:
Unperformed checks:
Plan deviation: NONE / <details>
Git before:
Git after:
Commit SHA: <only if explicitly authorized>
Next phase started automatically: NO
STOP reason: <if applicable>
=== END REPORT ===
```

---

# 7. DONE 定義

J-14 / J-15 は、単に翻訳ファイルが埋まった時点では DONE としない。

以下をすべて満たした場合のみ DONE とする。

1. 実装前 read-only 調査完了。
2. 本仕様との整合性確認完了。
3. 許可ファイルのみ変更。
4. 必須テスト PASS。
5. localization baseline が実態と一致。
6. placeholder が保持されている。
7. 既存英語・他言語の動作に回帰なし。
8. Git diff に unrelated change がない。
9. END REPORT が出力されている。
10. 次 Phase が自動開始されていない。

**J-16 は v2-P2 の DONE 条件から除外し、v2-P5 に移管する。**

---

# 8. 変更禁止事項

この仕様の範囲では以下を禁止する。

- alias resolver の全面リファクタリング
- tokenizer の全面変更
- wake word の変更
- STT backend の変更
- `EnglishAiActionAliases` の仕様変更
- 新しい汎用 dictionary framework の作成
- LLM に commodity の自由翻訳をさせる変更
- DB の実データをテストへ持ち込む変更
- 実DBへの書き込みを伴うテスト
- v2-P5 の日本語 STT 実装
- v2-P8 の outfitting 検索実装
- C-CORE の変更
- agy runtime integration（v2-P3）
- 無関係なリファクタリング

これらが必要になった場合は STOP し、対象 Phase の仕様へ戻す。
