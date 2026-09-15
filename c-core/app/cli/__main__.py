"""Top-level `edpj` CLI for the C-CORE subset carried into the unified repository.

The original EDpjKinsaku `app/cli/__main__.py` also registered `journal` / `state` / `collector` /
`api` / `calibration`, which depend on the database stack and are not part of this repository.
Only `bio` is registered here; `bio evaluate` behaves exactly as in EDpjKinsaku.
"""
from __future__ import annotations

import typer

from app.cli.bio import bio_app

app = typer.Typer(help="edpj — C-CORE exobiology species evaluation")
app.add_typer(bio_app, name="bio")


if __name__ == "__main__":
    app()
