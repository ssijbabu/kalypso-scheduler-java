package io.kalypso.scheduler.api.v1alpha1;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kalypso.scheduler.api.v1alpha1.spec.BaseRepoSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that {@link BaseRepo} CRD model classes serialize to and from JSON
 * with the field names expected by the Kubernetes API server.
 */
class BaseRepoSerializationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    void testBaseRepoSpecRoundTripWithAllFields() throws Exception {
        BaseRepoSpec spec = new BaseRepoSpec();
        spec.setRepo("https://github.com/org/control-plane");
        spec.setBranch("main");
        spec.setPath("./environments");
        spec.setCommit("a1b2c3d4e5f6");

        String json = objectMapper.writeValueAsString(spec);

        assertTrue(json.contains("\"repo\":\"https://github.com/org/control-plane\""), "repo must be serialized");
        assertTrue(json.contains("\"branch\":\"main\""),            "branch must be serialized");
        assertTrue(json.contains("\"path\":\"./environments\""),    "path must be serialized");
        assertTrue(json.contains("\"commit\":\"a1b2c3d4e5f6\""),    "commit must be serialized");

        BaseRepoSpec deserialized = objectMapper.readValue(json, BaseRepoSpec.class);
        assertEquals("https://github.com/org/control-plane", deserialized.getRepo());
        assertEquals("main",              deserialized.getBranch());
        assertEquals("./environments",    deserialized.getPath());
        assertEquals("a1b2c3d4e5f6",      deserialized.getCommit());
    }

    @Test
    void testBaseRepoSpecRoundTripWithoutCommit() throws Exception {
        BaseRepoSpec spec = new BaseRepoSpec();
        spec.setRepo("https://github.com/org/control-plane");
        spec.setBranch("main");
        spec.setPath("./base");

        String json = objectMapper.writeValueAsString(spec);
        BaseRepoSpec deserialized = objectMapper.readValue(json, BaseRepoSpec.class);

        assertEquals("https://github.com/org/control-plane", deserialized.getRepo());
        assertEquals("main",   deserialized.getBranch());
        assertEquals("./base", deserialized.getPath());
        assertNull(deserialized.getCommit(), "optional commit must remain null when not set");
    }

    @Test
    void testBaseRepoSpecNullCommitIsExcludedFromJson() throws Exception {
        BaseRepoSpec spec = new BaseRepoSpec();
        spec.setRepo("https://github.com/org/repo");
        spec.setBranch("develop");
        spec.setPath("./");

        String json = objectMapper.writeValueAsString(spec);

        assertFalse(json.contains("\"commit\""), "null commit must be omitted from JSON");
    }

    @Test
    void testBaseRepoSpecDeserializesPartialJson() throws Exception {
        String json = "{\"repo\":\"https://github.com/org/repo\",\"branch\":\"main\",\"path\":\"./\"}";
        BaseRepoSpec spec = objectMapper.readValue(json, BaseRepoSpec.class);

        assertEquals("https://github.com/org/repo", spec.getRepo());
        assertEquals("main", spec.getBranch());
        assertEquals("./",   spec.getPath());
        assertNull(spec.getCommit());
    }

    @Test
    void testBaseRepoSpecSshRepoUrl() throws Exception {
        BaseRepoSpec spec = new BaseRepoSpec();
        spec.setRepo("git@github.com:org/control-plane.git");
        spec.setBranch("release/v2");
        spec.setPath("./prod");

        String json = objectMapper.writeValueAsString(spec);
        BaseRepoSpec deserialized = objectMapper.readValue(json, BaseRepoSpec.class);

        assertEquals("git@github.com:org/control-plane.git", deserialized.getRepo());
        assertEquals("release/v2", deserialized.getBranch());
    }
}
