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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Objects;

@SofaCapability(
        outputSofas = {"UriView"}
)
public class FolderReader extends JCasCollectionReader_ImplBase {
    final String viewName = "_InitialView";
    Iterator<Path> files;

    public static final String PARAM_DIR_NAME = "dirName";
    @ConfigurationParameter(
            name = "dirName",
            mandatory = true,
            description = "Takes the name of a directory."
    )
    private String dirName;

    public FolderReader() {

    }

    @Override
    public void initialize(UimaContext context) throws ResourceInitializationException {
        Objects.requireNonNull(dirName);
        var path = Paths.get(dirName);
        if(!Files.isDirectory(path))
            throw new RuntimeException("Dir only");
        try {
            files = Files.list(path).iterator();
        }
        catch(IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void getNext(JCas jCas) throws CollectionException, IOException {
        JCas view;
        try {
            view = ViewCreatorAnnotator.createViewSafely(jCas, this.viewName);
        } catch (AnalysisEngineProcessException e) {
            throw new CollectionException(e);
        }
        var p = files.next();
        if(!Files.isRegularFile(p))
            return;
        view.setSofaDataString(new String(Files.readAllBytes(p)), "text/plain");
        view.setDocumentLanguage("en");

        URI uri;
        try {
            uri = new URI(p.getFileName().toString());
        } catch (URISyntaxException e) {
            throw new CollectionException(e);
        }

        CAS cview = view.getCas().createView("UriView");
        cview.setSofaDataURI(uri.toString(), null);
    }

    @Override
    public boolean hasNext() throws IOException, CollectionException {
        return files.hasNext();
    }

    @Override
    public Progress[] getProgress() {
        return new Progress[0];
    }
}
