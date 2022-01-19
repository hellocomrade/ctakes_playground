package org.example.ctakesplayground;

import org.apache.ctakes.chunker.ae.Chunker;
import org.apache.ctakes.chunker.ae.adjuster.ChunkAdjuster;
import org.apache.ctakes.contexttokenizer.ae.ContextDependentTokenizerAnnotator;
import org.apache.ctakes.core.ae.SimpleSegmentAnnotator;
import org.apache.ctakes.core.ae.TokenizerAnnotatorPTB;
import org.apache.ctakes.dictionary.lookup2.ae.DefaultJCasTermAnnotator;
import org.apache.ctakes.postagger.POSTagger;
import org.apache.ctakes.typesystem.type.textsem.IdentifiedAnnotation;
import org.apache.uima.UIMAFramework;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.collection.CollectionReader;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.internal.ResourceManagerFactory;
import org.apache.uima.fit.util.LifeCycleUtil;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.Resource;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceManager;
import org.apache.uima.util.CasPool;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;


public class Multithreadable {
    private CasPool casPool;
    private AnalysisEngine analysisEngine;
    private CollectionReader collectionReader;
    private ResourceManager resourceManager;

    public List<NegatedTerm> getNegatedTerms() {
        return negatedTerms;
    }

    private List<NegatedTerm> negatedTerms = Collections.synchronizedList(new ArrayList<>());

    public record NegatedTerm(String text, String type, String lineno, int start){}

    public Multithreadable(int numberOfThreads, int timeout) throws ResourceInitializationException {
        resourceManager = ResourceManagerFactory.newResourceManager();
        var desc = AnalysisEngineFactory.createEngineDescription(
                AnalysisEngineFactory.createEngineDescription(
                AnalysisEngineFactory.createEngineDescription(SimpleSegmentAnnotator.class),
                AnalysisEngineFactory.createEngineDescription(SentenceDetector.class),
                AnalysisEngineFactory.createEngineDescription(TokenizerAnnotatorPTB.class),
                AnalysisEngineFactory.createEngineDescription(ContextDependentTokenizerAnnotator.class),
                AnalysisEngineFactory.createEngineDescription(POSTagger.class),
                AnalysisEngineFactory.createEngineDescription(Chunker.class),
                ChunkAdjuster.createAnnotatorDescription(new String[]{"NP", "NP"}, 1),
                ChunkAdjuster.createAnnotatorDescription(new String[]{"NP", "PP", "NP"}, 2),
                DefaultJCasTermAnnotator.createAnnotatorDescription("/org/apache/ctakes/dictionary/lookup/david/custom.xml"),
                AnalysisEngineFactory.createEngineDescription(IdentificationAnnotator.class),
                AnalysisEngineFactory.createEngineDescription(NegexAnnotator.class)
        ));
        Map<String, Object> additionalParams = new HashMap<>();
        additionalParams.put("NUM_SIMULTANEOUS_REQUESTS", Math.max(1, numberOfThreads));
        additionalParams.put("TIMEOUT_PERIOD", Math.max(0, timeout));
        analysisEngine = UIMAFramework.produceAnalysisEngine(desc, resourceManager, additionalParams);
        casPool = new CasPool(Math.max(1, numberOfThreads), analysisEngine);
    }

    public void anaylze(String doc, String id) {
        CAS cas = casPool.getCas(0);
        try {
            cas.setDocumentLanguage("en");
            cas.setDocumentText(doc);
            analysisEngine.process(cas);
            cas.<Annotation>select()
                    .filter(anno -> anno instanceof IdentifiedAnnotation)
                    .forEach(anno -> {
                        if(((IdentifiedAnnotation) anno).getPolarity() != 0) {
                            negatedTerms.add(
                                    new NegatedTerm(anno.getCoveredText(), anno.getType().getShortName(), id, anno.getBegin())
                            );
                        }
                    });
        } catch (AnalysisEngineProcessException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } finally {
            casPool.releaseCas(cas);
        }
    }

    public void destroy() {
        LifeCycleUtil.destroy(new Resource[]{collectionReader});
        LifeCycleUtil.destroy(new Resource[]{analysisEngine});
        LifeCycleUtil.destroy(resourceManager);
    }

    private static void singleFile(Multithreadable mt, String fileName) {
        try(var lines = Files.lines(Path.of(fileName))) {
            lines.parallel().forEach(line -> {
                var cols = line.split("\t");
                mt.anaylze(cols[2], cols[0]);
            });
        } catch (IOException e) {
            e.printStackTrace();
            throw new UncheckedIOException(e);
        }
    }

    private static void files(Multithreadable mt, String dirName) {
        try(var lines = Files.list(Path.of(dirName))) {
            lines.parallel().forEach(p -> {
                if(Files.isRegularFile(p)) {
                    try {
                        mt.anaylze(new String(Files.readAllBytes(p)), p.getFileName().toString());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
            throw new UncheckedIOException(e);
        }
    }

    public static void main(String[] args) throws ResourceInitializationException, IOException {
        Multithreadable mt = new Multithreadable(3, 0);

        files(mt, args[0]);

        var outputPath = Paths.get("output1.txt");
        var negatedTerms = mt.getNegatedTerms();
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
        }
        mt.destroy();
    }

}
