from contextlib import contextmanager
from typing import Union, Callable, List

from collections import namedtuple

NegatedTerm = namedtuple('NegatedTerm', ['start', 'lineno', 'term', 'type', 'negated_1', 'negated_2'])


class LineIterator:

    def __init__(self, file_path: str, consumer: Callable[[List[str]], NegatedTerm]):
        self.file = open(file_path, 'r')
        self.last_line = None
        self.last_row_no = 0
        self.consumer = consumer

    def line_iterator(self, line_no: int) -> Union[NegatedTerm, None]:
        if line_no < 1 or line_no < self.last_row_no:
            return None
        while True:
            line = None
            ln = self.last_row_no
            if self.last_line:
                line = self.last_line
                self.last_line = None
            if ln == line_no and not line:
                ln = -1
            while ln < line_no:
                line = self.file.readline()
                if not line:
                    return None
                ln = int(line.split('\t')[0])
            if not line:
                return None
            cols = line.split('\t')
            cur_line_no = int(cols[0])
            if cur_line_no != line_no:
                self.last_line = line
                self.last_row_no = cur_line_no
                break
            self.last_line = None
            self.last_row_no = cur_line_no
            yield self.consumer(cols)
        return None

    def close(self):
        self.file.close()


@contextmanager
def token_reader(file_path: str, consumer: Callable[[List[str]], NegatedTerm]) -> LineIterator:
    r = LineIterator(file_path, consumer)
    try:
        yield r
    finally:
        r.close()


def main():
    disagreed, not_found, cnt, total = [], [], 0, 0
    with token_reader('input6.txt', lambda l: NegatedTerm(int(l[1]), int(l[0]), l[2], l[3], '1' == l[4][:-1] if len(l[4]) > 1 else l[4], False)) as treader:
        with open('test.txt', 'r') as input_file:
            while True:
                line = input_file.readline()[:-1]
                if not line:
                    break
                cols = line.split('\t')
                line_no = int(cols[0])
                total += 1
                annotated_phrase = cols[1].lower()
                negated = cols[3] == "Negated"
                token_generator = treader.line_iterator(line_no)
                found = False
                for nt in token_generator:
                    if (nt.term.lower() == annotated_phrase or nt.term.lower() in annotated_phrase) \
                            and nt.negated_1 == negated:
                        found = True
                        break
                if not found:
                    cnt += 1
                    disagreed.append(f'{line_no}: {cols[1]}')
    with open('test_result.txt', 'w') as f:
        f.writelines('\n'.join(disagreed))
    print(f'{(total - cnt) / total}')


if __name__ == '__main__':
    main()
