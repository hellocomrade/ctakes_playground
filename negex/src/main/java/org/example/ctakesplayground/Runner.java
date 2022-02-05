package org.example.ctakesplayground;

import org.apache.ctakes.assertion.medfacts.cleartk.windowed.PolarityCleartkAnalysisEngineWindowed;
import org.apache.ctakes.chunker.ae.Chunker;
import org.apache.ctakes.chunker.ae.adjuster.ChunkAdjuster;
import org.apache.ctakes.contexttokenizer.ae.ContextDependentTokenizerAnnotator;
import org.apache.ctakes.core.ae.SimpleSegmentAnnotator;
import org.apache.ctakes.core.ae.TokenizerAnnotatorPTB;
import org.apache.ctakes.dictionary.lookup2.ae.DefaultJCasTermAnnotator;
import org.apache.ctakes.lvg.ae.LvgAnnotator;
import org.apache.ctakes.lvg.ae.LvgBaseTokenAnnotator;
import org.apache.ctakes.necontexts.ContextAnnotator;
import org.apache.ctakes.postagger.POSTagger;
import org.apache.ctakes.typesystem.type.textsem.*;
import org.apache.uima.UIMAFramework;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.SerialFormat;
import org.apache.uima.collection.CollectionException;
import org.apache.uima.collection.CollectionReader;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.CasFactory;
import org.apache.uima.fit.factory.CollectionReaderFactory;
import org.apache.uima.fit.internal.ResourceManagerFactory;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.apache.uima.fit.util.LifeCycleUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.TOP;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.Resource;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceManager;
import org.apache.uima.util.CasCreationUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Runner {
    static List<NegatedTerm> negatedTerms = new ArrayList<>();
    public static class NegatedTermReader extends LineWriter.LineConsumer {
        @Override
        public void apply(JCas jCas) {
            jCas.<TOP>select()
                    .filter(top -> top instanceof IdentifiedAnnotation && !(top instanceof ContextAnnotation))
                    .forEach(top -> {
                        var anno = (IdentifiedAnnotation)top;
                        negatedTerms.add(
                                    new Runner.NegatedTerm(
                                            anno.getCoveredText().trim(),
                                            anno.getType().getShortName(),
                                            anno._casView.getView("UriView").getSofaDataURI(),
                                            anno.getBegin(),
                                            anno.getPolarity() == -1
                                    )
                        );
                    });
        }
    }

    private static AnalysisEngineDescription buildPipeline() throws ResourceInitializationException {
        return AnalysisEngineFactory.createEngineDescription(
                AnalysisEngineFactory.createEngineDescription(SimpleSegmentAnnotator.class),
                AnalysisEngineFactory.createEngineDescription(SentenceDetector.class),
                AnalysisEngineFactory.createEngineDescription(TokenizerAnnotatorPTB.class),
                //LvgAnnotator.createAnnotatorDescription(),
                AnalysisEngineFactory.createEngineDescription(ContextDependentTokenizerAnnotator.class),
                AnalysisEngineFactory.createEngineDescription(POSTagger.class),
                AnalysisEngineFactory.createEngineDescription(Chunker.class),
                ChunkAdjuster.createAnnotatorDescription(new String[]{"NP", "NP"}, 1),
                ChunkAdjuster.createAnnotatorDescription(new String[]{"NP", "PP", "NP"}, 2),
                DefaultJCasTermAnnotator.createAnnotatorDescription("/org/apache/ctakes/dictionary/lookup/david/custom.xml"),
                AnalysisEngineFactory.createEngineDescription(IdentificationAnnotator.class),
                //AnalysisEngineFactory.createEngineDescription(NegexAnnotator.class),
                //AnalysisEngineFactory.createEngineDescription(ContextAnnotator.class),
                AnalysisEngineFactory.createEngineDescription(PolarityCleartkAnalysisEngineWindowed.createAnnotatorDescription()),
                AnalysisEngineFactory.createEngineDescription(LineWriter.class, LineWriter.CONSUMER_CLASS_NAME, "org.example.ctakesplayground.Runner$NegatedTermReader")
                );
    }

    public static void runSingleFilePipeline(String fileName) throws ResourceInitializationException, CollectionException, IOException, AnalysisEngineProcessException {
        SimplePipeline.runPipeline(
                CollectionReaderFactory.createReaderDescription(
                        LineReader.class,
                        LineReader.PARAM_FILE_NAME,
                        fileName)
                ,
                buildPipeline()
        );
    }

    public static void runFilesInFolderPipeline(String dirName) throws ResourceInitializationException, CollectionException, IOException, AnalysisEngineProcessException {
        SimplePipeline.runPipeline(
                CollectionReaderFactory.createReaderDescription(
                        FolderReader.class,
                        FolderReader.PARAM_DIR_NAME,
                        dirName)
                ,
                buildPipeline()
        );
    }

    public static void main(String[] args) throws ResourceInitializationException, AnalysisEngineProcessException, IOException, CollectionException {
        //runSingleFilePipeline(args[0]);
        runFilesInFolderPipeline(args[0]);
        var outputPath = Paths.get("output.txt");
        try(var writer = Files.newBufferedWriter(outputPath)) {
            negatedTerms.forEach(nt -> {
                try {
                    writer.write(String.format("%s\t%d\t%s\t%s\t%d", nt.lineno, nt.start, nt.text, nt.type, nt.negated ? 1 : 0));
                    writer.newLine();
                } catch (IOException e) {
                    e.printStackTrace();
                    throw new UncheckedIOException(e);
                }
            });
        }
        //var cas = CasFactory.createCas();
        /*cas.setDocumentText("""
                The patient underwent a CT scan in April which did not reveal lesions in his liver. 
                Heart was not found. 
                The patient is smoke free, he also likes to eat pizza.
                The first two covid tests which were negative and the other test was positive.
                Markers for hepatitis B and C were negative.
                no portal or biliary structures were present which confirmed the diagnosis of hepatic adenoma.
                20/5/1 20-5-12
                line 3:	20-12-5
                line 4:	11-11-11
                line 5:	11/11/11
                line 6:	2021/5/1
                line 7:	2021-5-12
                line 8:	2021-12-5
                line 9:	2011-11-11
                line 10:	2011/11/11
                	1/5/11
                	11-11-11
                	11/11/11
                	3-25-25
                	25-jan-18
                	1/february/11
                	21 march, 20
                	6 SEPTEMBER 2021
                	18 jun, 2018
                	4-jul-2017
                	Jan 8, 20
                	february 17, 17
                	JULY-30, 21
                	JULY 30, 21
                	Jan 8, 2021
                	february 17, 2017
                	JULY-30, 2021
                	JULY 30, 2021
                	25-jan-8
                	11/february 11
                	21 march, 2
                	2025-jan-8
                	1925/february 11
                	2021 march, 2
                """);*/
        //cas.setDocumentText("The patient underwent a CT scan in April which did not reveal lesions in his liver");
        //cas.setDocumentText("Markers for hepatitis B and C were negative.");
        //cas.setDocumentText("This marker is not a trigger event for no Hepatitis B");
        //cas.setDocumentText("The Chest X-ray showed no infiltrates and EKG revealed sinus tachycardia");
        //cas.setDocumentText("The patient denied experiencing chest pain on exertion");
        //cas.setDocumentText("Extremities showed no cyanosis, clubbing, or edema");
        //cas.setDocumentText("The patient has a slight cough but denies a severe cough.");
        //cas.setDocumentText("Extremities reveal no peripheral cyanosis or EDEMA.");
        //cas.setDocumentLanguage("en");

        //final AnalysisEngineDescription pipeline = buildPipeline();

        /*for (var anno : cas.<Annotation>select()) {
            //AnatomicalSiteMention aa; EntityMention
            //MedicationMention nn;EventMention
            //ProcedureMention pp;
            //SignSymptomMention ss;
            //DiseaseDisorderMention dd;
            if(anno instanceof IdentifiedAnnotation) {
                if (0 != ((IdentifiedAnnotation) anno).getPolarity())
                    System.out.printf("Negative: %s: [%s]\n", anno.getType().getName(), anno.getCoveredText());
                //else
                //    System.out.printf("Positive: %s: [%s]%n", anno.getType().getName(), anno.getCoveredText());
            }

        }*/

        //CasIOUtils.save(cas, System.out, SerialFormat.XMI_PRETTY);

        /*List<NegatedTerm> negatedTerms = null;
        var cass = CasFactory.createCas();
        try(var lines = Files.lines(Path.of(args[0]))) {
            var ret = lines.flatMap(line -> {
                try {
                    cass.reset();
                    var cols = line.split("\t");
                    if(cols.length > 2) {
                        cass.setDocumentText(cols[2]);
                        cass.setDocumentLanguage("en");
                        SimplePipeline.runPipeline(cass, pipeline);
                        var r = cass.<Annotation>select()
                                .filter(anno -> anno instanceof IdentifiedAnnotation && ((IdentifiedAnnotation) anno).getPolarity() != 0)
                                .map(anno -> new NegatedTerm(anno.getCoveredText(), anno.getType().getShortName(), cols[0], anno.getBegin()))
                                .collect(Collectors.toList());
                        return r.stream();
                    }
                } catch ( ResourceInitializationException | AnalysisEngineProcessException e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
                return Stream.empty();
            });
            negatedTerms = ret.collect(Collectors.toList());
        }

        var outputPath = Paths.get("output.txt");
        try(var writer = Files.newBufferedWriter(outputPath)) {
            negatedTerms.forEach(nt -> {
                try {
                    writer.write(String.format("%s\t%d\t%s\t%s", nt.lineno, nt.start, nt.text, nt.type));
                    writer.newLine();
                } catch (IOException e) {
                    e.printStackTrace();
                    throw new UncheckedIOException(e);
                }
            });
        }*/
    }
    public record NegatedTerm(String text, String type, String lineno, int start, boolean negated){}
}
