# This is a sample Python script.

# Press Shift+F10 to execute it or replace it with your code.
# Press Double Shift to search everywhere for classes, files, tool windows, actions, and settings.


from negex_original import *


def main():
    rfile = open(r'negex_triggers_1.txt')
    irules = sortRules(rfile.readlines())
    rfile.close()
    sentences = ['result is negative for Hepatitis B',
                 'This marker is not a trigger event for negative for Hepatitis B',
                 'The Chest X-ray showed no infiltrates and EKG revealed sinus tachycardia',
                 'The patient denied experiencing chest pain on exertion',
                 'Extremities showed no cyanosis, clubbing, or edema',
                 'The patient was Hepatitis A negative',
                 'The patient has no change on cyanosis',
                 'The patient has a slight cough but denies a severe cough.',
                 'Four biopsies were obtained and sent for pathology to R/O CELIAC SPRUE.',
                 'She denies any ABDOMINAL PAIN, no chest pain or shortness of   breath, she has had no particular sick contacts, and she has been using   over-the-counter remedies with no specific relief.'
                 ]
    phrases = [['Hepatitis B'], ['Hepatitis B'], ['infiltrates', 'sinus tachycardia'], ['chest pain'],
               ['cyanosis', 'edema'], ['Hepatitis A'], ['cyanosis'], ['cough'], ['CELIAC SPRUE'], ['ABDOMINAL PAIN']]
    for i in range(len(sentences)):
        tagger = negTagger(sentence=sentences[i], phrases=phrases[i], rules=irules, negP=True)
        print(tagger.getNegTaggedSentence())
        print(tagger.getNegationFlag())
        print(tagger.getScopes())


# Press the green button in the gutter to run the script.
if __name__ == '__main__':
    main()
