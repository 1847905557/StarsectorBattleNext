import argparse

from common import ROOT, iter_text_files


def format_lines(content: bytes, line_ending: bytes) -> bytes:
    normalized = content.replace(b"\r\n", b"\n").replace(b"\r", b"\n")
    return normalized.replace(b"\n", line_ending)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--line-ending", choices=("lf", "crlf"), default="lf")
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    line_ending = b"\n" if args.line_ending == "lf" else b"\r\n"

    changed = []
    for path in iter_text_files():
        if not path.suffix:
            continue
        content = path.read_bytes()
        converted = format_lines(content, line_ending)
        if converted != content:
            if not args.check:
                path.write_bytes(converted)
            changed.append(path)

    for path in changed:
        print(f"[{args.line_ending.upper()}] {path.relative_to(ROOT)}")
    action = "needed" if args.check else "converted"
    print(f"{args.line_ending.upper()} {action}: {len(changed)}")
    return 1 if args.check and changed else 0


if __name__ == "__main__":
    raise SystemExit(main())
