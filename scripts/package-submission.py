"""Build and inspect the source-only submission archive from the repository root."""

from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile
import hashlib


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "dist" / "xiaoying-fullstack-submission.zip"
INCLUDE = [
    ROOT / ".github", ROOT / "backend", ROOT / "demo-inputs", ROOT / "docs",
    ROOT / "frontend", ROOT / "scripts", ROOT / ".env.example",
    ROOT / ".gitignore", ROOT / ".gitattributes", ROOT / "AGENTS.md", ROOT / "compose.yaml",
    ROOT / "README.md",
]
BLOCKED_PARTS = {
    ".git", "node_modules", ".next", "target", "coverage", "dist",
    "storage", "uploads", "mysql-data", "asset-data", "__pycache__",
    ".idea", ".vscode",
}
BLOCKED_NAMES = {".env", "id_rsa", "id_ed25519"}
REQUIRED = {
    "README.md", ".env.example", ".gitattributes", "compose.yaml", ".github/workflows/ci.yml",
    "frontend/package-lock.json", "frontend/Dockerfile", "backend/Dockerfile",
    "backend/pom.xml", "backend/mvnw", "backend/mvnw.cmd",
    "backend/.mvn/wrapper/maven-wrapper.properties",
    "backend/src/main/resources/db/migration/V1__schema.sql",
    "backend/src/main/resources/db/migration/V2__seed_templates.sql",
    "backend/src/main/resources/db/migration/V3__seed_demo_users.sql",
    "demo-inputs/coast.png", "demo-inputs/sunset.jpg",
    "docs/final-acceptance.md",
}


def eligible(path: Path) -> bool:
    relative = path.relative_to(ROOT)
    if path.is_symlink() or not path.is_file():
        return False
    if any(part in BLOCKED_PARTS for part in relative.parts):
        return False
    if path.name in BLOCKED_NAMES or path.name.endswith((".local", ".log", ".pem", ".key")):
        return False
    if path.name.startswith(".env.") and path.name != ".env.example":
        return False
    return True


def main() -> None:
    files = sorted(
        {path for item in INCLUDE for path in (item.rglob("*") if item.is_dir() else [item])
         if eligible(path)},
        key=lambda path: path.relative_to(ROOT).as_posix(),
    )
    names = {path.relative_to(ROOT).as_posix() for path in files}
    missing = REQUIRED - names
    if missing:
        raise SystemExit(f"Missing required source files: {sorted(missing)}")
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    with ZipFile(OUTPUT, "w", ZIP_DEFLATED) as archive:
        for path in files:
            archive.write(path, path.relative_to(ROOT).as_posix())
    with ZipFile(OUTPUT) as archive:
        listed = archive.namelist()
        corrupt = archive.testzip()
        if corrupt or len(listed) != len(set(listed)) or set(listed) != names:
            raise SystemExit(f"Archive verification failed; corrupt={corrupt}")
        if any(any(part in BLOCKED_PARTS for part in Path(name).parts) for name in listed):
            raise SystemExit("Archive contains a blocked path")
    digest = hashlib.sha256(OUTPUT.read_bytes()).hexdigest()
    print(f"PASS archive: {len(files)} entries, {OUTPUT.stat().st_size} bytes")
    print(f"SHA-256 {digest}")
    print(f"Path: {OUTPUT}")


if __name__ == "__main__":
    main()
