package io.kalypso.scheduler.api.v1alpha1;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kalypso.scheduler.api.v1alpha1.spec.GitOpsRepoSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that {@link GitOpsRepo} CRD model classes serialize to and from JSON
 * with the field names expected by the Kubernetes API server.
 */
class GitOpsRepoSerializationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    void testGitOpsRepoSpecRoundTripWithAllFields() throws Exception {
        GitOpsRepoSpec spec = new GitOpsRepoSpec();
        spec.setRepo("https://github.com/org/gitops-manifests");
        spec.setBranch("main");
        spec.setPath("./clusters");

        String json = objectMapper.writeValueAsString(spec);

        assertTrue(json.contains("\"repo\":\"https://github.com/org/gitops-manifests\""), "repo must be serialized");
        assertTrue(json.contains("\"branch\":\"main\""), "branch must be serialized");
        assertTrue(json.contains("\"path\":\"./clusters\""), "path must be serialized");

        GitOpsRepoSpec deserialized = objectMapper.readValue(json, GitOpsRepoSpec.class);
        assertEquals("https://github.com/org/gitops-manifests", deserialized.getRepo());
        assertEquals("main", deserialized.getBranch());
        assertEquals("./clusters", deserialized.getPath());
    }

    @Test
    void testGitOpsRepoSpecNullFieldsOmittedFromJson() throws Exception {
        GitOpsRepoSpec spec = new GitOpsRepoSpec();
        spec.setRepo("https://github.com/org/gitops");

        String json = objectMapper.writeValueAsString(spec);

        assertFalse(json.contains("\"branch\""), "null branch must be omitted");
        assertFalse(json.contains("\"path\""), "null path must be omitted");
    }

    @Test
    void testGitOpsRepoSpecDeserializesPartialJson() throws Exception {
        String json = "{\"repo\":\"https://github.com/org/gitops\",\"branch\":\"release/v2\"}";
        GitOpsRepoSpec spec = objectMapper.readValue(json, GitOpsRepoSpec.class);

        assertEquals("https://github.com/org/gitops", spec.getRepo());
        assertEquals("release/v2", spec.getBranch());
        assertNull(spec.getPath(), "absent path must remain null");
    }

    @Test
    void testGitOpsRepoSpecRoundTripWithSshRepo() throws Exception {
        GitOpsRepoSpec spec = new GitOpsRepoSpec();
        spec.setRepo("git@github.com:org/gitops.git");
        spec.setBranch("main");
        spec.setPath("./prod");

        String json = objectMapper.writeValueAsString(spec);
        GitOpsRepoSpec deserialized = objectMapper.readValue(json, GitOpsRepoSpec.class);

        assertEquals("git@github.com:org/gitops.git", deserialized.getRepo());
        assertEquals("main", deserialized.getBranch());
        assertEquals("./prod", deserialized.getPath());
    }

    @Test
    void testGitOpsRepoSpecDeserializesOnlyPath() throws Exception {
        String json = "{\"path\":\"./manifests\"}";
        GitOpsRepoSpec spec = objectMapper.readValue(json, GitOpsRepoSpec.class);

        assertEquals("./manifests", spec.getPath());
        assertNull(spec.getRepo(), "absent repo must remain null");
        assertNull(spec.getBranch(), "absent branch must remain null");
    }
}
