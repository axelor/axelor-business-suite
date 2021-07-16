package com.axelor.apps.sale.service.configurator;

import com.axelor.apps.sale.db.ConfiguratorCreator;
import com.axelor.exception.AxelorException;
import com.axelor.meta.db.MetaFile;
import java.util.List;

/** Interface for import and export methods that use xml files by using jaxb library */
public interface ConfiguratorJaxbIEService {

  /**
   * Export list of configurator creator to xml
   *
   * @param ccList
   */
  MetaFile exportConfiguratorsToXML(List<ConfiguratorCreator> ccList) throws AxelorException;

  /**
   * Import a Configurators creators xml to the database.
   *
   * @param pathDiff
   * @return
   * @throws AxelorException
   */
  String importXMLToConfigurators(String pathDiff) throws AxelorException;
}
