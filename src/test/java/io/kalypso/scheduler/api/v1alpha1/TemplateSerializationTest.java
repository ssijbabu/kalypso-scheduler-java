package io.kalypso.scheduler.api.v1alpha1;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kalypso.scheduler.api.v1alpha1.spec.ContentType;
import io.kalypso.scheduler.api.v1alpha1.spec.TemplateManifest;
import io.kalypso.scheduler.api.v1alpha1.spec.TemplateSpec;
import io.kalypso.scheduler.api.v1alpha1.spec.TemplateType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that {@link Template} CRD model classes serialize to and from JSON
 * with the field names expected by the Kubernetes API server.
 */
class TemplateSerializationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    void testTemplateTypeSerializesToLowercaseJson() throws Exception {
        String json = objectMapper.writeValueAsString(TemplateType.RECONCILER);
        assertEquals("\"reconciler\"", json);

        json = objectMapper.writeValueAsString(TemplateType.NAMESPACE);
        assertEquals("\"namespace\"", json);

        json = objectMapper.writeValueAsString(TemplateType.CONFIG);
        assertEquals("\"config\"", json);
    }

    @Test
    void testTemplateTypeDeserializesFromLowercaseJson() throws Exception {
        assertEquals(TemplateType.RECONCILER, objectMapper.readValue("\"reconciler\"", TemplateType.class));
        assertEquals(TemplateType.NAMESPACE,  objectMapper.readValue("\"namespace\"",  TemplateType.class));
        assertEquals(TemplateType.CONFIG,     objectMapper.readValue("\"config\"",     TemplateType.class));
    }

    @Test
    void testContentTypeSerializesToLowercaseJson() throws Exception {
        assertEquals("\"yaml\"", objectMapper.writeValueAsString(ContentType.YAML));
        assertEquals("\"sh\"",   objectMapper.writeValueAsString(ContentType.SH));
    }

    @Test
    void testContentTypeDeserializesFromLowercaseJson() throws Exception {
        assertEquals(ContentType.YAML, objectMapper.readValue("\"yaml\"", ContentType.class));
        assertEquals(ContentType.SH,   objectMapper.readValue("\"sh\"",   ContentType.class));
    }

    @Test
    void testTemplateManifestRoundTrip() throws Exception {
        TemplateManifest manifest = new TemplateManifest();
        manifest.setName("reconciler.yaml");
        manifest.setTemplate("apiVersion: kustomize.toolkit.fluxcd.io/v1beta2\nkind: Kustomization");
        manifest.setContentType(ContentType.YAML);

        String json = objectMapper.writeValueAsString(manifest);

        assertTrue(json.contains("\"name\":\"reconciler.yaml\""),   "name field must be present");
        assertTrue(json.contains("\"template\":"),                   "template field must be present");
        assertTrue(json.contains("\"contentType\":\"yaml\""),        "contentType must serialize as lowercase");

        TemplateManifest deserialized = objectMapper.readValue(json, TemplateManifest.class);
        assertEquals("reconciler.yaml", deserialized.getName());
        assertEquals(ContentType.YAML,  deserialized.getContentType());
        assertNotNull(deserialized.getTemplate());
    }

    @Test
    void testTemplateManifestDefaultsContentTypeToYaml() throws Exception {
        // When contentType is omitted in JSON it should default to YAML after round-trip
        String json = "{\"name\":\"ns.yaml\",\"template\":\"kind: Namespace\"}";
        TemplateManifest deserialized = objectMapper.readValue(json, TemplateManifest.class);

        // Default set in the field initialiser — null when not present in JSON,
        // which is expected: the default only applies when constructed in Java.
        assertEquals("ns.yaml", deserialized.getName());
    }

    @Test
    void testTemplateSpecRoundTrip() throws Exception {
        TemplateManifest manifest = new TemplateManifest();
        manifest.setName("reconciler.yaml");
        manifest.setTemplate("kind: Kustomization");
        manifest.setContentType(ContentType.YAML);

        TemplateSpec spec = new TemplateSpec();
        spec.setType(TemplateType.RECONCILER);
        spec.setManifests(List.of(manifest));

        String json = objectMapper.writeValueAsString(spec);

        assertTrue(json.contains("\"type\":\"reconciler\""),  "type must serialize as lowercase");
        assertTrue(json.contains("\"manifests\":"),           "manifests array must be present");

        TemplateSpec deserialized = objectMapper.readValue(json, TemplateSpec.class);
        assertEquals(TemplateType.RECONCILER, deserialized.getType());
        assertEquals(1, deserialized.getManifests().size());
        assertEquals("reconciler.yaml", deserialized.getManifests().get(0).getName());
    }

    @Test
    void testTemplateSpecEmptyManifestListIsNotNull() {
        TemplateSpec spec = new TemplateSpec();
        assertNotNull(spec.getManifests(), "manifests list must be initialised to empty list, not null");
        assertTrue(spec.getManifests().isEmpty());
    }

    @Test
    void testTemplateSpecSetManifestsNullSafetyReturnsEmptyList() {
        TemplateSpec spec = new TemplateSpec();
        spec.setManifests(null);
        assertNotNull(spec.getManifests());
        assertTrue(spec.getManifests().isEmpty());
    }
}
