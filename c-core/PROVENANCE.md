# c-core の由来

- 元リポジトリ: `sugr332-cloud/EDpjKinsaku`
- 元コミット: `1b320c9d8cd3ce4f99e63509e6d8c06238d1ebd9`（`main`。C-CORE の作業ブランチ `bio-c-core-validation` の最終コミット `3a0d8d4` を含む）
- 取り込み方式: 履歴なしのファイルコピー（2026-09-15）

## 変更せずにコピーしたファイル

- `app/__init__.py`
- `app/bio/__init__.py`
- `app/bio/c_core.py`
- `app/bio/species_value_master.py`（標準ライブラリのみ依存。`tests/test_bio_c_core.py` が C-CORE の価値値との一致確認に使う）
- `app/cli/__init__.py`
- `app/cli/bio.py`
- `app/cli/bio_entry.py`
- `tests/__init__.py`
- `tests/test_bio_c_core.py`
- `tests/test_cli_bio.py`
- `scripts/build_ccore_binary.ps1`
- `scripts/count_bioscan_rulesets.py`
- `docs/BIOSCAN_UPSTREAM_DISCREPANCIES.md`

## この取り込みで新規作成・変更したファイル

- `app/cli/__main__.py` — 元ファイルは DB 依存の `journal` / `state` / `collector` / `api` / `calibration` も登録していたため、`bio` だけを登録する内容に作り直した。`app` という名前と `bio evaluate` の呼び出し方は同じで、`tests/test_cli_bio.py` は無変更のまま動く。
- `pyproject.toml` — 依存を `typer` のみに絞り、パッケージ名を `edpj-ccore` にした。`edpj` コマンド名、`dev` / `packaging` extras は維持。
- `README.md` / `PROVENANCE.md` — 新規。
- リポジトリルートの `.github/workflows/ccore-checks.yml` / `ccore-bioscan-count.yml` — 元の `checks.yml` / `bioscan-count.yml` を `c-core/` 用に調整したもの。F-1 / F-2 の整合性チェック（`check_consistency.py` / `check_dangling_refs.py`）は EDpjKinsaku の docs 全体を対象とするため持ち込んでいない。live EDSM probe（`bio-c-core.yml`）は DB 依存のため持ち込んでいない。

## テスト結果（取り込み時）

`pip install -e ".[dev]"` 後の `python -m pytest -q`: 54 passed（Python 3.12、Linux）。

## 持ち込んでいないもの

`app/bio` の `body_context.py` / `body_parameters.py` / `candidates.py` / `conditions.py` / `journal_reader.py` /
`observation_ingestion.py` / `species_prediction*.py` / `value*.py` などは DB・EDSM 依存で、
C-CORE の `bio evaluate` 経路からは参照されない。Roadmap v2 の v2-P9（value / ranking）で必要になった時点で、
EDpjKinsaku（アーカイブ）から改めて取り込みを判断する。
