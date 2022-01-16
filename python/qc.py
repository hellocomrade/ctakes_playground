from contextlib import contextmanager
from typing import Union

from negex import *
from collections import namedtuple, OrderedDict

NegatedTerm = namedtuple('NegatedTerm', ['start', 'lineno', 'term', 'type', 'negated_1', 'negated_2'])


class LineIterator:

    def __init__(self, file_path: str):
        self.file = open(file_path, 'r')
        self.last_line = None

    def line_iterator(self, line_no: int) -> Union[NegatedTerm, None]:
        if line_no < 1:
            return None
        while True:
            if self.last_line:
                line = self.last_line
                self.last_line = None
            else:
                line = self.file.readline()
            if not line:
                break
            cols = line.split('\t')
            if int(cols[0]) != line_no:
                self.last_line = line
                break
            yield NegatedTerm(int(cols[1]), int(cols[0]), cols[2], cols[3], cols[4][:-1] == '1', False)
        return None

    def close(self):
        self.file.close()


@contextmanager
def token_reader(file_path: str) -> LineIterator:
    try:
        r = LineIterator(file_path)
        yield r
    finally:
        r.close()


_tags = ['CONJ', 'PSEU', 'PREN', 'PREP', 'POSP', 'NEGATED', 'PHRASE', 'POSSIBLE']


def term_scanner(anno: str, term: str) -> [NegatedTerm]:
    ans = []
    index, i, last_tag, current_tag = 0, 0, None, None
    last_tag_loc, current_tag_loc, term_loc = -1, -1, -1
    while i < len(anno):
        if current_tag and last_tag and current_tag == last_tag:
            if current_tag in ['NEGATED', 'PHRASE', 'POSSIBLE']:
                if anno[last_tag_loc + len(current_tag) + 1: current_tag_loc - 1].strip() == term:
                    ans.append(NegatedTerm(term_loc, None, term, None, None, current_tag != 'PHRASE'))
                    term_loc = -1
            current_tag = last_tag = None
            current_tag_loc = last_tag_loc = -1
        if anno[i] == '[':
            j = i + 1
            while anno[j] != ']':
                j += 1
            if anno[i + 1: j] not in _tags:
                i += 1
                index += 1
            else:
                if current_tag:
                    last_tag = current_tag
                    last_tag_loc = current_tag_loc
                else:
                    term_loc = index
                current_tag_loc = i + 1
                current_tag = anno[i + 1: j]
                i = j + 1
        else:
            i += 1
            index += 1
    return ans


def main():
    rfile = open(r'negex_triggers_1.txt')
    irules = sortRules(rfile.readlines())
    rfile.close()
    disagreed, not_found = [], []
    with token_reader('input.txt') as treader:
        with open('test.txt', 'r') as input_file:
            while True:
                line = input_file.readline()[:-1]
                if not line:
                    break
                cols = line.split('\t')
                line_no = int(cols[0])
                token_generator = treader.line_iterator(line_no)
                nts = [nt for nt in token_generator]
                locs = dict()
                for nt in nts:
                    if nt.term not in locs:
                        locs[nt.term] = []
                    locs[nt.term].append(nt)
                for phrase in {nt.term for nt in nts}:
                    tagger = negTagger(sentence=cols[2], phrases=[phrase], rules=irules, negP=True)
                    tks = term_scanner(tagger.getNegTaggedSentence(), phrase)
                    nt_arr = locs[phrase]
                    if len(tks) == 0:  # not found at all:
                        not_found.append(f'{line_no}: {phrase}, {nt.negated_1}, NOT_FOUND, {tagger.getNegTaggedSentence()}')
                    for tk in tks:
                        for nt in nt_arr:
                            if nt.start == tk.start and nt.negated_1 != tk.negated_2:
                                disagreed.append(f'{line_no}: {phrase}, {nt.negated_1}, {tk.negated_2}, {tagger.getNegTaggedSentence()}')
    with open('test_result.txt', 'w') as f:
        f.writelines('\n'.join(disagreed))

"""
    negated_terms = OrderedDict()
    with open('input.txt', 'r') as f:
        for line in f.readlines():
            cols = line.split('\t')
            if cols[0] not in negated_terms:
                negated_terms[cols[0]] = []
            negated_terms[cols[0]].append(NegatedTerm(cols[1], cols[0], cols[2], cols[3][:-1]))

    rfile = open(r'negex_triggers_1.txt')
    irules = sortRules(rfile.readlines())
    rfile.close()

    line_no = 0
    txt_file = open('test.txt')
    for key in negated_terms:
        li = int(key)
        txt = ''
        while line_no < li:
            txt = txt_file.readline()
            line_no += 1
        cols = txt.split('\t')
        phrases = set()
        phrase_index_map = dict()

        for nt in negated_terms[key]:
            tt = nt.term.split()
            ptr = 0
            for i in range(len(tt)):
                if tt[i] not in phrase_index_map:
                    phrase_index_map[tt[i]] = set()
                phrase_index_map[tt[i]].add(int(nt.start) + ptr)
                ptr += len(tt[i]) + 1
            phrases.update(tt)

        tagger = negTagger(sentence=cols[2], phrases=list(phrases), rules=irules, negP=True)
        print(tagger.getNegTaggedSentence())
        print(tagger.getNegationFlag())
        print(tagger.getScopes())
        break
    txt_file.close()
"""

if __name__ == '__main__':
    main()
