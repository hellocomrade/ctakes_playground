package org.example.ctakesplayground;

import org.apache.ctakes.typesystem.type.textspan.Segment;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;

import java.util.Arrays;

public class NaiveSegmentAnnotator extends JCasAnnotator_ImplBase {

    @Override
    public void process(JCas jCas) throws AnalysisEngineProcessException {
        String text = jCas.getDocumentText();
        Arrays.stream(text.split(System.lineSeparator())).forEach((txt) -> {
            Segment seg = new Segment(jCas);
            seg.setBegin(0);
            seg.setEnd(txt.length());
            seg.addToIndexes();
        });
    }
}
