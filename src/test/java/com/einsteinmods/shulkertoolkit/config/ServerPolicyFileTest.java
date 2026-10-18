package com.einsteinmods.shulkertoolkit.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.einsteinmods.shulkertoolkit.core.ServerPolicy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServerPolicyFileTest {
  private static final String ENDER_PREVIEW_OFF_JSON =
      """
      {
        "schemaVersion": 1,
        "allowEnderPreview": false
      }
      """;

  private static final String CRAFT_FROM_BOXES_OFF_JSON =
      """
      {
        "schemaVersion": 1,
        "allowCraftFromBoxes": false
      }
      """;

  private static JsonConfigFile<ServerPolicyFile> file(Path directory) {
    return new JsonConfigFile<>(
        directory.resolve(ServerPolicyFile.FILE_NAME),
        ServerPolicyFile.class,
        ServerPolicyFile.DEFAULTS);
  }

  @Test
  void missingFileIsWrittenWithEverythingAllowed(@TempDir Path directory) {
    assertEquals(ServerPolicy.UNRESTRICTED, file(directory).load().toPolicy());
    assertEquals(ServerPolicy.UNRESTRICTED, file(directory).load().toPolicy());
  }

  @Test
  void missingFlagsAreAllowedAndFalseFlagsForbid(@TempDir Path directory) throws IOException {
    Files.writeString(
        directory.resolve(ServerPolicyFile.FILE_NAME),
        ENDER_PREVIEW_OFF_JSON,
        StandardCharsets.UTF_8);

    assertEquals(
        new ServerPolicy(true, true, true, false, true, true), file(directory).load().toPolicy());
  }

  @Test
  void craftFromBoxesCanBeForbidden(@TempDir Path directory) throws IOException {
    Files.writeString(
        directory.resolve(ServerPolicyFile.FILE_NAME),
        CRAFT_FROM_BOXES_OFF_JSON,
        StandardCharsets.UTF_8);

    assertFalse(file(directory).load().toPolicy().allowCraftFromBoxes());
  }

  @Test
  void savedPolicyLoadsBack(@TempDir Path directory) {
    ServerPolicy policy =
        new ServerPolicy(false, true, false, true, false, true, true, true, false);
    file(directory).save(ServerPolicyFile.from(policy));

    assertEquals(policy, file(directory).load().toPolicy());
  }
}
