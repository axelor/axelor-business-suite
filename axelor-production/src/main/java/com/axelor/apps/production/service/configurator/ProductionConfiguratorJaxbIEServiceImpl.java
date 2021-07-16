package com.axelor.apps.production.service.configurator;

import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.base.service.export.xml.IEXmlService;
import com.axelor.apps.production.db.ConfiguratorBOM;
import com.axelor.apps.production.xml.models.ProductionConfiguratorExport;
import com.axelor.apps.sale.db.ConfiguratorCreator;
import com.axelor.apps.sale.db.repo.ConfiguratorCreatorRepository;
import com.axelor.apps.sale.service.configurator.ConfiguratorJaxbIEServiceImpl;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.meta.db.MetaFile;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.persistence.PersistenceException;
import javax.xml.bind.JAXBException;

/**
 * This class is a implementation on ConfiguratorIEService. It uses library jaxb in order to export
 * or import xml of Configurators creators. This class does manage ConfiguratorBOM that may be
 * located in ConfiguratorCreator.
 */
public class ProductionConfiguratorJaxbIEServiceImpl extends ConfiguratorJaxbIEServiceImpl {

  @Inject
  public ProductionConfiguratorJaxbIEServiceImpl(
      IEXmlService exportXmlService,
      AppBaseService appBaseService,
      ConfiguratorCreatorRepository configuratorCreatorRepository) {
    super(exportXmlService, appBaseService, configuratorCreatorRepository);
  }

  @Override
  public MetaFile exportConfiguratorsToXML(List<ConfiguratorCreator> ccList)
      throws AxelorException {

    try {
      ProductionConfiguratorExport productionConfiguratorExport =
          new ProductionConfiguratorExport(ccList);
      return ieXmlService.exportXML(
          productionConfiguratorExport,
          String.format(
              XML_NAME_TEMPLATE,
              appBaseService.getTodayDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)),
          ProductionConfiguratorExport.class);

    } catch (JAXBException e) {
      throw new AxelorException(e, TraceBackRepository.CATEGORY_CONFIGURATION_ERROR);
    } catch (IOException e) {
      throw new AxelorException(e, TraceBackRepository.CATEGORY_CONFIGURATION_ERROR);
    } catch (Exception e) {
      throw new AxelorException(e, TraceBackRepository.CATEGORY_CONFIGURATION_ERROR);
    }
  }

  @Override
  public String importXMLToConfigurators(String pathDiff) throws AxelorException {

    try {
      ProductionConfiguratorExport configuratorExport =
          ieXmlService.importXMLToModel(pathDiff, ProductionConfiguratorExport.class);
      StringBuilder importLog = new StringBuilder();
      super.linkConfiguratorFormulaToCC(configuratorExport.getConfiguratorsCreators());
      List<ConfiguratorCreator> configuratorsCreators =
          configuratorExport.getConfiguratorsCreators();
      configuratorsCreators.forEach(
          configuratorCreator -> {
            ConfiguratorBOM configuratorBom = configuratorCreator.getConfiguratorBom();
            // we check if id is not null since we may have already retrieve from database the
            // configuratorBom
            if (configuratorBom != null && configuratorBom.getId() == null) {
              linkChildToParentConfiguratorBOM(configuratorBom);
            }
          });

      int totalImport = saveConfiguratorCreators(configuratorsCreators, importLog);

      importLog.append(
          "\nTotal records: "
              + configuratorExport.getConfiguratorsCreators().size()
              + ", Total imported: "
              + totalImport);
      return importLog.toString();

    } catch (Exception e) {
      throw new AxelorException(e, TraceBackRepository.CATEGORY_CONFIGURATION_ERROR);
    }
  }

  protected void linkChildToParentConfiguratorBOM(ConfiguratorBOM configuratorBom) {

    if (configuratorBom.getConfiguratorBomList() != null) {
      configuratorBom
          .getConfiguratorBomList()
          .forEach(
              configuratorBomChild -> {
                configuratorBomChild.setParentConfiguratorBOM(configuratorBom);
                linkChildToParentConfiguratorBOM(configuratorBomChild);
              });
    }
  }

  @Transactional(ignore = {PersistenceException.class})
  protected int saveConfiguratorCreators(
      List<ConfiguratorCreator> configuratorsCreators, StringBuilder importLog) {

    AtomicInteger totalImport = new AtomicInteger(0);

    configuratorsCreators.forEach(
        configuratorCreator -> {
          try {
            configuratorCreatorRepository.save(configuratorCreator);
            totalImport.addAndGet(1);
          } catch (PersistenceException e) {
            importLog.append(
                "\nError in import: "
                    + configuratorCreator.getName()
                    + "\n"
                    + Arrays.toString(e.getStackTrace()));
          }
        });

    return totalImport.get();
  }
}
