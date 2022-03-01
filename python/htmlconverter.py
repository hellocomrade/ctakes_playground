import sys

from os.path import basename, join


# Assuming input_file and addtional_input_file are both sorted by the unique ID field
def main(input_file, note_file, output_dir, additional_input_file=None):
    template = ''
    with open('NOTE_TEMPLATE.html', 'r', encoding='utf-8') as template_reader:
        template = template_reader.read()
    with open(note_file, 'r', encoding='utf-8') as note_reader:
        def kv(line):
            _l = line.split('\t')
            return _l[0], _l[6]

        notes = dict([kv(n) for n in note_reader.readlines()])
    print(f'{len(notes)} raw notes loaded.')
    negated_offsets, uncertain_offsets, family_offsets = [], [], []
    with open(input_file, 'r', encoding='utf-8') as input_reader:
        last_id = None
        inputs = input_reader.readlines()
        for i in range(1, len(inputs)):
            cols = inputs[i].split('\t')
            id = cols[0]
            if not last_id or last_id != id:
                if last_id:
                    with open(join(output_dir, f'{last_id}.html'), 'w', encoding='utf-8') as note_writer:
                        note_writer.write(('' + template).replace('####TITLE####', last_id)
                                          .replace('####NEGATION_OFFSETS####', f'[{",".join(negated_offsets)}]')
                                          .replace('####UNCERTAINTY_OFFSETS####', f'[{",".join(uncertain_offsets)}]')
                                          .replace('####FAMILY_OFFSETS####', f'[{",".join(family_offsets)}]')
                                          .replace('####NUM_NEG####', f'{int(len(negated_offsets) / 2)}')
                                          .replace('####NUM_UNCER####', f'{int(len(uncertain_offsets) / 2)}')
                                          .replace('####NUM_FAM####', f'{int(len(family_offsets) / 2)}')
                                          .replace('####ALL_TEXT####', notes[last_id].replace('"', "'")))
                negated_offsets, uncertain_offsets, family_offsets = [], [], []
                last_id = id
            ttype, family = cols[1], cols[2]
            lst = None
            if ttype == 'negated':
                lst = negated_offsets
            elif ttype == 'uncertain':
                lst = uncertain_offsets
            elif len(family.strip()) > 0:
                lst = family_offsets
            lst.append(cols[3])
            lst.append(cols[4])
    if additional_input_file:
        additional_offsets = []
        with open(additional_input_file, 'r', encoding='utf-8') as note_reader:
            lines = note_reader.readlines()
            last_id = None
            for line in lines[1:]:
                cols = line.split('\t')
                id = cols[1]
                if not last_id or last_id != id:
                    if last_id:
                        with open(join(output_dir, f'{last_id}.js'), 'w', encoding='utf-8') as extra_writer:
                            extra_writer.write(f'offsetPairs = "{",".join(additional_offsets)}";')
                    additional_offsets = []
                    last_id = id
                if cols[7].startswith('certainty=Negated'):
                    additional_offsets.append(f'{cols[9][:-1]}-{int(cols[9][:-1]) + len(cols[3])}')


if __name__ == '__main__':
    if len(sys.argv) < 4:
        print(f'{basename(__file__)} input_file note_file output_dir [additional_input_file]')
        exit(-1)
    main(sys.argv[1], sys.argv[2], sys.argv[3], None if len(sys.argv) < 5 else sys.argv[4])
