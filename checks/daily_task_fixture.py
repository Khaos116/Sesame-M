"""Embed the production daily gate in existing single-file JVM replay fixtures."""
from pathlib import Path
import re


def with_daily_task(code):
    if 'DailyTask.' not in code or 'class DailyTask' in code:
        return code
    source = (Path(__file__).resolve().parents[1] /
              'app/src/main/java/io/github/aw1y2z/sesame/util/DailyTask.java').read_text(encoding='utf-8')
    body = source[source.index('public final class DailyTask'):].replace('public final class DailyTask', 'static final class DailyTask', 1)
    start = code.index('{', code.index('public class ')) + 1
    code = code[:start] + '\n' + body + '\n' + code[start:]
    # The fixture's Status set stands in for the existing account-bound disk store.
    if not re.search(r'\bvoid\s+clearFlag\(', code):
        code, count = re.subn(r'(class Status\s*\{)', r'\1 static void clearFlag(String key) { flags.remove(key); }', code, count=1)
        assert count, 'DailyTask fixture needs Status'
    return code
