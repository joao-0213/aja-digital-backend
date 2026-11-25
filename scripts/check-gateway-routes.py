import json
import re
from pathlib import Path

root = Path(__file__).resolve().parents[1]
openapi = json.loads((root / "docs/http/openapi.json").read_text(encoding="utf-8"))
config = (root / "infra/docker/routes.map.conf").read_text(encoding="utf-8")
locations = [
    (name, re.compile(pattern))
    for pattern, name in re.findall(r'\s+"~(\^/[^"]+)" (core|questions):\d+;', config)
]

assert {name for name, _ in locations} == {"core", "questions"}, "gateway must have both service route allowlists"
assert len(locations) == len(openapi["paths"]), "gateway routes must have one entry per OpenAPI path"
question_paths = {"/questions", "/questions/{id}", "/questions/{id}/practice"}
count = 0
for path, path_item in openapi["paths"].items():
    owner = "questions" if path in question_paths else "core"
    concrete = re.sub(r"\{[^}]+\}", "sample-id", "/api/v1" + path)
    matches = [name for name, pattern in locations if pattern.fullmatch(concrete)]
    assert set(matches) == {owner}, f"{path} routes to {matches}, expected only {owner}"
    if "{" in path:
        invalid_id = re.sub(r"\{[^}]+\}", "sample+id", "/api/v1" + path)
        assert {name for name, pattern in locations if pattern.fullmatch(invalid_id)} == {owner}, f"{path} filters an invalid ID before its owner can validate it"
    count += sum(method in path_item for method in ("get", "post", "put", "patch", "delete", "options", "head"))

for path in ("/internal/v1/session", "/internal/v1/snapshots", "/internal/v1/question-files/id", "/actuator/health"):
    concrete = "/api/v1" + path if not path.startswith("/api/") else path
    assert not any(pattern.fullmatch(concrete) for _, pattern in locations), f"private path exposed: {path}"

for path in ("/api/v1/questions/a/b", "/api/v1/questions/a;v=1", "/api/v1/questions/a/practice/extra"):
    assert not any(pattern.fullmatch(path) for _, pattern in locations), f"ambiguous path exposed: {path}"

print(f"OK: {count} OpenAPI operations across {len(openapi['paths'])} path templates route to the owning service; private paths are excluded")
