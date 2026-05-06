package io.kalypso.scheduler.api.v1alpha1;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kalypso.scheduler.api.v1alpha1.spec.EnvironmentSpec;
import io.kalypso.scheduler.api.v1alpha1.spec.RepositoryReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that {@link Environment} CRD model classes serialize to and from JSON
 * with the field names expected by the Kubernetes API server.
 */
class EnvironmentSerializationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    void testEnvironmentSpecRoundTripWithAllFields() throws Exception {
        RepositoryReference ref = new RepositoryReference();
        ref.setRepo("https://github.com/org/control-plane");
        ref.setBranch("main");
        ref.setPath("./environments/prod");

        EnvironmentSpec spec = new EnvironmentSpec();
        spec.setControlPlane(ref);

        String json = objectMapper.writeValueAsString(spec);

        assertTrue(json.contains("\"controlPlane\""), "controlPlane must be serialized");
        assertTrue(json.contains("\"repo\":\"https://github.com/org/control-plane\""), "repo must be serialized");
        assertTrue(json.contains("\"branch\":\"main\""), "branch must be serialized");
        assertTrue(json.contains("\"path\":\"./environments/prod\""), "path must be serialized");

        EnvironmentSpec deserialized = objectMapper.readValue(json, EnvironmentSpec.class);
        assertNotNull(deserialized.getControlPlane());
        assertEquals("https://github.com/org/control-plane", deserialized.getControlPlane().getRepo());
        assertEquals("main", deserialized.getControlPlane().getBranch());
        assertEquals("./environments/prod", deserialized.getControlPlane().getPath());
    }

    @Test
    void testEnvironmentSpecNullControlPlaneIsExcludedFromJson() throws Exception {
        EnvironmentSpec spec = new EnvironmentSpec();

        String json = objectMapper.writeValueAsString(spec);

        assertFalse(json.contains("\"controlPlane\""), "null controlPlane must be omitted from JSON");
    }

    @Test
    void testEnvironmentSpecDeserializesPartialJson() throws Exception {
        String json = "{\"controlPlane\":{\"repo\":\"https://github.com/org/repo\",\"branch\":\"develop\"}}";
        EnvironmentSpec spec = objectMapper.readValue(json, EnvironmentSpec.class);

        assertNotNull(spec.getControlPlane());
        assertEquals("https://github.com/org/repo", spec.getControlPlane().getRepo());
        assertEquals("develop", spec.getControlPlane().getBranch());
        assertNull(spec.getControlPlane().getPath(), "absent path must remain null");
    }

    @Test
    void testRepositoryReferenceRoundTripWithAllFields() throws Exception {
        RepositoryReference ref = new RepositoryReference();
        ref.setRepo("git@github.com:org/cp.git");
        ref.setBranch("release/v3");
        ref.setPath("./staging");

        String json = objectMapper.writeValueAsString(ref);
        RepositoryReference deserialized = objectMapper.readValue(json, RepositoryReference.class);

        assertEquals("git@github.com:org/cp.git", deserialized.getRepo());
        assertEquals("release/v3", deserialized.getBranch());
        assertEquals("./staging", deserialized.getPath());
    }

    @Test
    void testRepositoryReferenceNullFieldsOmittedFromJson() throws Exception {
        RepositoryReference ref = new RepositoryReference();
        ref.setRepo("https://github.com/org/repo");

        String json = objectMapper.writeValueAsString(ref);

        assertFalse(json.contains("\"branch\""), "null branch must be omitted");
        assertFalse(json.contains("\"path\""), "null path must be omitted");
    }
}
