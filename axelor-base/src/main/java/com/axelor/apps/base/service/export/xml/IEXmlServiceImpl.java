package com.axelor.apps.base.service.export.xml;

import com.axelor.apps.base.xml.models.ExportedModel;
import com.axelor.meta.MetaFiles;
import com.axelor.meta.db.MetaFile;
import com.google.common.io.Files;
import com.google.inject.Inject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Path;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import org.apache.commons.io.FileUtils;

/** Implementation of IEXmlService. This class use jaxb library to import and export Xml. */
public class IEXmlServiceImpl implements IEXmlService {

  private MetaFiles metaFiles;

  @Inject
  public IEXmlServiceImpl(MetaFiles metaFiles) {

    this.metaFiles = metaFiles;
  }

  @Override
  public <T extends ExportedModel> MetaFile exportXML(
      T srcObject, String fileName, Class<T> classObject) throws Exception {

    JAXBContext jc = JAXBContext.newInstance(classObject);
    Marshaller jaxbMarshaller = jc.createMarshaller();

    jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
    File file = new File(String.format("%s.xml", fileName));
    try (FileOutputStream fileOutputStream = new FileOutputStream(file, true)) {
      jaxbMarshaller.marshal(srcObject, fileOutputStream);
    }

    return metaFiles.upload(file);
  }

  @Override
  public <T extends ExportedModel> T importXMLToModel(String pathFile, Class<T> classObject)
      throws Exception {

    Path path = MetaFiles.getPath(pathFile);

    try (FileInputStream fileInputStream = new FileInputStream(path.toFile())) {

      // File temporary importation to the server
      File tempDir = Files.createTempDir();
      File importFile = new File(tempDir, "configurator-creator.xml");
      FileUtils.copyInputStreamToFile(fileInputStream, importFile);

      JAXBContext jc = JAXBContext.newInstance(classObject);
      Unmarshaller jaxbUnmarshaller = jc.createUnmarshaller();

      T resultObject = classObject.cast(jaxbUnmarshaller.unmarshal(importFile));

      FileUtils.forceDelete(tempDir);

      return resultObject;
    }
  }
}
