package org.example.ctakesplayground;

import org.apache.ctakes.typesystem.type.syntax.WordToken;
import org.apache.ctakes.typesystem.type.textsem.IdentifiedAnnotation;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.cas.text.AnnotationIndex;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.JFSIndexRepository;
import org.apache.uima.jcas.tcas.Annotation;

import java.util.Set;
import java.util.stream.Collectors;

public class IdentificationAnnotator extends JCasAnnotator_ImplBase {
    public static final String[] defaultExclusionWords = new String[]{"And", "and", "By", "by", "For", "for", "In", "in", "Of", "of", "On", "on", "The", "the", "To", "to", "With", "with", "Or", "or", "OR"};
    private static Set<String> excludedWordSet;
    static {
        excludedWordSet = Set.copyOf(Set.of(defaultExclusionWords));
    }
    @Override
    public void process(JCas jCas) {
        AnnotationIndex<Annotation> annoIdx = jCas.getAnnotationIndex(IdentifiedAnnotation.typeIndexID);
        Set<MentionedIdentification> mentionedSet = annoIdx.stream()
                .map(annotation -> new MentionedIdentification(annotation.getBegin(), annotation.getEnd(), annotation.getCoveredText()))
                .collect(Collectors.toSet());
        JFSIndexRepository indexes = jCas.getJFSIndexRepository();
        for (Annotation annotation : indexes.getAnnotationIndex(WordToken.type)) {
            WordToken wordAnnotation = (WordToken) annotation;
            String word = wordAnnotation.getCoveredText();
            if (!excludedWordSet.contains(word) && !mentionedSet.contains(new MentionedIdentification(annotation.getBegin(), annotation.getEnd(), annotation.getCoveredText()))) {
                IdentifiedAnnotation ida = new IdentifiedAnnotation(jCas);
                ida.setBegin(wordAnnotation.getBegin());
                ida.setEnd(wordAnnotation.getEnd());
                ida.addToIndexes();
            }
        }
    }

    private record MentionedIdentification(int begin, int end, String text){}
}
