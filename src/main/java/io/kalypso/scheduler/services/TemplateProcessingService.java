package io.kalypso.scheduler.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import freemarker.template.AdapterTemplateModel;
import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapper;
import freemarker.template.Template;
import freemarker.template.TemplateBooleanModel;
import freemarker.template.TemplateCollectionModel;
import freemarker.template.TemplateHashModelEx;
import freemarker.template.TemplateMethodModelEx;
import freemarker.template.TemplateModel;
import freemarker.template.TemplateModelException;
import freemarker.template.TemplateModelIterator;
import freemarker.template.TemplateNumberModel;
import freemarker.template.TemplateScalarModel;
import freemarker.template.TemplateSequenceModel;
import io.kalypso.scheduler.api.v1alpha1.ClusterType;
import io.kalypso.scheduler.api.v1alpha1.DeploymentTarget;
import io.kalypso.scheduler.exception.TemplateProcessingException;
import io.kalypso.scheduler.model.TemplateContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders Freemarker manifest templates against a {@link TemplateContext}.
 *
 * <p>This is the Java equivalent of the Go operator's {@code scheduler/templater.go}
 * {@code Templater} interface. The Go implementation uses {@code text/template} with
 * Sprig functions; this implementation uses Freemarker 2.3.32 with equivalent
 * custom functions registered as shared variables.
 *
 * <h3>Template syntax</h3>
 * Templates use Freemarker syntax:
 * <ul>
 *   <li>{@code ${DeploymentTargetName}} — variable interpolation</li>
 *   <li>{@code ${toYaml(Labels)}} — custom function call</li>
 *   <li>{@code <#if Environment == "prod">...</#if>} — conditionals</li>
 * </ul>
 *
 * <h3>Custom functions (Go funcMap equivalents)</h3>
 * <table>
 *   <tr><th>Freemarker</th><th>Go</th><th>Purpose</th></tr>
 *   <tr><td>{@code toYaml}</td><td>{@code toYaml} (yaml.Marshal)</td><td>Serialize any object to YAML</td></tr>
 *   <tr><td>{@code stringify}</td><td>{@code stringify}</td><td>Serialize a map to YAML-style string</td></tr>
 *   <tr><td>{@code hash}</td><td>{@code hash} (hashstructure)</td><td>Stable numeric hash of a value</td></tr>
 *   <tr><td>{@code unquote}</td><td>{@code unquote}</td><td>Strip surrounding quotes and whitespace</td></tr>
 * </table>
 *
 * <h3>Recursive template processing</h3>
 * After the first rendering pass, if the output still contains {@code ${} (a Freemarker
 * interpolation marker), the result is re-processed as a new template. This mirrors
 * Go's {@code replaceTemplateVariables} check for {@code {{}} in the rendered output.
 * Recursion is capped at {@value #MAX_RECURSION_DEPTH} levels.
 *
 * <h3>Target namespace</h3>
 * {@link #buildTargetNamespace(DeploymentTarget, ClusterType)} produces the
 * derived namespace string {@code {environment}-{clusterTypeName}-{deploymentTargetName}},
 * which is the Java equivalent of Go's {@code buildTargetNamespace} in {@code templater.go}.
 */
public class TemplateProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(TemplateProcessingService.class);

    /** Maximum depth of recursive template re-processing. */
    static final int MAX_RECURSION_DEPTH = 5;

    private final Configuration freemarkerConfig;
    private final YAMLMapper yamlMapper;
    private final ObjectMapper jsonMapper;

    /**
     * Constructs a {@code TemplateProcessingService} with Freemarker 2.3.32 and all
     * custom template functions pre-registered.
     */
    public TemplateProcessingService() {
        // MINIMIZE_QUOTES matches Go's yaml.Marshal behaviour: simple strings are not quoted.
        this.yamlMapper = new YAMLMapper();
        this.yamlMapper.enable(YAMLGenerator.Feature.MINIMIZE_QUOTES);
        this.jsonMapper = new ObjectMapper();
        this.freemarkerConfig = buildFreemarkerConfiguration();
    }

    /**
     * Renders a single Freemarker template string against the provided context.
     *
     * <p>Mirrors Go's {@code ProcessTemplate(ctx, template, data)} method.
     * Recursively re-processes the output if it still contains Freemarker
     * interpolation markers after the first pass (up to {@value #MAX_RECURSION_DEPTH} times).
     *
     * @param templateSource raw Freemarker template string
     * @param context        data to bind during rendering
     * @return fully rendered string
     * @throws TemplateProcessingException if Freemarker parsing or rendering fails
     */
    public String processTemplate(String templateSource, TemplateContext context) {
        return processTemplateInternal(templateSource, context, 0);
    }

    /**
     * Derives the target namespace from a deployment target and cluster type.
     *
     * <p>Java equivalent of Go's {@code buildTargetNamespace} in {@code templater.go}:
     * <pre>
     * fmt.Sprintf("%s-%s-%s", deploymentTarget.Spec.Environment, clusterType.Name, deploymentTarget.Name)
     * </pre>
     *
     * @param deploymentTarget the deployment target whose environment and metadata name are used
     * @param clusterType      the cluster type whose metadata name is used
     * @return derived namespace string {@code {environment}-{clusterTypeName}-{deploymentTargetName}}
     */
    public static String buildTargetNamespace(DeploymentTarget deploymentTarget, ClusterType clusterType) {
        return deploymentTarget.getSpec().getEnvironment()
                + "-" + clusterType.getMetadata().getName()
                + "-" + deploymentTarget.getMetadata().getName();
    }

    private String processTemplateInternal(String templateSource, TemplateContext context, int depth) {
        if (depth >= MAX_RECURSION_DEPTH) {
            logger.warn("Reached max recursion depth ({}) processing template, returning as-is", MAX_RECURSION_DEPTH);
            return templateSource;
        }
        try {
            StringWriter writer = new StringWriter();
            Template template = new Template("tpl-depth-" + depth, new StringReader(templateSource), freemarkerConfig);
            template.process(context.toMap(), writer);
            String result = writer.toString();

            // Re-process if the rendered output still contains Freemarker expressions.
            // Mirrors Go's recursive replaceTemplateVariables check for "{{".
            if (result.contains("${") || result.contains("<#")) {
                logger.debug("Template output at depth {} contains further expressions, re-processing", depth);
                return processTemplateInternal(result, context, depth + 1);
            }
            return result;
        } catch (Exception e) {
            throw new TemplateProcessingException(
                    "Failed to process template at depth " + depth + ": " + e.getMessage(), e);
        }
    }

    private Configuration buildFreemarkerConfiguration() {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_32);
        cfg.setObjectWrapper(new DefaultObjectWrapper(Configuration.VERSION_2_3_32));
        cfg.setDefaultEncoding("UTF-8");
        cfg.setLogTemplateExceptions(false);

        try {
            cfg.setSharedVariable("toYaml", new ToYamlMethod());
            cfg.setSharedVariable("stringify", new StringifyMethod());
            cfg.setSharedVariable("hash", new HashMethod());
            cfg.setSharedVariable("unquote", new UnquoteMethod());
        } catch (Exception e) {
            throw new TemplateProcessingException("Failed to register Freemarker functions", e);
        }

        return cfg;
    }

    // -------------------------------------------------------------------------
    // Custom Freemarker template methods (Go funcMap equivalents)
    // -------------------------------------------------------------------------

    /**
     * Freemarker equivalent of Go's {@code toYaml} function ({@code yaml.Marshal}).
     *
     * <p>Serializes the argument to YAML using Jackson. The leading {@code ---}
     * document separator is stripped so the output embeds cleanly in a manifest.
     */
    private class ToYamlMethod implements TemplateMethodModelEx {
        @Override
        @SuppressWarnings("rawtypes")
        public Object exec(List args) throws TemplateModelException {
            if (args.isEmpty()) return "";
            Object value = toJavaObject((TemplateModel) args.get(0));
            try {
                String yaml = yamlMapper.writeValueAsString(value);
                if (yaml.startsWith("---\n")) yaml = yaml.substring(4);
                return yaml.stripTrailing();
            } catch (JsonProcessingException e) {
                throw new TemplateModelException("toYaml: failed to serialize to YAML", e);
            }
        }
    }

    /**
     * Freemarker equivalent of Go's {@code stringify} function.
     *
     * <p>Converts a map or any value to a YAML-style multi-line string.
     * Useful for embedding {@code ConfigData} as a literal block in templates.
     * For non-map values, delegates to {@link String#valueOf}.
     */
    private class StringifyMethod implements TemplateMethodModelEx {
        @Override
        @SuppressWarnings("rawtypes")
        public Object exec(List args) throws TemplateModelException {
            if (args.isEmpty()) return "";
            Object value = toJavaObject((TemplateModel) args.get(0));
            if (value instanceof Map) {
                try {
                    String yaml = yamlMapper.writeValueAsString(value);
                    if (yaml.startsWith("---\n")) yaml = yaml.substring(4);
                    return yaml.stripTrailing();
                } catch (JsonProcessingException e) {
                    throw new TemplateModelException("stringify: failed to serialize map", e);
                }
            }
            return String.valueOf(value);
        }
    }

    /**
     * Freemarker equivalent of Go's {@code hash} function (hashstructure library).
     *
     * <p>Returns a stable unsigned numeric string derived from the JSON representation
     * of the argument. Used for deterministic name generation in templates.
     * The exact numeric value differs from the Go hashstructure result, but is
     * equally stable across JVM runs for the same input.
     */
    private class HashMethod implements TemplateMethodModelEx {
        @Override
        @SuppressWarnings("rawtypes")
        public Object exec(List args) throws TemplateModelException {
            if (args.isEmpty()) return "0";
            Object value = toJavaObject((TemplateModel) args.get(0));
            try {
                String json = jsonMapper.writeValueAsString(value);
                byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
                long hash = 0L;
                for (byte b : bytes) {
                    hash = hash * 31L + (b & 0xFFL);
                }
                return Long.toUnsignedString(hash);
            } catch (JsonProcessingException e) {
                throw new TemplateModelException("hash: failed to serialize value", e);
            }
        }
    }

    /**
     * Freemarker equivalent of Go's {@code unquote} function.
     *
     * <p>Strips leading and trailing double-quotes, single-quotes, and whitespace
     * from the argument. Useful when template variables contain quoted values.
     */
    private class UnquoteMethod implements TemplateMethodModelEx {
        @Override
        @SuppressWarnings("rawtypes")
        public Object exec(List args) throws TemplateModelException {
            if (args.isEmpty()) return "";
            Object value = toJavaObject((TemplateModel) args.get(0));
            String s = String.valueOf(value);
            return s.replaceAll("^[\"'\\s]+|[\"'\\s]+$", "");
        }
    }

    // -------------------------------------------------------------------------
    // Model unwrapping helper
    // -------------------------------------------------------------------------

    /**
     * Converts a {@link TemplateModel} to a plain Java object suitable for serialization.
     *
     * <p>{@code DeepUnwrap.unwrap()} only handles {@link AdapterTemplateModel} and
     * {@link WrapperTemplateModel}. When {@code DefaultObjectWrapper} wraps a
     * {@code java.util.Map} as a {@code SimpleHash} (non-adapter), this method
     * falls back to iterating the hash model to reconstruct a {@link Map}.
     */
    private Object toJavaObject(TemplateModel model) throws TemplateModelException {
        if (model == null || model == TemplateModel.NOTHING) return null;
        // Adapter-based (DefaultMapAdapter when useAdaptersForContainers=true)
        if (model instanceof AdapterTemplateModel) {
            return ((AdapterTemplateModel) model).getAdaptedObject(Object.class);
        }
        // Non-adapter hash (SimpleHash) — iterate manually
        if (model instanceof TemplateHashModelEx) {
            Map<String, Object> result = new LinkedHashMap<>();
            TemplateCollectionModel keys = ((TemplateHashModelEx) model).keys();
            TemplateModelIterator it = keys.iterator();
            while (it.hasNext()) {
                String key = ((TemplateScalarModel) it.next()).getAsString();
                result.put(key, toJavaObject(((TemplateHashModelEx) model).get(key)));
            }
            return result;
        }
        if (model instanceof TemplateSequenceModel) {
            TemplateSequenceModel seq = (TemplateSequenceModel) model;
            List<Object> result = new ArrayList<>(seq.size());
            for (int i = 0; i < seq.size(); i++) result.add(toJavaObject(seq.get(i)));
            return result;
        }
        if (model instanceof TemplateScalarModel)  return ((TemplateScalarModel) model).getAsString();
        if (model instanceof TemplateNumberModel)  return ((TemplateNumberModel) model).getAsNumber();
        if (model instanceof TemplateBooleanModel) return ((TemplateBooleanModel) model).getAsBoolean();
        return String.valueOf(model);
    }
}
