package com.axelor.apps.production.db.repo;

import com.axelor.apps.production.db.ConfiguratorBOM;

public class ProductionConfiguratorBOMRepository extends ConfiguratorBOMRepository {

  public ConfiguratorBOM findByImportId(Long importId) {

    ConfiguratorBOM result =
        all().filter("self.importId = :importId").bind("importId", importId).fetchOne();

    return result;
  }
}
