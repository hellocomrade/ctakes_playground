package org.example.ctakesplayground;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.collection.CollectionException;
import org.apache.uima.fit.component.JCasCollectionReader_ImplBase;
import org.apache.uima.fit.component.ViewCreatorAnnotator;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.descriptor.SofaCapability;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Progress;
import org.cleartk.util.ViewUriUtil;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Objects;

@SofaCapability(
        outputSofas = {"UriView"}
)
public class LineReader extends JCasCollectionReader_ImplBase {
    final String viewName = "_InitialView";
    Iterator<String> lines;

    public static final String PARAM_FILE_NAME = "fileName";
    @ConfigurationParameter(
            name = "fileName",
            mandatory = true,
            description = "Takes the name of a single file."
    )
    private String fileName;
    private int lineNumber;

    public LineReader() {
        lineNumber = 0;
    }

    @Override
    public void initialize(UimaContext context) throws ResourceInitializationException {
        Objects.requireNonNull(fileName);
        var path = Paths.get(fileName);
        if(!Files.isRegularFile(path))
            throw new RuntimeException("Text file only");
        try {
            lines = Files.lines(path).iterator();
        }
        catch(IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void getNext(JCas jCas) throws CollectionException {
        JCas view;
        try {
            view = ViewCreatorAnnotator.createViewSafely(jCas, this.viewName);
        } catch (AnalysisEngineProcessException e) {
            throw new CollectionException(e);
        }
        var cols = lines.next().split("\t");
        view.setSofaDataString(cols[2], "text/plain");
        view.setDocumentLanguage("en");

        URI uri;
        try {
            uri = new URI(String.format("%d", ++lineNumber));
        } catch (URISyntaxException e) {
            throw new CollectionException(e);
        }

        CAS cview = view.getCas().createView("UriView");
        cview.setSofaDataURI(uri.toString(), null);
    }

    @Override
    public boolean hasNext() throws IOException, CollectionException {
        return lines.hasNext();
    }

    @Override
    public Progress[] getProgress() {
        return new Progress[0];
    }
}
