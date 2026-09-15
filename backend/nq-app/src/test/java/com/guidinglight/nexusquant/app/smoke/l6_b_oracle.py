"""还原hash绑定的完整Venue前缀，再调用相同业务oracle；不从摘要推断业务成功。"""
import gzip
import hashlib
import json
import sys
from pathlib import Path
from l6_oracle import verify
from l5_measurement import require


def load_checkpoint(path):
    path = Path(path).resolve()
    require(path.stat().st_size <= 32 * 1024 * 1024, 'compressed checkpoint bound')
    with gzip.open(path, 'rb') as stream:
        payload = stream.read(32 * 1024 * 1024 + 1)
    require(len(payload) <= 32 * 1024 * 1024, 'expanded checkpoint bound')
    proof = json.loads(payload)
    require(proof['mode'] == 'FORMAL_L6_B', 'explicit B identity')
    venue = proof['venue']
    reference = venue.pop('eventJournal')
    require(set(reference) == {'path', 'eventCount', 'bytes', 'sha256'}, 'journal reference schema')
    require(reference['path'] == 'venue-events.ndjson', 'owned journal path')
    require(0 < reference['eventCount'] <= 1_000_000 and 0 < reference['bytes'] <= 512_000_000, 'journal budget')
    events = []
    digest = hashlib.sha256()
    remaining = reference['bytes']
    with (path.parent / reference['path']).open('rb') as stream:
        while remaining:
            line = stream.readline(min(513, remaining))
            require(0 < len(line) <= 512 and line.endswith(b'\n'), 'complete bounded journal row')
            remaining -= len(line)
            digest.update(line)
            event = json.loads(line)
            require(event['sequence'] == len(events) + 1, 'journal sequence continuity')
            events.append(event)
    require(len(events) == reference['eventCount'] and digest.hexdigest() == reference['sha256'], 'journal prefix identity')
    require(venue['boundedDelayApplied'] in (0, 1), 'bounded one-shot delay')
    begins = [x for x in events if x['type'] == 'L6B_DELAY_BEGIN']
    ends = [x for x in events if x['type'] == 'L6B_DELAY_END']
    require(len(begins) == len(ends) == venue['boundedDelayApplied'], 'delay begin/end completeness')
    if begins:
        require(begins[0]['client'] == ends[0]['client'] and begins[0]['delayMillis'] == 700
                and ends[0]['nanoTime'] - begins[0]['nanoTime'] >= 700_000_000, 'delay same identity and duration')
    venue['events'] = events
    return proof


if __name__ == '__main__':
    print(json.dumps(verify(load_checkpoint(sys.argv[1]))))
