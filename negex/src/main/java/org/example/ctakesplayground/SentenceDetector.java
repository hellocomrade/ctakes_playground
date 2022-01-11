package org.example.ctakesplayground;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import opennlp.tools.sentdetect.DefaultSDContextGenerator;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.util.InvalidFormatException;

import opennlp.tools.util.Span;
import org.apache.ctakes.typesystem.type.textsem.IdentifiedAnnotation;
import org.apache.ctakes.typesystem.type.textspan.Segment;
import org.apache.ctakes.typesystem.type.textspan.Sentence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.analysis_engine.annotator.AnnotatorProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.JFSIndexRepository;
import org.apache.uima.resource.ResourceAccessException;
import org.apache.uima.resource.ResourceInitializationException;


/**
 * Wraps the OpenNLP sentence detector in a UIMA annotator.
 *
 * Changes:
 * <ul>
 * <li>split on paragraphs before feeding into maximum entropy model
 * <li>don't split on newlines
 * <li>split on periods
 * <li>split on semi-structured text such as checkboxes
 * </ul>
 *
 * Parameters (optional):
 * <ul>
 * <li>paragraphPattern: regex to split paragraphs. default PARAGRAPH_PATTERN
 * <li>acronymPattern: default ACRONYM_PATTERN. If the text preceding period
 * matches this pattern, we do not split at the period
 * <li>periodPattern: default PERIOD_PATTERN. If the text following period
 * matches this pattern, we split it.
 * <li>splitPattern: regex to split at semi-structured fields. default
 * SPLIT_PATTERN
 * </ul>
 *
 *
 *
 * @author Mayo Clinic
 * @author vijay
 */
public class SentenceDetector extends JCasAnnotator_ImplBase {
    /**
     * Value is "SegmentsToSkip". This parameter specifies which sections to
     * skip. The parameter should be of type String, should be multi-valued and
     * optional.
     */
    public static final String PARAM_SEGMENTS_TO_SKIP = "SegmentsToSkip";

    // LOG4J logger based on class name
    private Logger logger = LoggerFactory.getLogger(getClass().getName());

    public static final String SD_MODEL_FILE_PARAM = "SentenceModelFile";

    private opennlp.tools.sentdetect.SentenceModel sdmodel;
    /**
     * vng change split paragraphs on this pattern
     */
    public static final String PARAGRAPH_PATTERN = "(?m):\\r{0,1}\\n|\\r{0,1}\\n\\r{0,1}\\n";
    /**
     * vng change split sentences periods that do not have this acronym
     * preceding it
     */
    public static final String ACRONYM_PATTERN = "(?m)Dr\\z|Ms\\z|Mr\\z|Mrs\\z|Ms\\z|\\p{Upper}\\z";
    /**
     * vng change split sentences periods after which this pattern is seen
     */
    public static final String PERIOD_PATTERN = "(?m)\\A\\s+\\p{Upper}|\\A\\s+\\d\\.";
    /**
     * vng change split sentences on these patterns
     */
    public static final String SPLIT_PATTERN = "(?im)\\n[\\(\\[]\\s*[yesxno]{0,3}\\s*[\\)\\]]|[\\(\\[]\\s*[yesxno]{0,3}\\s*[\\)\\]]\\s*\\r{0,1}\\n|^[^:\\r\\n]{3,20}\\:[^\\r\\n]{3,20}$";
    /**
     * vng change
     */
    private Pattern paragraphPattern;
    /**
     * vng change
     */
    private Pattern splitPattern;
    /**
     * vng change
     */
    private Pattern periodPattern;
    /**
     * vng change
     */
    private Pattern acronymPattern;

    private UimaContext context;

    private Set<?> skipSegmentsSet;

    private SentenceDetectorME sentenceDetector;

    private String NEWLINE = "\n";

    private int sentenceCount = 0;

    public void initialize(UimaContext aContext)
            throws ResourceInitializationException {

        super.initialize(aContext);
        logger.info("", Arrays.asList(aContext.getConfigParameterNames()));

        context = aContext;
        try {
            configInit();
        } catch (Exception ace) {
            throw new ResourceInitializationException(ace);
        }
    }

    /**
     * Reads configuration parameters.
     *
     * @throws ResourceAccessException
     * @throws IOException
     * @throws InvalidFormatException
     */
    private void configInit() throws ResourceAccessException, InvalidFormatException, IOException {

        try(InputStream is = this.getClass().getResourceAsStream("/org/example/ctakesplayground/sd-med-model.zip")) {
            sdmodel = new SentenceModel(is);
        }

        sentenceDetector = new SentenceDetectorME (sdmodel);
        skipSegmentsSet = Collections.EMPTY_SET;
                // vng change begin
        paragraphPattern = compilePatternCheck("paragraphPattern",
                PARAGRAPH_PATTERN);
        splitPattern = compilePatternCheck("splitPattern", SPLIT_PATTERN);
        periodPattern = compilePatternCheck("periodPattern", PERIOD_PATTERN);
        acronymPattern = compilePatternCheck("acronymPattern", ACRONYM_PATTERN);
        // vng change end
    }
    /**
     * vng change
     */
    private Pattern compilePatternCheck(String patternKey, String patternDefault) {
        String strPattern = (String) context
                .getConfigParameterValue(patternKey);
        if (strPattern == null)
            strPattern = patternDefault;
        Pattern pat = null;
        try {
            pat = Optional.ofNullable(strPattern).orElse("").isEmpty() ? null : Pattern
                    .compile(strPattern);
        } catch (PatternSyntaxException pse) {
            logger.warn("ignoring bad pattern, reverting to default: "
                    + strPattern, pse);
            pat = Pattern.compile(patternDefault);
        }
        return pat;
    }

    /**
     * Entry point for processing.
     */
    public void process(JCas jcas) throws AnalysisEngineProcessException {

        logger.info("Starting processing.");

        sentenceCount = 0;

        String text = jcas.getDocumentText();

        JFSIndexRepository indexes = jcas.getJFSIndexRepository();
        Iterator<?> sectionItr = indexes.getAnnotationIndex(Segment.type)
                .iterator();
        while (sectionItr.hasNext()) {
            Segment sa = (Segment) sectionItr.next();
            String sectionID = sa.getId();
            if (!skipSegmentsSet.contains(sectionID)) {
                sentenceCount = annotateParagraph(jcas, text, sa.getBegin(),
                        sa.getEnd(), sentenceCount);
            }
        }
    }

    /**
     * split paragraphs. Arc v1.0 had a paragraph splitter, and sentences never
     * crossed paragraph boundaries. paragraph splitter was lost in upgrade to
     * ctakes 1.3.2. Now split paragraphs before running through maximum entropy
     * model - this resolves situations where the model would split after a
     * period, e.g.:
     *
     * <pre>
     * Clinical History:
     * Mr. So and so
     * </pre>
     *
     * Without the paragraph splitter, the model splits after Mr. With the
     * paragraph splitter, the model doesn't split after Mr.
     *
     * @param jcas
     * @param text
     * @param b
     * @param e
     * @param sentenceCount
     * @return
     * @throws AnalysisEngineProcessException
     * @throws AnnotatorProcessException
     */
    protected int annotateParagraph(JCas jcas, String text, int b, int e,
                                    int sentenceCount) throws AnalysisEngineProcessException {
        if (this.paragraphPattern == null) {
            return this.annotateRange(jcas, text, b, e, sentenceCount);
        } else {
            int lastEnd = b;
            Matcher m = paragraphPattern.matcher(text);
            while (m.find()) {
                if (m.end() > b && m.end() < e) {
                    sentenceCount = annotateRange(jcas, text, lastEnd, m.end(),
                            sentenceCount);
                    lastEnd = m.end();
                } else if (m.end() >= e) {
                    break;
                }
            }
            sentenceCount = annotateRange(jcas, text, lastEnd, e, sentenceCount);
            return sentenceCount;
        }
    }

    /**
     * Detect sentences within a section of the text and add annotations to the
     * CAS. Uses OpenNLP sentence detector, and then additionally forces
     * sentences to end at end-of-line characters (splitting into multiple
     * sentences). Also trims sentences. And if the sentence detector does
     * happen to form a sentence that is just white space, it will be ignored.
     *
     * @param jcas
     *            view of the CAS containing the text to run sentence detector
     *            against
     * @param text
     *            the document text
     * @param section
     *            the section this sentence is in
     * @param sentenceCount
     *            the number of sentences added already to the CAS (if
     *            processing one section at a time)
     * @return count The sum of <code>sentenceCount</code> and the number of
     *         Sentence annotations added to the CAS for this section
     * @throws AnnotatorProcessException
     */
    protected int annotateRange(JCas jcas, String text, int b, int e,
                                int sentenceCount) throws AnalysisEngineProcessException {

        // vng change begin
        // int b = section.getBegin();
        // int e = section.getEnd();
        // vng chang end

        // Use OpenNLP tools to split text into sentences
        // The sentence detector returns the offsets of the sentence-endings it
        // detects
        // within the string
        Span[] sentenceBreaks = sentenceDetector.sentPosDetect(text.substring(b,
                e)); // OpenNLP tools 1.5 returns Spans rather than offsets that
        // 1.4 did
        int numSentences = sentenceBreaks.length;
        // There might be text after the last sentence-ending found by detector,
        // so +1
        SentenceSpan[] potentialSentSpans = new SentenceSpan[numSentences + 1];

        int sentStart = b;
        int sentEnd = b;
        // Start by filling in sentence spans from what OpenNLP tools detected
        // Will trim leading or trailing whitespace when check for end-of-line
        // characters
        for (int i = 0; i < numSentences; i++) {
            sentEnd = sentenceBreaks[i].getStart() + b; // OpenNLP tools 1.5 returns Spans
            // rather than offsets that 1.4
            // did
            String coveredText = text.substring(sentStart, sentEnd);
            potentialSentSpans[i] = new SentenceSpan(sentStart, sentEnd,
                    coveredText);
            sentStart = sentEnd;
        }

        // If detector didn't find any sentence-endings,
        // or there was text after the last sentence-ending found,
        // create a sentence from what's left, as long as it's not all
        // whitespace.
        // Will trim leading or trailing whitespace when check for end-of-line
        // characters
        if (sentEnd < e) {
            String coveredText = text.substring(sentEnd, e);
            if (coveredText.trim() != "") {
                potentialSentSpans[numSentences] = new SentenceSpan(sentEnd, e,
                        coveredText);
                numSentences++;
            }
        }

        // Copy potentialSentSpans into sentenceSpans,
        // ignoring any that are entirely whitespace,
        // trimming the rest,
        // and splitting any of those that contain an end-of-line character.
        // Then trim any leading or trailing whitespace of ones that were split.
        ArrayList<SentenceSpan> sentenceSpans1 = new ArrayList<SentenceSpan>(0);
        for (int i = 0; i < potentialSentSpans.length; i++) {
            if (potentialSentSpans[i] != null) {
                sentenceSpans1.addAll(potentialSentSpans[i]
                        .splitAtLineBreaksAndTrim(NEWLINE)); // TODO Determine
                // line break
                // type
            }
        }
        // vng change begin
        // split at ".  "
        ArrayList<SentenceSpan> sentenceSpans = new ArrayList<SentenceSpan>(
                sentenceSpans1.size());
        for (SentenceSpan span : sentenceSpans1) {
            if (span != null) {
                sentenceSpans.addAll(span.splitAtPeriodAndTrim(acronymPattern,
                        periodPattern, splitPattern));
            }
        }
        // vng change end

        // Add sentence annotations to the CAS
        int previousEnd = -1;
        for (int i = 0; i < sentenceSpans.size(); i++) {
            SentenceSpan span = sentenceSpans.get(i);
            if (span.getStart() != span.getEnd()) { // skip empty lines
                Sentence sa = new Sentence(jcas);
                sa.setBegin(span.getStart());
                sa.setEnd(span.getEnd());
                //this.attachIdAnnontations(jcas, span);
                if (previousEnd <= sa.getBegin()) {
                    // System.out.println("Adding Sentence Annotation for " +
                    // span.toString());
                    sa.setSentenceNumber(sentenceCount);
                    sa.addToIndexes();
                    sentenceCount++;
                    previousEnd = span.getEnd();
                } else {
                    logger.error("Skipping sentence from " + span.getStart()
                            + " to " + span.getEnd());
                    logger.error("Overlap with previous sentence that ended at "
                            + previousEnd);
                }
            }
        }
        return sentenceCount;
    }
    private void attachIdAnnontations(JCas jcas, SentenceSpan span) {
        var tokens = span.getText().split(" ");
        int start = span.getStart();
        for(var token : tokens) {
            IdentifiedAnnotation ida = new IdentifiedAnnotation(jcas);
            ida.setBegin(start);
            ida.setEnd(start + token.length());
            ida.addToIndexes();
            start += token.length() + 1; 
        }
    }

}
