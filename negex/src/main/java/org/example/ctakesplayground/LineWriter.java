package org.example.ctakesplayground;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.component.initialize.ConfigurationParameterInitializer;
import org.apache.uima.fit.component.initialize.ExternalResourceInitializer;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import java.lang.reflect.InvocationTargetException;
import java.util.Objects;


public class LineWriter extends JCasAnnotator_ImplBase {

    public static class LineConsumer {
        public void apply(JCas jCas) {
        }
    }

    public static final String CONSUMER_CLASS_NAME = "consumerClassName";
    @ConfigurationParameter(
            name = "consumerClassName",
            description = "Provides the class name of a class that extends LineConsumer.",
            mandatory = true,
            defaultValue = {"org.example.ctakesplayground.LineConsumer"}
    )
    private String consumerClassName;
    private LineConsumer consumer;

    public LineWriter() {}

    @Override
    public void initialize(UimaContext context) throws ResourceInitializationException {
        super.initialize(context);
        ConfigurationParameterInitializer.initialize(this, context);
        ExternalResourceInitializer.initialize(this, context);
        Objects.requireNonNull(consumerClassName);
        try {
            Class<? extends LineConsumer> clazz = Class.forName(consumerClassName).asSubclass(LineConsumer.class);
            consumer = clazz.getConstructor().newInstance();
        } catch (ClassNotFoundException | NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
    @Override
    public void process(JCas jCas) {
        consumer.apply(jCas);
    }
}
