import sys
from collections import OrderedDict

if __name__ == '__main__':
    negated_terms = OrderedDict()
    with open('input.txt', 'r') as f:
        for line in f.readlines():
            cols = line.split('\t')
            neg = int(cols[4][:-1] if len(cols[4]) > 1 else cols[4])
            loc = int(cols[1])
            term = cols[2]
            if 1 == neg:
                if cols[0] not in negated_terms:
                    negated_terms[cols[0]] = [-1, 0]
                t = negated_terms[cols[0]]
                t[0] = loc if t[0] == -1 else min(t[0], loc)
                t[1] = max(t[1], loc + len(term) + 1)
    output = []
    with open(sys.argv[1]) as f:
        lines = f.readlines()
        for line in lines:
            cols = line.split('\t')
            if len(cols) > 1:
                output.append(cols[:-1])
    visited = set()
    for ret in output:
        if ret[0] not in negated_terms:
            print(f'Negex may fail to negate: {ret[0]}')
        r = negated_terms[ret[0]]
        if int(ret[2]) < r[0] or int(ret[1]) > r[1]:
            print(f'Not overlap: {ret[0]}, negex: [{r[0]}, {r[1]}], test: [{ret[1]}, {ret[2]}]')
        visited.add(ret[0])
    for key in negated_terms:
        if key not in visited:
            print(f'Disagreed on {key}')
