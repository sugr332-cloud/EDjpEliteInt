# c-core — C-CORE exobiology species evaluation

EliteIntel の `elite.intel.bio.ccore.CCoreAdapter` が呼び出す、生物種判定エンジン（C-CORE）です。
判定の Source of Truth であり、AI（`agy` など）はこの結果を説明・要約するだけです（`docs/ELITEINTEL_INTEGRATION_PLAN.md` §R）。

## 由来

`sugr332-cloud/EDpjKinsaku` の `main`（`1b320c9d8cd3ce4f99e63509e6d8c06238d1ebd9`）から、C-CORE とその CLI 境界だけを
履歴なしでコピーしました。対象ファイルと変更点は `PROVENANCE.md` を参照してください。
EDpjKinsaku の Mining / DB / value・ranking / backtest 系は持ち込んでいません。

## 使い方

```text
cd c-core
pip install -e ".[dev]"
python -m pytest -q
echo '{"genus": "Aleoida", "body": {...}}' | edpj bio evaluate
```

## EliteIntel への同梱バイナリ

```text
pip install -e ".[packaging]"
powershell -File scripts/build_ccore_binary.ps1
```

出力は `c-core/dist/ccore/bio_entry/` です。`bio_entry.exe` と `_internal/` をまとめて、
リポジトリルートの `distribution/ccore/windows/` へコピーします（コピーは現時点で手動）。
