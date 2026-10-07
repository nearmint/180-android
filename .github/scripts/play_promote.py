"""Promotion d'un versionCode d'une piste Play à une autre (androidpublisher v3).

Appelé par .github/workflows/promote.yml. Le contenu de la piste cible est
remplacé par une seule release `completed` portant le versionCode demandé ; le
nom, les notes et le ciblage pays de la release existante (le brouillon, en
priorité) sont repris tels quels. L'edit est commité avec
changesNotSentForReview=true : l'envoi en revue reste un geste manuel dans la
Play Console (managed publishing).

Entrées (environnement) : PLAY_SERVICE_ACCOUNT_JSON, PACKAGE_NAME,
VERSION_CODE, SOURCE_TRACK, TARGET_TRACK.
"""

import json
import os
import sys

from google.auth.transport.requests import AuthorizedSession
from google.oauth2 import service_account

API = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications"


def fail(message):
    print(f"::error::{message}")
    sys.exit(1)


def call(session, method, url, **kwargs):
    response = session.request(method, url, **kwargs)
    if response.status_code >= 400:
        # 403 = droits insuffisants du compte de service sur l'app ou la piste.
        fail(f"{method} {url} → HTTP {response.status_code} : {response.text}")
    return response.json() if response.content else {}


def summary(track):
    return json.dumps(track.get("releases", []), indent=2, ensure_ascii=False)


def main():
    package = os.environ["PACKAGE_NAME"]
    version_code = os.environ["VERSION_CODE"].strip()
    source = os.environ["SOURCE_TRACK"].strip()
    target = os.environ["TARGET_TRACK"].strip()
    if not version_code.isdigit():
        fail(f"versionCode invalide : {version_code!r}")

    credentials = service_account.Credentials.from_service_account_info(
        json.loads(os.environ["PLAY_SERVICE_ACCOUNT_JSON"]),
        scopes=["https://www.googleapis.com/auth/androidpublisher"],
    )
    session = AuthorizedSession(credentials)
    base = f"{API}/{package}/edits"

    edit_id = call(session, "POST", base)["id"]

    # Garde-fou : on ne promeut que ce qui est déjà sur la piste source.
    source_track = call(session, "GET", f"{base}/{edit_id}/tracks/{source}")
    on_source = any(
        version_code in release.get("versionCodes", [])
        for release in source_track.get("releases", [])
    )
    if not on_source:
        fail(f"versionCode {version_code} absent de la piste {source} :\n{summary(source_track)}")

    target_track = call(session, "GET", f"{base}/{edit_id}/tracks/{target}")
    print(f"Piste {target} avant :\n{summary(target_track)}")

    releases = target_track.get("releases", [])
    template = next((r for r in releases if r.get("status") == "draft"), releases[0] if releases else {})
    release = {
        key: template[key]
        for key in ("name", "releaseNotes", "countryTargeting", "inAppUpdatePriority")
        if key in template
    }
    release["versionCodes"] = [version_code]
    release["status"] = "completed"

    call(
        session,
        "PUT",
        f"{base}/{edit_id}/tracks/{target}",
        json={"track": target, "releases": [release]},
    )
    call(session, "POST", f"{base}/{edit_id}:commit", params={"changesNotSentForReview": "true"})
    print(f"Edit {edit_id} commité (changesNotSentForReview=true).")

    # Relecture dans un edit neuf, abandonné ensuite : état réellement enregistré.
    check_id = call(session, "POST", base)["id"]
    for track in (source, target):
        state = call(session, "GET", f"{base}/{check_id}/tracks/{track}")
        print(f"Piste {track} après :\n{summary(state)}")
    call(session, "DELETE", f"{base}/{check_id}")


if __name__ == "__main__":
    main()
