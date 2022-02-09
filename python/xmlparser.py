import sys

import xml.etree.ElementTree as ET
from io import StringIO
from typing import List


class SentenceWrapper:

    def __init__(self):
        self.buffer = StringIO()
        self.id = None
        self.len = 0
        self.negated = []
        self.speculated = []

    def clear(self):
        self.buffer.truncate(0)
        self.buffer.seek(0)
        self.id = None
        self.len = 0
        self.negated = []
        self.speculated = []

    def write(self, text):
        self.len += len(text)
        self.buffer.write(text)

    def set_id(self, id):
        self.id = id

    def mark(self, offsets: List[int], is_negate: bool):
        if len(offsets) != 2:
            return
        self.negated.append(offsets) if is_negate else self.speculated.append(offsets)

    def to_string(self):
        return self.buffer.getvalue()


def inner_xml(raw_xml, tag):
    raw_xml = raw_xml.strip('\t\n\r')
    if not raw_xml.startswith(f'<{tag}'):
        return None
    start, end = 0, len(raw_xml)
    while raw_xml[start] != '>' and start < end:
        start += 1
    return raw_xml[start + 1: len(raw_xml) - len(f'</{tag}>')] if start < end else None


def xml_startswith_tag(raw_txt, tag):
    raw_txt = raw_txt.strip('\t\n\r')
    start_tag = f'<{tag}'
    if not raw_txt.startswith(start_tag):
        return None
    end_tag = f'</{tag}>'
    stag_len, tag_len = len(start_tag), len(end_tag)
    start, end, depth = 0, len(raw_txt), -1
    while (raw_txt[-end + start - tag_len: start] != end_tag or depth > 0) and start < end:
        if raw_txt[-end + start - stag_len: start] == start_tag:
            depth += 1
        elif raw_txt[-end + start - tag_len: start] == end_tag:
            depth -= 1
        start += 1
    return raw_txt[: start] if start <= end else None


def handle_scopes(element, raw_xml, wrapper: SentenceWrapper):
    start = end = wrapper.len
    is_negated = False
    in_xml = inner_xml(raw_xml, element.tag)
    #raw_text = ET.tostring(element, method='text').decode('utf-8')
    txt = element.text if element.text else ''
    wrapper.write(txt)
    next_start = len(txt)
    while True:
        if in_xml[next_start:].startswith('<xcope'):
            rx = xml_startswith_tag(in_xml[next_start:], 'xcope')
            next_start += len(rx)
            xp_elem = ET.ElementTree(ET.fromstring(rx)).getroot()
            handle_scopes(xp_elem, rx, wrapper)
        elif in_xml[next_start:].startswith('<cue'):
            rx = xml_startswith_tag(in_xml[next_start:], 'cue')
            next_start += len(rx)
            cue_elem = ET.ElementTree(ET.fromstring(rx)).getroot()
            wrapper.write(cue_elem.text)
            is_negated = cue_elem.get('type') == 'negation'
        else:
            idx_xc = in_xml[next_start:].find('<xcope')
            idx_cu = in_xml[next_start:].find('<cue')
            if idx_xc == idx_cu == -1:
                wrapper.write(in_xml[next_start:])
                break
            else:
                idx_min = max(idx_xc, idx_cu) if min(idx_xc, idx_cu) == -1 else min(idx_xc, idx_cu)
                wrapper.write(in_xml[next_start: next_start + idx_min])
                next_start += idx_min
    if element.tag == 'xcope':
        wrapper.mark([start, wrapper.len], is_negated)


def main(input_file, output_file):
    tree = ET.parse(input_file)
    root = tree.getroot()
    wrapper = SentenceWrapper()
    line_no = 1
    with open(output_file, 'w') as f:
        for sentence in root.iter("sentence"):
            wrapper.set_id(sentence.get('id'))
            raw_xml = ET.tostring(sentence).decode('utf-8')
            if '<xcope' in raw_xml:
                handle_scopes(sentence, raw_xml, wrapper)
            else:
                wrapper.write(sentence.text)
            plain_text = wrapper.to_string()
            if len(wrapper.negated) == 0 and len(wrapper.speculated) == 0:
                f.write(f'{line_no}\t{plain_text}\t{plain_text}\tAffirmed\t{wrapper.id}')
                line_no += 1
                f.write('\n')
            else:
                for i in range(len(wrapper.negated)):
                    f.write(f'{line_no}\t{plain_text[wrapper.negated[i][0]: wrapper.negated[i][1]]}\t{plain_text}\tNegated\t{wrapper.id}-{i}-n')
                    f.write('\n')
                    line_no += 1
                for i in range(len(wrapper.speculated)):
                    f.write(f'{line_no}\t{plain_text[wrapper.speculated[i][0]: wrapper.speculated[i][1]]}\t{plain_text}\tPossible\t{wrapper.id}-{i}-s')
                    f.write('\n')
                    line_no += 1
            wrapper.clear()


if __name__ == '__main__':
    if len(sys.argv) < 3:
        exit(1)
    main(sys.argv[1], sys.argv[2])
