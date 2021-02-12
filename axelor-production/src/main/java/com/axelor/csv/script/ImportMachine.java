package com.axelor.csv.script;

import com.axelor.apps.production.db.Machine;
import com.axelor.apps.production.db.repo.MachineRepository;
import com.axelor.common.StringUtils;
import com.axelor.meta.MetaFiles;
import com.axelor.meta.db.MetaFile;
import com.google.inject.Inject;
import java.io.File;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImportMachine {
  private final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Inject private MachineRepository machineRepo;

  @Inject MetaFiles metaFiles;

  public Object setPictures(Object bean, Map<String, Object> values) {

    assert bean instanceof Machine;

    Machine machine = (Machine) bean;
    String fileName = (String) values.get("picture_fileName");

    if (!StringUtils.isEmpty(fileName)) {
      final Path path = (Path) values.get("__path__");

      try {
        final File image = path.resolve(fileName).toFile();
        if (image != null && image.isFile()) {
          final MetaFile metaFile = metaFiles.upload(image);
          machine.setPicture(metaFile);
        } else {
          LOG.debug(
              "No image file found: {}",
              image == null ? path.toAbsolutePath() : image.getAbsolutePath());
        }

      } catch (Exception e) {
        LOG.error("Error when importing product picture : {}", e);
      }
    }

    return machineRepo.save(machine);
  }
}
